package com.swiperf.app.data.model

enum class Verdict { LIKE, DISLIKE, DISCARD }

enum class OverviewFilter { ALL, POSITIVE, NEGATIVE, PENDING, DISCARDED }

data class SortState(var col: String = "dur", var dir: Int = -1)

enum class SortField { INDEX, STARTUP_DUR }

data class SummaryRow(
    val label: String,
    val short: String,
    val dur: Long,
    val count: Int,
    val color: Long, // ARGB color as Long for Compose Color(long)
    val pct: Float
)
