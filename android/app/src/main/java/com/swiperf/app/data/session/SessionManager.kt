package com.swiperf.app.data.session

import android.content.Context
import com.swiperf.app.data.model.*
import com.swiperf.app.data.parse.TraceParser
import com.swiperf.app.data.scoring.ScoringDictionary
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SessionLoadResult(
    val clusters: List<Cluster>,
    val activeClusterId: String?,
    val scoringDictJson: String? = null,
    val scoringUseDict: Boolean = true,
    val scoringNormalizeDigits: Boolean = false,
    val sessionId: String? = null
) {
    fun applyDictTo(
        dict: ScoringDictionary,
        useDict: MutableStateFlow<Boolean>,
        normalizeDigits: MutableStateFlow<Boolean>
    ) {
        dict.clear()
        if (scoringDictJson != null && scoringDictJson.isNotEmpty()) {
            val imported = ScoringDictionary.fromJson(scoringDictJson)
            for (entry in imported.all) {
                dict.addFromState(com.swiperf.app.data.scoring.ScoringState(
                    emptyList(),
                    sameSignatures = if (entry.verdict == com.swiperf.app.data.scoring.RegionVerdict.SAME) mutableSetOf(entry.signature) else mutableSetOf(),
                    diffSignatures = if (entry.verdict == com.swiperf.app.data.scoring.RegionVerdict.DIFFERENT) mutableSetOf(entry.signature) else mutableSetOf()
                ))
            }
        }
        useDict.value = scoringUseDict
        normalizeDigits.value = scoringNormalizeDigits
    }
}

object SessionManager {

    private fun dao(context: Context) = AppDatabase.getInstance(context).sessionDao()

    // ── Serialize clusters to JSON (web-app compatible format) ──

    fun clustersToJson(
        clusters: List<Cluster>,
        activeClusterId: String?,
        scoringDict: ScoringDictionary? = null,
        scoringUseDict: Boolean = true,
        scoringNormalizeDigits: Boolean = false
    ): String {
        val json = JSONObject()
        json.put("version", 1)
        json.put("activeClusterId", activeClusterId ?: JSONObject.NULL)

        // Scoring dictionary (global, not per-cluster)
        if (scoringDict != null && scoringDict.size > 0) {
            json.put("scoringDict", scoringDict.toJson())
        }
        json.put("scoringUseDict", scoringUseDict)
        json.put("scoringNormalizeDigits", scoringNormalizeDigits)

        val clustersArr = JSONArray()
        for (cl in clusters) {
            val clObj = JSONObject()
            clObj.put("id", cl.id)
            clObj.put("name", cl.name)

            val tracesArr = JSONArray()
            for (ts in cl.traces) {
                val tObj = JSONObject()
                tObj.put("trace_uuid", ts.trace.traceUuid)
                tObj.put("package_name", ts.trace.packageName)
                tObj.put("startup_dur", ts.trace.startupDur)

                val slicesArr = JSONArray()
                for (s in ts.trace.slices) {
                    val sObj = JSONObject()
                    sObj.put("ts", s.ts)
                    sObj.put("dur", s.dur)
                    if (s.name != null) sObj.put("name", s.name)
                    if (s.state != null) sObj.put("state", s.state)
                    if (s.depth != null) sObj.put("depth", s.depth)
                    if (s.ioWait != null) sObj.put("io_wait", s.ioWait)
                    if (s.blockedFunction != null) sObj.put("blocked_function", s.blockedFunction)
                    slicesArr.put(sObj)
                }
                tObj.put("slices", slicesArr)

                ts.trace.extra?.let { extra ->
                    for ((k, v) in extra) {
                        when (v) {
                            is String -> tObj.put(k, v)
                            is Number -> tObj.put(k, v)
                            is Boolean -> tObj.put(k, v)
                            null -> tObj.put(k, JSONObject.NULL)
                        }
                    }
                }
                tracesArr.put(tObj)
            }
            clObj.put("traces", tracesArr)

            val verdictsArr = JSONArray()
            for ((key, verdict) in cl.verdicts) {
                val vArr = JSONArray()
                vArr.put(key)
                vArr.put(when (verdict) {
                    Verdict.LIKE -> "like"
                    Verdict.DISLIKE -> "dislike"
                    Verdict.DISCARD -> "discard"
                })
                verdictsArr.put(vArr)
            }
            clObj.put("verdicts", verdictsArr)
            clObj.put("overviewFilter", cl.overviewFilter.name.lowercase())
            clObj.put("splitView", false)
            clObj.put("splitFilters", JSONArray().put("pending").put("positive"))
            clObj.put("splitRatio", 0.5)
            clObj.put("sortField", when (cl.sortField) {
                SortField.STARTUP_DUR -> "startup_dur"
                SortField.COSINE_SIMILARITY -> "cosine_similarity"
                SortField.MANUAL_SCORE -> "manual_score"
                SortField.INDEX -> "index"
            })
            clObj.put("sortDir", cl.sortDir)
            clObj.put("globalSlider", cl.globalSlider)

            // Scores
            if (cl.scores.isNotEmpty()) {
                val scoresObj = JSONObject()
                for ((key, score) in cl.scores) scoresObj.put(key, score.toDouble())
                clObj.put("scores", scoresObj)
                if (cl.scoreAnchorKey != null) clObj.put("scoreAnchorKey", cl.scoreAnchorKey)
            }

            if (cl.propFilters.isNotEmpty()) {
                val pfArr = JSONArray()
                for ((field, values) in cl.propFilters) {
                    val entry = JSONArray()
                    entry.put(field)
                    val vals = JSONArray()
                    for (v in values) vals.put(v)
                    entry.put(vals)
                    pfArr.put(entry)
                }
                clObj.put("propFilters", pfArr)
            }

            clustersArr.put(clObj)
        }
        json.put("clusters", clustersArr)
        return json.toString()
    }

    // ── Parse JSON to clusters ──

    fun jsonToClusters(text: String): SessionLoadResult {
        val json = JSONObject(text)
        val version = json.optInt("version", 1)
        if (version != 1) throw Exception("Unknown session version: $version")

        val activeId = if (json.isNull("activeClusterId")) null else json.optString("activeClusterId")

        // Extract top-level scoring dictionary (global)
        val topDictJson = json.optString("scoringDict", "")
        val topUseDict = json.optBoolean("scoringUseDict", true)
        val topNormalizeDigits = json.optBoolean("scoringNormalizeDigits", false)

        val clustersArr = json.getJSONArray("clusters")
        val clusters = mutableListOf<Cluster>()

        for (i in 0 until clustersArr.length()) {
            val clObj = clustersArr.getJSONObject(i)
            val tracesArr = clObj.getJSONArray("traces")
            val traceEntries = mutableListOf<TraceEntry>()

            for (j in 0 until tracesArr.length()) {
                val tObj = tracesArr.getJSONObject(j)
                val entry = TraceParser.normalizeTrace(tObj)
                if (entry != null) traceEntries.add(entry)
            }

            val traceStates = traceEntries.map { TraceState.fromEntry(it) }
            val verdicts = mutableMapOf<String, Verdict>()
            val verdictsArr = clObj.optJSONArray("verdicts")
            if (verdictsArr != null) {
                for (j in 0 until verdictsArr.length()) {
                    val pair = verdictsArr.getJSONArray(j)
                    val key = pair.getString(0)
                    val v = when (pair.getString(1)) {
                        "like" -> Verdict.LIKE
                        "dislike" -> Verdict.DISLIKE
                        "discard" -> Verdict.DISCARD
                        else -> continue
                    }
                    verdicts[key] = v
                }
            }

            val sortFieldStr = clObj.optString("sortField", "index")
            val sortField = when (sortFieldStr) {
                "startup_dur" -> SortField.STARTUP_DUR
                "cosine_similarity" -> SortField.COSINE_SIMILARITY
                "manual_score" -> SortField.MANUAL_SCORE
                else -> SortField.INDEX
            }

            val cluster = Cluster(
                id = clObj.getString("id"),
                name = clObj.getString("name"),
                traces = traceStates,
                verdicts = verdicts,
                overviewFilter = try {
                    OverviewFilter.valueOf(clObj.optString("overviewFilter", "all").uppercase())
                } catch (_: Exception) { OverviewFilter.ALL },
                sortField = sortField,
                sortDir = clObj.optInt("sortDir", 1),
                globalSlider = clObj.optInt("globalSlider", 100)
            )

            val pfArr = clObj.optJSONArray("propFilters")
            if (pfArr != null) {
                for (j in 0 until pfArr.length()) {
                    val entry = pfArr.getJSONArray(j)
                    val field = entry.getString(0)
                    val vals = entry.getJSONArray(1)
                    val valueSet = mutableSetOf<String>()
                    for (k in 0 until vals.length()) valueSet.add(vals.getString(k))
                    cluster.propFilters[field] = valueSet
                }
            }

            // Restore scores
            val scoresObj = clObj.optJSONObject("scores")
            if (scoresObj != null) {
                for (key in scoresObj.keys()) {
                    cluster.scores[key] = scoresObj.getDouble(key).toFloat()
                }
                cluster.scoreAnchorKey = clObj.optString("scoreAnchorKey", null)
            }

            cluster.recomputeCounts()
            if (traceStates.isNotEmpty()) traceStates[0].ensureCache()
            clusters.add(cluster)
        }

        // Prefer top-level dict; fall back to first cluster's legacy dict
        val finalDictJson = if (topDictJson.isNotEmpty()) topDictJson
            else clusters.firstNotNullOfOrNull { cl ->
                val clObj = clustersArr.getJSONObject(clusters.indexOf(cl))
                val legacy = clObj.optString("scoringDict", "")
                legacy.ifEmpty { null }
            }
        // For useDict/normalizeDigits, prefer top-level; fall back to first cluster's legacy values
        val finalUseDict = if (json.has("scoringUseDict")) topUseDict
            else {
                val firstClObj = if (clustersArr.length() > 0) clustersArr.getJSONObject(0) else null
                firstClObj?.optBoolean("scoringUseDict", true) ?: true
            }
        val finalNormalizeDigits = if (json.has("scoringNormalizeDigits")) topNormalizeDigits
            else {
                val firstClObj = if (clustersArr.length() > 0) clustersArr.getJSONObject(0) else null
                firstClObj?.optBoolean("scoringNormalizeDigits", false) ?: false
            }

        return SessionLoadResult(
            clusters = clusters,
            activeClusterId = activeId,
            scoringDictJson = finalDictJson,
            scoringUseDict = finalUseDict,
            scoringNormalizeDigits = finalNormalizeDigits
        )
    }

    // ── Save session to SQLite ──

    suspend fun saveSession(
        context: Context,
        sessionName: String,
        clusters: List<Cluster>,
        activeClusterId: String?,
        scoringDict: ScoringDictionary? = null,
        scoringUseDict: Boolean = true,
        scoringNormalizeDigits: Boolean = false,
        sessionId: String = UUID.randomUUID().toString()
    ): String {
        val jsonData = clustersToJson(clusters, activeClusterId, scoringDict, scoringUseDict, scoringNormalizeDigits)
        val traceCount = clusters.sumOf { it.traces.size }
        val now = System.currentTimeMillis()

        dao(context).upsertSession(SessionEntity(
            id = sessionId,
            name = sessionName,
            createdAt = now,
            updatedAt = now,
            clusterCount = clusters.size,
            traceCount = traceCount,
            jsonData = jsonData
        ))
        return sessionId
    }

    // ── Update existing session (same ID, new data) ──

    suspend fun updateSession(
        context: Context,
        sessionId: String,
        clusters: List<Cluster>,
        activeClusterId: String?,
        scoringDict: ScoringDictionary? = null,
        scoringUseDict: Boolean = true,
        scoringNormalizeDigits: Boolean = false
    ) {
        val existing = dao(context).getSession(sessionId) ?: return
        val jsonData = clustersToJson(clusters, activeClusterId, scoringDict, scoringUseDict, scoringNormalizeDigits)
        val traceCount = clusters.sumOf { it.traces.size }

        dao(context).upsertSession(existing.copy(
            updatedAt = System.currentTimeMillis(),
            clusterCount = clusters.size,
            traceCount = traceCount,
            jsonData = jsonData
        ))
    }

    // ── Load session from SQLite ──

    suspend fun loadSession(context: Context, sessionId: String): SessionLoadResult? {
        val entity = dao(context).getSession(sessionId) ?: return null
        return jsonToClusters(entity.jsonData)
    }

    // ── Get session JSON for export ──

    suspend fun getSessionJson(context: Context, sessionId: String): String? {
        return dao(context).getSession(sessionId)?.jsonData
    }

    // ── List all sessions ──

    suspend fun listSessions(context: Context): List<SessionMeta> {
        return dao(context).listSessions()
    }

    // ── Delete ──

    suspend fun deleteSession(context: Context, sessionId: String) {
        dao(context).deleteSession(sessionId)
    }

    suspend fun deleteAllSessions(context: Context) {
        dao(context).deleteAllSessions()
        dao(context).clearAppState()
    }

    // ── Auto-save current app state (survives process death) ──

    suspend fun saveCurrentState(
        context: Context,
        activeSessionId: String?,
        activeClusterId: String?,
        clusters: List<Cluster>,
        scoringDict: ScoringDictionary? = null,
        scoringUseDict: Boolean = true,
        scoringNormalizeDigits: Boolean = false
    ) {
        val stateJson = if (clusters.isNotEmpty()) {
            clustersToJson(clusters, activeClusterId, scoringDict, scoringUseDict, scoringNormalizeDigits)
        } else null

        dao(context).saveAppState(AppStateEntity(
            key = "current",
            activeSessionId = activeSessionId,
            activeClusterId = activeClusterId,
            stateJson = stateJson
        ))
    }

    // ── Restore current app state ──

    suspend fun restoreCurrentState(context: Context): SessionLoadResult? {
        val state = dao(context).getAppState() ?: return null
        if (state.stateJson == null) return SessionLoadResult(emptyList(), null, sessionId = state.activeSessionId)
        return try {
            val result = jsonToClusters(state.stateJson)
            result.copy(
                activeClusterId = result.activeClusterId ?: state.activeClusterId,
                sessionId = state.activeSessionId
            )
        } catch (_: Exception) { null }
    }

    // ── Import from external JSON text ──

    fun parseExternalJson(text: String): SessionLoadResult = jsonToClusters(text)

    // ── Session count ──

    suspend fun sessionCount(context: Context): Int = dao(context).sessionCount()
}
