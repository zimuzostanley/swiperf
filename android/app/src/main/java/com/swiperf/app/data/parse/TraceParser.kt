package com.swiperf.app.data.parse

import android.util.Base64
import com.swiperf.app.data.model.Slice
import com.swiperf.app.data.model.TraceEntry
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TraceParser {

    // ── Field alias configs matching types.ts DEFAULT_COLUMN_CONFIG ──

    private val UUID_ALIASES = listOf("trace_uuid", "uuid", "id", "trace_id", "trace_address")
    private val PKG_ALIASES = listOf("package_name", "process_name", "process", "package", "pkg", "app")
    private val DUR_ALIASES = listOf("startup_dur", "startup_dur_ms", "startup_duration", "dur", "duration", "total_dur", "startup_ms")
    private val SLICES_ALIASES = listOf("slices", "quantized_sequence", "quantized_sequence_json", "json", "data", "trace_data", "base64", "thread_slices")
    private val MS_ALIASES = setOf("startup_dur_ms", "startup_ms")

    private val TS_ALIASES = listOf("ts", "timestamp", "start", "start_ts", "begin")
    private val SLICE_DUR_ALIASES = listOf("dur", "duration", "length", "end_ts")
    private val NAME_ALIASES = listOf("name", "slice_name", "label", "event")
    private val STATE_ALIASES = listOf("state", "thread_state", "sched_state")
    private val DEPTH_ALIASES = listOf("depth", "level", "stack_depth")
    private val IO_WAIT_ALIASES = listOf("io_wait", "iowait", "io")
    private val BF_ALIASES = listOf("blocked_function", "blocked_fn", "blocked", "wchan")

    private val UUID_RE = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)

    // ── Field resolution ──

    private fun resolveString(obj: JSONObject, aliases: List<String>, fallback: String): String {
        for (a in aliases) {
            if (obj.has(a) && !obj.isNull(a)) return obj.getString(a)
        }
        return fallback
    }

    private fun resolveLong(obj: JSONObject, aliases: List<String>, fallback: Long): Long {
        for (a in aliases) {
            if (obj.has(a) && !obj.isNull(a)) return obj.optLong(a, fallback)
        }
        return fallback
    }

    private fun resolveIntOrNull(obj: JSONObject, aliases: List<String>): Int? {
        for (a in aliases) {
            if (obj.has(a) && !obj.isNull(a)) return obj.optInt(a)
        }
        return null
    }

    private fun resolveStringOrNull(obj: JSONObject, aliases: List<String>): String? {
        for (a in aliases) {
            if (obj.has(a) && !obj.isNull(a)) {
                val v = obj.optString(a, "")
                return if (v.isEmpty()) null else v
            }
        }
        return null
    }

    // ── Slice normalization ──

    fun normalizeSlice(raw: JSONObject): Slice {
        return Slice(
            ts = resolveLong(raw, TS_ALIASES, 0),
            dur = resolveLong(raw, SLICE_DUR_ALIASES, 0),
            name = resolveStringOrNull(raw, NAME_ALIASES),
            state = resolveStringOrNull(raw, STATE_ALIASES),
            depth = resolveIntOrNull(raw, DEPTH_ALIASES),
            ioWait = resolveIntOrNull(raw, IO_WAIT_ALIASES),
            blockedFunction = resolveStringOrNull(raw, BF_ALIASES)
        )
    }

    // ── UUID extraction ──

    private fun extractUuid(value: String): String {
        if (!value.contains("/")) return value
        val match = UUID_RE.find(value)
        if (match != null) return match.value
        val base = value.split("/").last()
        return base.replace(Regex("\\.[\\w]+(\\.[\\w]+)*$"), "")
    }

    // ── Package name resolution (handles JSON-encoded column) ──

    private fun resolvePackageName(raw: JSONObject): String {
        val value = resolveString(raw, PKG_ALIASES, "unknown")
        if (value.startsWith("{")) {
            try {
                val parsed = JSONObject(value)
                if (parsed.has("package_name")) return parsed.getString("package_name")
            } catch (_: Exception) {}
        }
        return value
    }

    // ── Startup duration with ms→ns conversion ──

    private fun resolveStartupDur(raw: JSONObject): Long {
        for (alias in DUR_ALIASES) {
            if (raw.has(alias) && !raw.isNull(alias)) {
                val value = raw.optDouble(alias, 0.0)
                return if (alias in MS_ALIASES) (value * 1e6).toLong() else value.toLong()
            }
        }
        return 0L
    }

    // ── Trace normalization ──

    fun normalizeTrace(raw: JSONObject): TraceEntry? {
        var rawSlicesValue: Any? = null
        for (a in SLICES_ALIASES) {
            if (raw.has(a) && !raw.isNull(a)) {
                rawSlicesValue = raw.get(a)
                break
            }
        }
        if (rawSlicesValue == null) return null

        val slices: List<Slice>
        when (rawSlicesValue) {
            is JSONArray -> {
                if (rawSlicesValue.length() == 0) return null
                slices = (0 until rawSlicesValue.length()).mapNotNull { i ->
                    val item = rawSlicesValue.optJSONObject(i)
                    if (item != null) normalizeSlice(item) else null
                }
            }
            is String -> {
                var decoded = rawSlicesValue
                if (!decoded.startsWith("[") && !decoded.startsWith("{")) {
                    try {
                        decoded = String(Base64.decode(decoded, Base64.DEFAULT))
                    } catch (_: Exception) { return null }
                }
                try {
                    val repaired = try { decoded } catch (_: Exception) { repairJson(decoded) }
                    val arr = try { JSONArray(repaired) } catch (_: Exception) { JSONArray(repairJson(repaired)) }
                    slices = (0 until arr.length()).mapNotNull { i ->
                        val item = arr.optJSONObject(i)
                        if (item != null) normalizeSlice(item) else null
                    }
                } catch (_: Exception) { return null }
            }
            else -> return null
        }

        if (slices.isEmpty()) return null

        // Collect extra fields
        val knownKeys = (UUID_ALIASES + PKG_ALIASES + DUR_ALIASES + SLICES_ALIASES).toSet()
        val extra = mutableMapOf<String, Any?>()
        for (key in raw.keys()) {
            if (key !in knownKeys) {
                extra[key] = raw.opt(key)
            }
        }

        return TraceEntry(
            traceUuid = extractUuid(resolveString(raw, UUID_ALIASES, UUID.randomUUID().toString())),
            packageName = resolvePackageName(raw),
            startupDur = resolveStartupDur(raw),
            slices = slices,
            extra = if (extra.isNotEmpty()) extra else null
        )
    }

    // ── JSON repair for truncated input ──

    fun repairJson(text: String): String {
        var result = text.trimEnd()
        var inStr = false
        var escape = false
        val stack = mutableListOf<Char>()

        for (ch in result) {
            if (escape) { escape = false; continue }
            if (ch == '\\' && inStr) { escape = true; continue }
            if (ch == '"' && !escape) { inStr = !inStr; continue }
            if (inStr) continue
            when (ch) {
                '[' -> stack.add(']')
                '{' -> stack.add('}')
                ']', '}' -> { if (stack.isNotEmpty() && stack.last() == ch) stack.removeAt(stack.size - 1) }
            }
        }
        if (inStr) result += '"'
        while (stack.isNotEmpty()) result += stack.removeAt(stack.size - 1)
        return result
    }

    // ── Delimited parsing (TSV/CSV) — RFC 4180 ──

    fun parseDelimitedRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQ = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    }
                    inQ = false
                    i++
                    continue
                }
                current.append(ch)
                i++
            } else {
                when {
                    ch == '"' && current.isEmpty() -> { inQ = true; i++ }
                    ch == delimiter -> { fields.add(current.toString()); current.clear(); i++ }
                    ch == '\n' || ch == '\r' -> {
                        fields.add(current.toString()); current.clear()
                        if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        if (fields.any { it.trim().isNotEmpty() }) rows.add(fields.toList())
                        fields.clear()
                        i++
                    }
                    else -> { current.append(ch); i++ }
                }
            }
        }
        fields.add(current.toString())
        if (fields.any { it.trim().isNotEmpty() }) rows.add(fields.toList())
        return rows
    }

    // ── High-level: JSON text → TraceEntry[] ──

    fun parseJsonToTraces(text: String): List<TraceEntry> {
        val parsed = try { JSONArray(text) } catch (_: Exception) {
            try { JSONArray(repairJson(text)) } catch (_: Exception) {
                // Try as single object
                val obj = try { JSONObject(text) } catch (_: Exception) { JSONObject(repairJson(text)) }
                val trace = normalizeTrace(obj) ?: throw Exception("Object must have a slices/json/data field")
                return listOf(trace)
            }
        }

        if (parsed.length() == 0) throw Exception("Empty array")

        // Check for array-of-arrays
        val first = parsed.opt(0)
        if (first is JSONArray && parsed.length() >= 2) {
            val headers = (0 until first.length()).map { first.getString(it) }
            val items = mutableListOf<JSONObject>()
            for (r in 1 until parsed.length()) {
                val row = parsed.getJSONArray(r)
                val obj = JSONObject()
                headers.forEachIndexed { idx, h -> if (idx < row.length()) obj.put(h, row.opt(idx)) }
                items.add(obj)
            }
            return parseObjectArray(items)
        }

        if (first is JSONObject) {
            val items = (0 until parsed.length()).map { parsed.getJSONObject(it) }
            return parseObjectArray(items)
        }

        throw Exception("Expected array of objects or [headers, ...rows]")
    }

    private fun parseObjectArray(items: List<JSONObject>): List<TraceEntry> {
        val first = items[0]

        // Detect: slices vs traces
        val looksLikeSlice = TS_ALIASES.any { first.has(it) } && SLICE_DUR_ALIASES.any { first.has(it) }
        val looksLikeTrace = SLICES_ALIASES.any { first.has(it) }

        if (looksLikeSlice && !looksLikeTrace) {
            val slices = items.map { normalizeSlice(it) }
            return listOf(TraceEntry(
                traceUuid = UUID.randomUUID().toString(),
                packageName = "unknown",
                startupDur = 0,
                slices = slices
            ))
        }

        if (looksLikeTrace) {
            val traces = items.mapNotNull { normalizeTrace(it) }
            if (traces.isEmpty()) throw Exception("No valid traces in array")
            return traces
        }

        throw Exception("Array items need ts+dur (slices) or a slices/json/data column (traces)")
    }

    // ── High-level: delimited text → TraceEntry[] ──

    fun parseDelimitedToTraces(text: String, delimiter: Char): List<TraceEntry> {
        val rows = parseDelimitedRows(text, delimiter)
        if (rows.size < 2) throw Exception("Need header + data rows")

        val headers = rows[0]
        val norm = { s: String -> s.lowercase().trim().replace(Regex("\\s+"), "_") }
        val findCol = { aliases: List<String> ->
            var idx = -1
            for (a in aliases) {
                idx = headers.indexOfFirst { norm(it) == a.lowercase() }
                if (idx >= 0) break
            }
            idx
        }

        val slicesIdx = findCol(SLICES_ALIASES)
        val uuidIdx = findCol(UUID_ALIASES)
        val pkgIdx = findCol(PKG_ALIASES)
        val durIdx = findCol(DUR_ALIASES)
        val durIsMs = durIdx >= 0 && norm(headers[durIdx]) in MS_ALIASES

        if (slicesIdx < 0) throw Exception("Need a column matching: ${SLICES_ALIASES.joinToString(", ")}")

        val traces = mutableListOf<TraceEntry>()
        for (i in 1 until rows.size) {
            val cols = rows[i]
            if (slicesIdx >= cols.size || cols[slicesIdx].trim().isEmpty()) continue

            var raw = cols[slicesIdx].trim()
            if (!raw.startsWith("[") && !raw.startsWith("{")) {
                try { raw = String(Base64.decode(raw, Base64.DEFAULT)) } catch (_: Exception) {}
            }

            try {
                val sliceParsed = try { JSONArray(raw) } catch (_: Exception) { JSONArray(repairJson(raw)) }
                val slices = (0 until sliceParsed.length()).mapNotNull { j ->
                    sliceParsed.optJSONObject(j)?.let { normalizeSlice(it) }
                }
                if (slices.isEmpty()) continue

                val extra = mutableMapOf<String, Any?>()
                headers.forEachIndexed { idx, h ->
                    if (idx != slicesIdx && idx != uuidIdx && idx != pkgIdx && idx != durIdx && idx < cols.size) {
                        if (cols[idx].trim().isNotEmpty()) extra[norm(h)] = cols[idx].trim()
                    }
                }

                var pkgName = if (pkgIdx >= 0 && pkgIdx < cols.size) cols[pkgIdx].trim() else "unknown"
                if (pkgName.startsWith("{")) {
                    try {
                        val p = JSONObject(pkgName)
                        if (p.has("package_name")) pkgName = p.getString("package_name")
                    } catch (_: Exception) {}
                }

                traces.add(TraceEntry(
                    traceUuid = extractUuid(
                        if (uuidIdx >= 0 && uuidIdx < cols.size && cols[uuidIdx].isNotEmpty()) cols[uuidIdx].trim()
                        else UUID.randomUUID().toString()
                    ),
                    packageName = pkgName,
                    startupDur = if (durIdx >= 0 && durIdx < cols.size) {
                        val v = cols[durIdx].toDoubleOrNull() ?: 0.0
                        (v * if (durIsMs) 1e6 else 1.0).toLong()
                    } else 0L,
                    slices = slices,
                    extra = if (extra.isNotEmpty()) extra else null
                ))
            } catch (_: Exception) { /* skip row */ }
        }

        if (traces.isEmpty()) throw Exception("No valid traces found")
        return traces
    }

    // ── Unified entry point: auto-detect format ──

    fun parseText(text: String): List<TraceEntry> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return parseJsonToTraces(trimmed)
        }

        val firstNewline = trimmed.indexOf('\n')
        val firstLine = if (firstNewline >= 0) trimmed.substring(0, firstNewline) else trimmed
        if (firstLine.contains('\t')) return parseDelimitedToTraces(trimmed, '\t')
        if (firstLine.contains(',')) return parseDelimitedToTraces(trimmed, ',')

        return parseJsonToTraces(trimmed)
    }
}
