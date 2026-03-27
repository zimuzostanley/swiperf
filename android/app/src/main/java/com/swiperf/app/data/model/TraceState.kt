package com.swiperf.app.data.model

import com.swiperf.app.data.compression.Compression

data class TraceState(
    val trace: TraceEntry,
    val key: String,
    val totalDur: Long,
    val origN: Int,
    var sliderValue: Int,
    var currentSeq: List<MergedSlice> = emptyList()
) {
    var cache: Map<Int, List<MergedSlice>>? = null
        private set

    fun ensureCache() {
        if (cache != null) return
        cache = Compression.buildMergeCache(trace.slices)
        currentSeq = Compression.getCompressed(cache!!, origN, sliderValue)
    }

    fun updateSlider(value: Int) {
        ensureCache()
        sliderValue = value
        currentSeq = Compression.getCompressed(cache!!, origN, value)
    }

    companion object {
        fun traceKey(t: TraceEntry): String {
            val startupId = (t.extra?.get("startup_id") as? String) ?: ""
            return "${t.traceUuid}|${t.packageName}|$startupId|${t.startupDur}"
        }

        fun fromEntry(entry: TraceEntry): TraceState {
            val slices = entry.slices
            val totalDur = if (slices.isNotEmpty()) {
                slices.maxOf { (it.ts - slices[0].ts) + it.dur }
            } else 0L
            return TraceState(
                trace = entry,
                key = traceKey(entry),
                totalDur = totalDur,
                origN = slices.size,
                sliderValue = slices.size
            )
        }
    }
}
