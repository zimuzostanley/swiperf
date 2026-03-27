package com.swiperf.app.ui.util

object Format {
    fun fmtDur(ns: Long): String {
        if (ns >= 1_000_000_000L) return "%.3f s".format(ns / 1e9)
        if (ns >= 1_000_000L) return "%.1f ms".format(ns / 1e6)
        return "${ns / 1000} \u00b5s"
    }

    fun fmtPct(ns: Long, total: Long): String {
        if (total == 0L) return "0.0%"
        return "%.1f%%".format(ns.toDouble() / total * 100)
    }
}
