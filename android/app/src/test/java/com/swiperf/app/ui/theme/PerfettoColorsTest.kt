package com.swiperf.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class PerfettoColorsTest {

    @Test
    fun perfettoHash_matchesJavaScriptExactly() {
        // Verified against: node -e "function perfetto_hash(s,m){let h=0x811c9dc5&0xfffffff;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=(h*16777619)&0xffffffff}return Math.abs(h)%m}"
        assertEquals(40, PerfettoColors.perfettoHash("binder transaction", 360))
        assertEquals(218, PerfettoColors.perfettoHash("bindApplication", 360))
        assertEquals(216, PerfettoColors.perfettoHash("activityStart", 360))
        assertEquals(220, PerfettoColors.perfettoHash("inflate", 360))
        assertEquals(74, PerfettoColors.perfettoHash("Choreographer#doFrame", 360))
        assertEquals(112, PerfettoColors.perfettoHash("Lock contention", 360))
        assertEquals(142, PerfettoColors.perfettoHash("monitor contention", 360))
        assertEquals(272, PerfettoColors.perfettoHash("measure", 360))
    }

    @Test
    fun stateColor_running_isGreen() {
        val color = PerfettoColors.stateColor("Running", null, false)
        // #317d31 = RGB(49, 125, 49)
        assertEquals(49, (color.red * 255).toInt())
        assertEquals(125, (color.green * 255).toInt())
        assertEquals(49, (color.blue * 255).toInt())
    }

    @Test
    fun stateColor_runnable_isLime() {
        val color = PerfettoColors.stateColor("Runnable", null, false)
        // #99ba36 = RGB(153, 186, 54)
        assertEquals(153, (color.red * 255).toInt())
        assertEquals(186, (color.green * 255).toInt())
        assertEquals(54, (color.blue * 255).toInt())
    }

    @Test
    fun stateColor_ioWait_isOrange() {
        val color = PerfettoColors.stateColor("Uninterruptible Sleep", 1, false)
        // #ff9900 = RGB(255, 153, 0)
        assertEquals(255, (color.red * 255).toInt())
        assertEquals(153, (color.green * 255).toInt())
        assertEquals(0, (color.blue * 255).toInt())
    }

    @Test
    fun stateColor_nonIoSleep_isDesatRed() {
        val color = PerfettoColors.stateColor("Uninterruptible Sleep", 0, false)
        // #a25b57 matches web app
        assertEquals(162, (color.red * 255).toInt())
        assertEquals(91, (color.green * 255).toInt())
        assertEquals(87, (color.blue * 255).toInt())
    }

    @Test
    fun stateColor_sleeping_lightIsWhite() {
        val color = PerfettoColors.stateColor("Sleeping", null, false)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
    }

    @Test
    fun stateColor_sleeping_darkIsDarkBlue() {
        val color = PerfettoColors.stateColor("Sleeping", null, true)
        // #2a2a3a
        assertEquals(0x2a, (color.red * 255).toInt())
        assertEquals(0x2a, (color.green * 255).toInt())
        assertEquals(0x3a, (color.blue * 255).toInt())
    }

    @Test
    fun nameColor_isDeterministic() {
        val c1 = PerfettoColors.nameColor("bindApplication")
        val c2 = PerfettoColors.nameColor("bindApplication")
        assertEquals(c1, c2)
    }

    @Test
    fun nameColor_stripsTrailingDigits() {
        val c1 = PerfettoColors.nameColor("foo123")
        val c2 = PerfettoColors.nameColor("foo456")
        assertEquals(c1, c2)
    }

    @Test
    fun nameColor_differentNamesGetDifferentColors() {
        val c1 = PerfettoColors.nameColor("bindApplication")
        val c2 = PerfettoColors.nameColor("activityStart")
        assertNotEquals(c1, c2)
    }

    @Test
    fun stateLabel_ioWait() {
        assertEquals("Unint. Sleep (IO)", PerfettoColors.stateLabel("Uninterruptible Sleep", 1))
        assertEquals("Unint. Sleep (non-IO)", PerfettoColors.stateLabel("Uninterruptible Sleep", 0))
    }
}
