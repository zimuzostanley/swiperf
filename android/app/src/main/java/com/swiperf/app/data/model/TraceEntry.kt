package com.swiperf.app.data.model

data class TraceEntry(
    val traceUuid: String,
    val packageName: String,
    val startupDur: Long,
    val slices: List<Slice>,
    val extra: Map<String, Any?>? = null
)
