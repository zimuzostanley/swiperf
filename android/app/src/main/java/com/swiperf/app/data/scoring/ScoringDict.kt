package com.swiperf.app.data.scoring

import org.json.JSONArray
import org.json.JSONObject

data class DictEntry(
    val verdict: RegionVerdict,
    val signature: Set<Triple<String, String?, String?>>,
    val normalized: Boolean = false,
    var hitCount: Int = 0
) {
    val displayLabel: String get() {
        val symbol = if (verdict == RegionVerdict.SAME) "\u2248" else "\u2260"
        return signature.joinToString(", ") { (field, a, t) ->
            "${field.replace("_", " ")}: ${a ?: "null"} $symbol ${t ?: "null"}"
        }
    }

    val searchText: String by lazy { displayLabel.lowercase() }
}

/**
 * Lookup key: signature + normalized flag.
 * Two entries with the same signature but different normalized flags are distinct.
 */
private data class DictKey(val signature: Set<Triple<String, String?, String?>>, val normalized: Boolean)

class ScoringDictionary {
    private val map = LinkedHashMap<DictKey, DictEntry>()
    // Secondary index: signature-only → entry (for fast lookup in applyTo/bumpHitCount
    // where we don't know the normalized flag)
    private val bySig = HashMap<Set<Triple<String, String?, String?>>, DictEntry>()

    val all: List<DictEntry> get() = map.values.toList()
    val size: Int get() = map.size

    fun addFromState(state: ScoringState, normalized: Boolean = false) {
        for (sig in state.sameSignatures) addOrUpdate(RegionVerdict.SAME, sig, normalized)
        for (sig in state.diffSignatures) addOrUpdate(RegionVerdict.DIFFERENT, sig, normalized)
    }

    private fun addOrUpdate(verdict: RegionVerdict, signature: Set<Triple<String, String?, String?>>, normalized: Boolean) {
        val key = DictKey(signature, normalized)
        val existing = map[key]
        if (existing != null) {
            if (existing.verdict != verdict) {
                val updated = DictEntry(verdict, signature, normalized, existing.hitCount)
                map[key] = updated
                bySig[signature] = updated
            }
        } else {
            val entry = DictEntry(verdict, signature, normalized)
            map[key] = entry
            // bySig stores the most recent entry for this signature
            bySig[signature] = entry
        }
    }

    fun bumpHitCount(signature: Set<Triple<String, String?, String?>>) {
        bySig[signature]?.hitCount?.let { bySig[signature]!!.hitCount++ }
    }

    fun remove(entry: DictEntry) {
        map.remove(DictKey(entry.signature, entry.normalized))
        // Rebuild bySig for this signature if another entry with same sig exists
        val remaining = map.values.find { it.signature == entry.signature }
        if (remaining != null) bySig[entry.signature] = remaining
        else bySig.remove(entry.signature)
    }

    fun removeAll(toRemove: List<DictEntry>) {
        for (e in toRemove) map.remove(DictKey(e.signature, e.normalized))
        // Rebuild bySig
        bySig.clear()
        for (e in map.values) bySig[e.signature] = e
    }

    fun clear() { map.clear(); bySig.clear() }

    /** Pre-populate a ScoringState with known equivalences and auto-resolve matching regions. */
    fun applyTo(state: ScoringState) {
        for (entry in map.values) {
            if (entry.verdict == RegionVerdict.SAME) state.sameSignatures.add(entry.signature)
            else if (entry.verdict == RegionVerdict.DIFFERENT) state.diffSignatures.add(entry.signature)
        }
        for (i in state.regions.indices) {
            if (state.verdicts.containsKey(i) || state.regions[i].isAutoSame) continue
            val sig = state.regions[i].diffSignature
            val known = bySig[sig] // O(1) lookup
            if (known != null) {
                state.verdicts[i] = known.verdict
                state.dictApplied.add(i)
                known.hitCount++
            }
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (e in map.values) {
            val obj = JSONObject()
            obj.put("verdict", if (e.verdict == RegionVerdict.SAME) "same" else "different")
            obj.put("hitCount", e.hitCount)
            obj.put("normalized", e.normalized)
            val sigArr = JSONArray()
            for ((field, a, t) in e.signature) {
                val s = JSONObject()
                s.put("field", field)
                if (a != null) s.put("anchor", a) else s.put("anchor", JSONObject.NULL)
                if (t != null) s.put("target", t) else s.put("target", JSONObject.NULL)
                sigArr.put(s)
            }
            obj.put("signature", sigArr)
            arr.put(obj)
        }
        return arr.toString(2)
    }

    companion object {
        fun fromJson(json: String): ScoringDictionary {
            val dict = ScoringDictionary()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val verdict = if (obj.getString("verdict") == "same") RegionVerdict.SAME else RegionVerdict.DIFFERENT
                    val hitCount = obj.optInt("hitCount", 0)
                    val normalized = obj.optBoolean("normalized", false)
                    val sigArr = obj.getJSONArray("signature")
                    val sig = mutableSetOf<Triple<String, String?, String?>>()
                    for (j in 0 until sigArr.length()) {
                        val s = sigArr.getJSONObject(j)
                        sig.add(Triple(
                            s.getString("field"),
                            if (s.isNull("anchor")) null else s.getString("anchor"),
                            if (s.isNull("target")) null else s.getString("target")
                        ))
                    }
                    val entry = DictEntry(verdict, sig, normalized, hitCount)
                    dict.map[DictKey(sig, normalized)] = entry
                    dict.bySig[sig] = entry
                }
            } catch (_: Exception) {}
            return dict
        }
    }
}

private val DIGITS_RE = Regex("\\d+")

/** Normalize a string by replacing digit sequences with [num] */
fun normalizeDigits(s: String?): String? {
    if (s == null) return null
    return s.replace(DIGITS_RE, "[num]")
}
