package com.swiperf.app.data.export

import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.TraceEntry
import com.swiperf.app.data.model.Verdict

data class ExportRow(
    val traceUuid: String,
    val packageName: String,
    val startupDur: Long,
    val tabName: String,
    val verdict: String,
    val link: String,
    val extra: Map<String, Any?> = emptyMap()
)

object ExportHelper {

    private val EXCLUDED_EXTRA = setOf(
        "slices", "quantized_sequence", "quantized_sequence_json", "quantized_sequence_base64"
    )

    fun buildTraceLink(uuid: String, packageName: String? = null): String {
        if (uuid.isBlank()) return ""
        var url = "https://apconsole.corp.google.com/link/perfetto/field_traces?uuid=$uuid"
        if (!packageName.isNullOrBlank()) {
            url += "&query=${java.net.URLEncoder.encode("com.android.AndroidStartup.packageName=$packageName", "UTF-8")}"
        }
        return url
    }

    private fun verdictLabel(v: Verdict?): String = when (v) {
        Verdict.LIKE -> "positive"
        Verdict.DISLIKE -> "negative"
        Verdict.DISCARD -> "discarded"
        null -> "pending"
    }

    fun traceExportRow(
        trace: TraceEntry,
        traceKey: String,
        tabName: String,
        verdicts: Map<String, Verdict>
    ): ExportRow {
        val extra = mutableMapOf<String, Any?>()
        trace.extra?.forEach { (k, v) ->
            if (k !in EXCLUDED_EXTRA) extra[k] = v
        }
        return ExportRow(
            traceUuid = trace.traceUuid,
            packageName = trace.packageName,
            startupDur = trace.startupDur,
            tabName = tabName,
            verdict = verdictLabel(verdicts[traceKey]),
            link = buildTraceLink(trace.traceUuid, trace.packageName),
            extra = extra
        )
    }

    private val FIXED_COLS = listOf("trace_uuid", "package_name", "startup_dur", "tab_name", "verdict", "link")

    private fun tsvEscape(v: Any?): String {
        if (v == null) return ""
        return v.toString().replace(Regex("[\t\n\r]"), " ")
    }

    private fun colLetter(idx: Int): String {
        var s = ""
        var n = idx
        while (n >= 0) {
            s = ('A' + (n % 26)) + s
            n = n / 26 - 1
        }
        return s
    }

    private fun brushFormula(dataRows: Int, uuidCol: String, linkCol: String): String {
        val lastRow = dataRows + 1
        val range = "${uuidCol}2:${uuidCol}${lastRow}"
        return "=IF(COUNTA($range)=0, \"No UUIDs found\"," +
            " HYPERLINK(" +
            "\"https://brush.corp.google.com/?filters=\" &" +
            " ENCODEURL(" +
            "\"[{\" & CHAR(34) & \"column\" & CHAR(34) & \":\" & CHAR(34) & \"trace_uuid\" & CHAR(34) &" +
            "\",\" & CHAR(34) & \"operator\" & CHAR(34) & \":\" & CHAR(34) & \"in\" & CHAR(34) &" +
            "\",\" & CHAR(34) & \"value\" & CHAR(34) & \":\" & CHAR(34) & \"[\" & CHAR(92) & CHAR(34) &" +
            " TEXTJOIN(CHAR(92) & CHAR(34) & \",\" & CHAR(92) & CHAR(34), TRUE, FILTER($range, $range<>\"\")) &" +
            " CHAR(92) & CHAR(34) & \"]\" & CHAR(34) & \"}]\"" +
            ") &" +
            " \"&metric_id=android_startup&charts=gallery&gallerySvgColumn=svg&galleryMetricColumn=dur_ms&galleryMetricNameColumn=process_name\"," +
            " \"Open in Brush\"))"
    }

    fun rowsToTsv(rows: List<ExportRow>): String {
        if (rows.isEmpty()) return ""
        val extraCols = mutableSetOf<String>()
        for (row in rows) {
            for (k in row.extra.keys) {
                if (k !in FIXED_COLS) extraCols.add(k)
            }
        }
        val cols = FIXED_COLS + extraCols.sorted()
        val header = cols.joinToString("\t")

        fun rowValue(row: ExportRow, col: String): Any? = when (col) {
            "trace_uuid" -> row.traceUuid
            "package_name" -> row.packageName
            "startup_dur" -> row.startupDur
            "tab_name" -> row.tabName
            "verdict" -> row.verdict
            "link" -> row.link
            else -> row.extra[col]
        }

        val lines = rows.map { row -> cols.joinToString("\t") { col -> tsvEscape(rowValue(row, col)) } }

        val linkIdx = cols.indexOf("link")
        val uuidIdx = cols.indexOf("trace_uuid")
        if (linkIdx >= 0 && uuidIdx >= 0) {
            val formulaCells = cols.mapIndexed { i, _ ->
                if (i == linkIdx) brushFormula(rows.size, colLetter(uuidIdx), colLetter(linkIdx)) else ""
            }
            return header + "\n" + lines.joinToString("\n") + "\n" + formulaCells.joinToString("\t")
        }

        return header + "\n" + lines.joinToString("\n")
    }

    fun rowsToJson(rows: List<ExportRow>): String {
        val sb = StringBuilder("[\n")
        rows.forEachIndexed { idx, row ->
            sb.append("  {\n")
            sb.append("    \"trace_uuid\": \"${row.traceUuid}\",\n")
            sb.append("    \"package_name\": \"${row.packageName}\",\n")
            sb.append("    \"startup_dur\": ${row.startupDur},\n")
            sb.append("    \"tab_name\": \"${row.tabName}\",\n")
            sb.append("    \"verdict\": \"${row.verdict}\",\n")
            sb.append("    \"link\": \"${row.link}\"")
            if (row.extra.isNotEmpty()) {
                for ((k, v) in row.extra.toSortedMap()) {
                    sb.append(",\n    \"$k\": ")
                    when (v) {
                        is String -> sb.append("\"$v\"")
                        is Number -> sb.append(v)
                        null -> sb.append("null")
                        else -> sb.append("\"$v\"")
                    }
                }
            }
            sb.append("\n  }")
            if (idx < rows.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun buildRows(clusters: List<Cluster>): List<ExportRow> {
        val rows = mutableListOf<ExportRow>()
        for (cl in clusters) {
            for (ts in cl.traces) {
                rows.add(traceExportRow(ts.trace, ts.key, cl.name, cl.verdicts))
            }
        }
        return rows
    }
}
