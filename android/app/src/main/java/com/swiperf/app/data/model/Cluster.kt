package com.swiperf.app.data.model

import java.util.UUID

data class VerdictCounts(
    val positive: Int = 0,
    val negative: Int = 0,
    val pending: Int = 0,
    val discarded: Int = 0
)

data class Cluster(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val traces: List<TraceState>,
    val verdicts: MutableMap<String, Verdict> = mutableMapOf(),
    var overviewFilter: OverviewFilter = OverviewFilter.ALL,
    var counts: VerdictCounts = VerdictCounts(),
    val tableSortState: MutableMap<String, SortState> = mutableMapOf(),
    var sortField: SortField = SortField.INDEX,
    var sortDir: Int = 1,
    val propFilters: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    var globalSlider: Int = 100
) {
    fun recomputeCounts() {
        var positive = 0; var negative = 0; var discarded = 0
        for (v in verdicts.values) {
            when (v) {
                Verdict.LIKE -> positive++
                Verdict.DISLIKE -> negative++
                Verdict.DISCARD -> discarded++
            }
        }
        counts = VerdictCounts(positive, negative, traces.size - positive - negative - discarded, discarded)
    }

    fun setVerdict(traceKey: String, verdict: Verdict) {
        if (verdicts[traceKey] == verdict) verdicts.remove(traceKey)
        else verdicts[traceKey] = verdict
        recomputeCounts()
    }

    fun filterTraces(filter: OverviewFilter): List<TraceState> {
        var result = when (filter) {
            OverviewFilter.POSITIVE -> traces.filter { verdicts[it.key] == Verdict.LIKE }
            OverviewFilter.NEGATIVE -> traces.filter { verdicts[it.key] == Verdict.DISLIKE }
            OverviewFilter.PENDING -> traces.filter { verdicts[it.key] == null }
            OverviewFilter.DISCARDED -> traces.filter { verdicts[it.key] == Verdict.DISCARD }
            OverviewFilter.ALL -> traces.toList()
        }
        // Apply property filters
        if (propFilters.isNotEmpty()) {
            result = result.filter { ts ->
                propFilters.all { (field, allowed) ->
                    val value = traceFieldValue(ts, field)
                    allowed.contains(value)
                }
            }
        }
        // Apply sorting
        if (sortField == SortField.STARTUP_DUR) {
            result = result.sortedBy { it.trace.startupDur * sortDir }
        }
        return result
    }

    fun getFilterableFields(): List<String> {
        val fields = listOf("startup_type", "package_name", "device_name", "unique_session_name")
        return fields.filter { field ->
            val vals = mutableSetOf<String>()
            for (ts in traces) {
                vals.add(traceFieldValue(ts, field))
                if (vals.size >= 2) return@filter true
            }
            false
        }
    }

    fun getFieldValues(field: String): List<String> {
        return traces.map { traceFieldValue(it, field) }.distinct().sorted()
    }

    fun togglePropFilter(field: String, value: String) {
        val allowed = propFilters[field]
        if (allowed == null) {
            propFilters[field] = mutableSetOf(value)
        } else if (allowed.contains(value)) {
            allowed.remove(value)
            if (allowed.isEmpty()) propFilters.remove(field)
        } else {
            allowed.add(value)
            val all = getFieldValues(field)
            if (allowed.size == all.size) propFilters.remove(field)
        }
    }

    fun clearPropFilter(field: String) {
        propFilters.remove(field)
    }

    fun updateGlobalSlider(pct: Int) {
        globalSlider = pct
        val frac = pct / 100.0
        for (ts in traces) {
            val target = maxOf(2, Math.round(2 + (ts.origN - 2) * frac).toInt())
            ts.updateSlider(target)
        }
    }

    companion object {
        private fun traceFieldValue(ts: TraceState, field: String): String {
            return when (field) {
                "trace_uuid" -> ts.trace.traceUuid
                "package_name" -> ts.trace.packageName
                "startup_dur" -> ts.trace.startupDur.toString()
                else -> (ts.trace.extra?.get(field) as? String) ?: ""
            }
        }
    }
}
