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
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.Verdict
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraceCard(
    packageName: String,
    startupDur: Long,
    index: Int,
    verdict: Verdict?,
    seq: List<MergedSlice>,
    totalDur: Long,
    onVerdictChange: (Verdict) -> Unit,
    onCardClick: () -> Unit,
    onSliceTap: (MergedSlice, onDismiss: () -> Unit) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
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

    var highlightIdx by remember { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(6.dp)

    // Swipe to vote
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f }, // 40% of width — significant intentional swipe needed
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right → positive
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVerdictChange(Verdict.LIKE)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left → negative
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVerdictChange(Verdict.DISLIKE)
                }
                else -> {}
            }
            false // Don't actually dismiss — just register the vote
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> PerfettoColors.POSITIVE_COLOR.copy(alpha = 0.15f)
                    SwipeToDismissBoxValue.EndToStart -> PerfettoColors.NEGATIVE_COLOR.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                label = "swipeBg"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.ThumbUp
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.ThumbDown
                else -> null
            }
            val iconTint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> PerfettoColors.POSITIVE_COLOR
                SwipeToDismissBoxValue.EndToStart -> PerfettoColors.NEGATIVE_COLOR
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        // Card content
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                Text(packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (startupDur > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(Format.fmtDur(startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Timeline
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
        }
    }
}
