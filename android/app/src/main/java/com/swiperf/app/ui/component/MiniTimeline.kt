package com.swiperf.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.TraceState
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors

@Composable
fun MiniTimeline(
    traceState: TraceState,
    modifier: Modifier = Modifier,
    onSliceTapped: ((MergedSlice) -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    val seq = traceState.currentSeq
    val totalDur = traceState.totalDur
    val density = LocalDensity.current
    val minWidth = with(density) { 0.5.dp.toPx() }
    val stateRowH = with(density) { 12.dp.toPx() }
    val gapH = with(density) { 2.dp.toPx() }
    val nameRowH = with(density) { 16.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(seq, totalDur) {
                if (onSliceTapped == null || totalDur == 0L) return@pointerInput
                detectTapGestures { offset ->
                    val scale = size.width.toFloat() / totalDur
                    // Find tapped slice (reverse order for overlapping)
                    for (i in seq.indices.reversed()) {
                        val d = seq[i]
                        val x = d.tsRel * scale
                        val w = maxOf(d.dur * scale, minWidth)
                        if (offset.x >= x && offset.x <= x + w) {
                            onSliceTapped(d)
                            break
                        }
                    }
                }
            }
    ) {
        if (totalDur == 0L) return@Canvas
        val scale = size.width / totalDur.toFloat()

        // Background
        drawRect(PerfettoColors.canvasBg(isDark))

        // Draw slices
        for (d in seq) {
            val x = d.tsRel * scale
            val w = maxOf(d.dur * scale, minWidth)

            // State row (top)
            drawRect(
                color = PerfettoColors.stateColor(d.state, d.ioWait, isDark),
                topLeft = Offset(x, 0f),
                size = Size(w, stateRowH)
            )

            // Name row (bottom, after gap)
            val nameColor = if (d.name != null) PerfettoColors.nameColor(d.name)
                else PerfettoColors.nameRowFallback(isDark)
            drawRect(
                color = nameColor,
                topLeft = Offset(x, stateRowH + gapH),
                size = Size(w, nameRowH)
            )
        }
    }
}
