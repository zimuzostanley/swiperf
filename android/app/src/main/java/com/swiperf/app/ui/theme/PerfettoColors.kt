package com.swiperf.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * HSLuv minimal port (Apache 2.0) — used for procedural name-based colors.
 * Must match Perfetto's colorizer.ts exactly.
 */
private class Hsluv {
    var hex = "#000000"
    var rgb_r = 0.0; var rgb_g = 0.0; var rgb_b = 0.0
    var xyz_x = 0.0; var xyz_y = 0.0; var xyz_z = 0.0
    var luv_l = 0.0; var luv_u = 0.0; var luv_v = 0.0
    var lch_l = 0.0; var lch_c = 0.0; var lch_h = 0.0
    var hsluv_h = 0.0; var hsluv_s = 0.0; var hsluv_l = 0.0
    var r0s = 0.0; var r0i = 0.0; var r1s = 0.0; var r1i = 0.0
    var g0s = 0.0; var g0i = 0.0; var g1s = 0.0; var g1i = 0.0
    var b0s = 0.0; var b0i = 0.0; var b1s = 0.0; var b1i = 0.0

    companion object {
        const val refY = 1.0; const val refU = 0.19783000664283; const val refV = 0.46831999493879
        const val kappa = 903.2962962; const val epsilon = 0.0088564516
        const val m_r0 = 3.240969941904521; const val m_r1 = -1.537383177570093; const val m_r2 = -0.498610760293
        const val m_g0 = -0.96924363628087; const val m_g1 = 1.87596750150772; const val m_g2 = 0.041555057407175
        const val m_b0 = 0.055630079696993; const val m_b1 = -0.20397695888897; const val m_b2 = 1.056971514242878

        fun fromLinear(c: Double): Double = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        fun lToY(L: Double): Double = if (L <= 8) refY * L / kappa else refY * ((L + 16) / 116.0).pow(3.0)
        fun rgbChannelToHex(c: Double): String {
            val n = (c * 255).roundToInt().coerceIn(0, 255)
            return "%02x".format(n)
        }
        fun distanceFromOriginAngle(s: Double, i: Double, a: Double): Double {
            val d = i / (sin(a) - s * cos(a))
            return if (d < 0) Double.MAX_VALUE else d
        }
    }

    fun rgbToHex() {
        hex = "#${rgbChannelToHex(rgb_r)}${rgbChannelToHex(rgb_g)}${rgbChannelToHex(rgb_b)}"
    }

    fun xyzToRgb() {
        rgb_r = fromLinear(m_r0 * xyz_x + m_r1 * xyz_y + m_r2 * xyz_z)
        rgb_g = fromLinear(m_g0 * xyz_x + m_g1 * xyz_y + m_g2 * xyz_z)
        rgb_b = fromLinear(m_b0 * xyz_x + m_b1 * xyz_y + m_b2 * xyz_z)
    }

    fun luvToXyz() {
        if (luv_l == 0.0) { xyz_x = 0.0; xyz_y = 0.0; xyz_z = 0.0; return }
        val vU = luv_u / (13 * luv_l) + refU
        val vV = luv_v / (13 * luv_l) + refV
        xyz_y = lToY(luv_l)
        xyz_x = -9 * xyz_y * vU / ((vU - 4) * vV - vU * vV)
        xyz_z = (9 * xyz_y - 15 * vV * xyz_y - vV * xyz_x) / (3 * vV)
    }

    fun lchToLuv() {
        val r = lch_h / 180 * PI
        luv_l = lch_l; luv_u = cos(r) * lch_c; luv_v = sin(r) * lch_c
    }

    fun calculateBoundingLines(l: Double) {
        val sub1 = (l + 16).pow(3) / 1560896.0
        val sub2 = if (sub1 > epsilon) sub1 else l / kappa
        val s1r = sub2 * (284517 * m_r0 - 94839 * m_r2)
        val s2r = sub2 * (838422 * m_r2 + 769860 * m_r1 + 731718 * m_r0)
        val s3r = sub2 * (632260 * m_r2 - 126452 * m_r1)
        val s1g = sub2 * (284517 * m_g0 - 94839 * m_g2)
        val s2g = sub2 * (838422 * m_g2 + 769860 * m_g1 + 731718 * m_g0)
        val s3g = sub2 * (632260 * m_g2 - 126452 * m_g1)
        val s1b = sub2 * (284517 * m_b0 - 94839 * m_b2)
        val s2b = sub2 * (838422 * m_b2 + 769860 * m_b1 + 731718 * m_b0)
        val s3b = sub2 * (632260 * m_b2 - 126452 * m_b1)
        r0s = s1r / s3r; r0i = s2r * l / s3r; r1s = s1r / (s3r + 126452); r1i = (s2r - 769860) * l / (s3r + 126452)
        g0s = s1g / s3g; g0i = s2g * l / s3g; g1s = s1g / (s3g + 126452); g1i = (s2g - 769860) * l / (s3g + 126452)
        b0s = s1b / s3b; b0i = s2b * l / s3b; b1s = s1b / (s3b + 126452); b1i = (s2b - 769860) * l / (s3b + 126452)
    }

    fun calcMaxChromaHsluv(h: Double): Double {
        val r = h / 360 * PI * 2
        return minOf(
            distanceFromOriginAngle(r0s, r0i, r), distanceFromOriginAngle(r1s, r1i, r),
            distanceFromOriginAngle(g0s, g0i, r), distanceFromOriginAngle(g1s, g1i, r),
            distanceFromOriginAngle(b0s, b0i, r), distanceFromOriginAngle(b1s, b1i, r)
        )
    }

    fun hsluvToLch() {
        if (hsluv_l > 99.9999999) { lch_l = 100.0; lch_c = 0.0 }
        else if (hsluv_l < 0.00000001) { lch_l = 0.0; lch_c = 0.0 }
        else { lch_l = hsluv_l; calculateBoundingLines(hsluv_l); lch_c = calcMaxChromaHsluv(hsluv_h) / 100 * hsluv_s }
        lch_h = hsluv_h
    }

    fun hsluvToHex() { hsluvToLch(); lchToLuv(); luvToXyz(); xyzToRgb(); rgbToHex() }
}

// ── Standard HSL → Color (matches Perfetto's hslToRgb exactly) ──

private fun hslToColor(h: Int, s: Int, l: Int): Color {
    val sf = s / 100f; val lf = l / 100f
    val c = (1 - abs(2 * lf - 1)) * sf
    val x = c * (1 - abs(((h / 60f) % 2) - 1))
    val m = lf - c / 2
    var r = 0f; var g = 0f; var b = 0f
    when {
        h < 60 -> { r = c; g = x }
        h < 120 -> { r = x; g = c }
        h < 180 -> { g = c; b = x }
        h < 240 -> { g = x; b = c }
        h < 300 -> { r = x; b = c }
        else -> { r = c; b = x }
    }
    return Color(r + m, g + m, b + m)
}

object PerfettoColors {

    // ── FNV-1a hash — exact Perfetto implementation ──
    // Note: Perfetto uses 0xfffffff (28-bit mask) for the initial value,
    // NOT 0xffffffff. This produces different results from standard FNV-1a.
    fun perfettoHash(s: String, max: Int): Int {
        // Must match JavaScript's (h * 16777619) & 0xffffffff exactly.
        // JS uses 64-bit floats (53-bit mantissa) so large multiplications lose
        // precision. We simulate this by doing the multiply in Double, then
        // converting back — matching the exact bit pattern JS produces.
        var h = (0x811c9dc5L and 0xfffffffL).toDouble()  // 28-bit init
        for (ch in s) {
            h = (h.toLong() xor ch.code.toLong()).toDouble()
            // JS: (h * 16777619) & 0xffffffff — float64 multiply then bitwise AND
            val mult = h * 16777619.0
            // JS & 0xffffffff: convert to signed 32-bit integer
            h = (mult.toLong() and 0xffffffffL).let {
                // Reinterpret as signed 32-bit (JS |0 semantics)
                if (it > Int.MAX_VALUE) (it - 0x100000000L).toDouble() else it.toDouble()
            }
        }
        return (abs(h.toInt()) % max)
    }

    private val nameColorCache = mutableMapOf<String, Color>()

    /**
     * Assign a color to a slice/function name using Perfetto's default algorithm.
     * 1. Strip trailing digits from name
     * 2. Hash seed → hue (0-359) in HSLuv
     * 3. Fixed saturation = 80
     * 4. Hash (seed + 'x') → lightness (40-79) in HSLuv
     */
    fun nameColor(name: String): Color {
        val seed = name.replace(Regex("( )?\\d+"), "")
        nameColorCache[seed]?.let { return it }
        val hue = perfettoHash(seed, 360)
        val lightness = perfettoHash(seed + "x", 40) + 40
        val conv = Hsluv()
        conv.hsluv_h = hue.toDouble()
        conv.hsluv_s = 80.0
        conv.hsluv_l = lightness.toDouble()
        conv.hsluvToHex()
        val hex = conv.hex
        val r = hex.substring(1, 3).toInt(16) / 255f
        val g = hex.substring(3, 5).toInt(16) / 255f
        val b = hex.substring(5, 7).toInt(16) / 255f
        val color = Color(r, g, b)
        nameColorCache[seed] = color
        return color
    }

    // ── Thread state colors — standard HSL, matches Perfetto exactly ──
    private val STATE_RUNNING = hslToColor(120, 44, 34)
    private val STATE_RUNNABLE = hslToColor(75, 55, 47)
    private val STATE_IO_WAIT = hslToColor(36, 100, 50)
    private val STATE_NONIO = hslToColor(3, 30, 49)
    private val STATE_CREATED = hslToColor(0, 0, 70)
    private val STATE_UNKNOWN_LIGHT = hslToColor(44, 63, 91)
    private val STATE_DEAD = hslToColor(0, 0, 62)
    private val STATE_INDIGO = hslToColor(231, 48, 48)

    fun stateColor(state: String?, ioWait: Int?, isDark: Boolean): Color {
        if (state == null) return if (isDark) Color(0xFF44444E) else STATE_UNKNOWN_LIGHT
        if (state == "Created") return STATE_CREATED
        if (state == "Running") return STATE_RUNNING
        if (state.startsWith("Runnable")) return STATE_RUNNABLE
        if (state.contains("Uninterruptible Sleep")) {
            if (state.contains("non-IO") || ioWait == 0) return STATE_NONIO
            return STATE_IO_WAIT
        }
        if (state.contains("Dead")) return STATE_DEAD
        if (state.contains("Sleeping") || state.contains("Idle")) {
            return if (isDark) Color(0xFF2A2A3A) else Color.White
        }
        if (state.contains("Unknown")) return if (isDark) Color(0xFF44444E) else STATE_UNKNOWN_LIGHT
        return STATE_INDIGO
    }

    fun stateLabel(state: String?, ioWait: Int?): String {
        if (state == null) return "Unknown"
        if (state == "Uninterruptible Sleep") {
            if (ioWait == 1) return "Unint. Sleep (IO)"
            if (ioWait == 0) return "Unint. Sleep (non-IO)"
            return "Unint. Sleep"
        }
        return state
    }

    /** Canvas background for timeline */
    fun canvasBg(isDark: Boolean): Color = if (isDark) Color(0xFF17171A) else Color.White

    /** Name row fallback color when name is null */
    fun nameRowFallback(isDark: Boolean): Color = if (isDark) Color(0xFF1C1C26) else Color(0xFFEDE9E2)

    /** Verdict colors */
    val POSITIVE_COLOR = Color(0xFF2E7D32)
    val NEGATIVE_COLOR = Color(0xFFC62828)
    val DISCARD_COLOR = Color(0xFF757575)
}
