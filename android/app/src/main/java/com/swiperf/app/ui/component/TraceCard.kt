package com.swiperf.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.TraceState
import com.swiperf.app.data.model.Verdict
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format

@Composable
fun TraceCard(
    traceState: TraceState,
    index: Int,
    verdict: Verdict?,
    onVerdictChange: (Verdict) -> Unit,
    onCardClick: () -> Unit,
    onSliderChange: (Int) -> Unit,
    onSliceTap: (MergedSlice) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (verdict) {
            Verdict.LIKE -> PerfettoColors.POSITIVE_COLOR
            Verdict.DISLIKE -> PerfettoColors.NEGATIVE_COLOR
            Verdict.DISCARD -> PerfettoColors.DISCARD_COLOR
            null -> Color.Transparent
        },
        label = "verdictAccent"
    )

    val bgTint by animateColorAsState(
        targetValue = when (verdict) {
            Verdict.LIKE -> PerfettoColors.POSITIVE_COLOR.copy(alpha = 0.04f)
            Verdict.DISLIKE -> PerfettoColors.NEGATIVE_COLOR.copy(alpha = 0.04f)
            Verdict.DISCARD -> PerfettoColors.DISCARD_COLOR.copy(alpha = 0.04f)
            null -> Color.Transparent
        },
        label = "verdictBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            if (verdict != null) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgTint)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Index + package + duration
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        traceState.trace.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (traceState.trace.startupDur > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Format.fmtDur(traceState.trace.startupDur),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VerdictButtons(
                        currentVerdict = verdict,
                        onVerdict = onVerdictChange
                    )
                }

                // Mini timeline
                MiniTimeline(
                    traceState = traceState,
                    onSliceTapped = onSliceTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )

                // Compression slider
                if (traceState.origN > 2) {
                    CompressionSlider(
                        label = "Slices",
                        value = traceState.sliderValue.toFloat(),
                        valueLabel = "${traceState.currentSeq.size}",
                        range = 2f..traceState.origN.toFloat(),
                        onValueChange = { onSliderChange(it.toInt()) },
                        suffix = "/ ${traceState.origN}"
                    )
                }
            }
        }
    }
}
