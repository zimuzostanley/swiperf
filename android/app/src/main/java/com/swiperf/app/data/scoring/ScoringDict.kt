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

class ScoringDictionary {
    private val entries = mutableListOf<DictEntry>()

    val all: List<DictEntry> get() = entries.toList()
    val size: Int get() = entries.size

    fun addFromState(state: ScoringState, normalized: Boolean = false) {
        for (sig in state.sameSignatures) addOrUpdate(RegionVerdict.SAME, sig, normalized)
        for (sig in state.diffSignatures) addOrUpdate(RegionVerdict.DIFFERENT, sig, normalized)
    }

    private fun addOrUpdate(verdict: RegionVerdict, signature: Set<Triple<String, String?, String?>>, normalized: Boolean) {
        val existing = entries.find { it.signature == signature && it.normalized == normalized }
        if (existing != null) {
            if (existing.verdict != verdict) {
                entries.remove(existing)
                entries.add(DictEntry(verdict, signature, normalized, existing.hitCount))
            }
        } else {
            entries.add(DictEntry(verdict, signature, normalized))
        }
    }

    fun bumpHitCount(signature: Set<Triple<String, String?, String?>>) {
        val entry = entries.find { it.signature == signature }
        if (entry != null) entry.hitCount++
    }

    fun remove(entry: DictEntry) { entries.remove(entry) }
    fun removeAll(toRemove: List<DictEntry>) { entries.removeAll(toRemove.toSet()) }
    fun clear() { entries.clear() }

    fun applyTo(state: ScoringState) {
        for (entry in entries) {
            if (entry.verdict == RegionVerdict.SAME) state.sameSignatures.add(entry.signature)
            else if (entry.verdict == RegionVerdict.DIFFERENT) state.diffSignatures.add(entry.signature)
        }
        for (i in state.regions.indices) {
            if (state.verdicts.containsKey(i) || state.regions[i].isAutoSame) continue
            val sig = state.regions[i].diffSignature
            val known = entries.find { it.signature == sig }
            if (known != null) {
                state.verdicts[i] = known.verdict
                known.hitCount++
            }
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (e in entries) {
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
                    dict.entries.add(DictEntry(verdict, sig, normalized, hitCount))
                }
            } catch (_: Exception) {}
            return dict
        }
    }
}

/** Normalize a string by replacing digit sequences with [num] */
fun normalizeDigits(s: String?): String? {
    if (s == null) return null
    return s.replace(Regex("\\d+"), "[num]")
}
