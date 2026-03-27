package com.swiperf.app.ui.util

import org.junit.Assert.*
import org.junit.Test

class FormatTest {

    @Test
    fun fmtDur_microseconds() {
        assertEquals("500 \u00b5s", Format.fmtDur(500_000))
    }

    @Test
    fun fmtDur_milliseconds() {
        assertEquals("5.0 ms", Format.fmtDur(5_000_000))
    }

    @Test
    fun fmtDur_seconds() {
        assertEquals("1.500 s", Format.fmtDur(1_500_000_000))
    }

    @Test
    fun fmtPct_normal() {
        assertEquals("50.0%", Format.fmtPct(500, 1000))
    }

    @Test
    fun fmtPct_zeroTotal() {
        assertEquals("0.0%", Format.fmtPct(100, 0))
    }
}
