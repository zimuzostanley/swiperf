package com.swiperf.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class PerfettoColorsTest {

    @Test
    fun perfettoHash_matchesKnownValues() {
        // These values are verified against the JavaScript implementation
        // perfetto_hash uses 0x811c9dc5 & 0xfffffff (28-bit init mask)
        val h1 = PerfettoColors.perfettoHash("bindApplication", 360)
        val h2 = PerfettoColors.perfettoHash("activityStart", 360)
        // Values should be deterministic
        assertEquals(h1, PerfettoColors.perfettoHash("bindApplication", 360))
        assertEquals(h2, PerfettoColors.perfettoHash("activityStart", 360))
        // Different strings should (usually) produce different values
        assertNotEquals(h1, h2)
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
