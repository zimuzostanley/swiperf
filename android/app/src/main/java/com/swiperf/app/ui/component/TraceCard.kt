package com.swiperf.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    version: Long,
    onVerdictChange: (Verdict) -> Unit,
    onCardClick: () -> Unit,
    onSliderChange: (Int) -> Unit,
    onSliceTap: (MergedSlice, onDismiss: () -> Unit) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (verdict) {
            Verdict.LIKE -> PerfettoColors.POSITIVE_COLOR
            Verdict.DISLIKE -> PerfettoColors.NEGATIVE_COLOR
            Verdict.DISCARD -> PerfettoColors.DISCARD_COLOR
            null -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "accent"
    )

    val sliderValue = remember(version) { traceState.sliderValue }
    val seqSize = remember(version) { traceState.currentSeq.size }
    var highlightIdx by remember { mutableStateOf<Int?>(null) }

    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                if (verdict != null) {
                    drawRect(accentColor, Offset.Zero, Size(3.dp.toPx(), size.height))
                }
            }
            .clickable { onCardClick() }
            .padding(start = if (verdict != null) 6.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onTogglePin != null) {
                Icon(
                    if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    null,
                    modifier = Modifier.size(16.dp).clickable { onTogglePin() },
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(traceState.trace.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (traceState.trace.startupDur > 0) {
                Spacer(Modifier.width(6.dp))
                Text(Format.fmtDur(traceState.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            VerdictButtons(currentVerdict = verdict, onVerdict = onVerdictChange)
        }

        // Timeline — seq/totalDur passed directly so Compose detects changes
        val seq = remember(version) { traceState.currentSeq }
        val totalDur = remember(version) { traceState.totalDur }
        MiniTimeline(
            seq = seq,
            totalDur = totalDur,
            highlightIndex = highlightIdx,
            onSliceTapped = { idx, slice ->
                highlightIdx = idx
                onSliceTap(slice) { highlightIdx = null }
            },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
        )

        // Slider
        if (traceState.origN > 2) {
            CompressionSlider(
                label = "",
                value = sliderValue.toFloat(),
                valueLabel = "$seqSize",
                range = 2f..traceState.origN.toFloat(),
                onValueChange = { onSliderChange(it.toInt()) },
                suffix = "/ ${traceState.origN}"
            )
        }
    }
}
