package com.swiperf.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.compare.ComparisonResult
import com.swiperf.app.data.compare.CrossCompare
import com.swiperf.app.data.compare.CrossCompareState
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.TraceState
import com.swiperf.app.ui.component.MiniTimeline
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    cluster: Cluster,
    ccState: CrossCompareState,
    anchorKey: String?,
    onSetAnchor: (String) -> Unit,
    onRecordComparison: (ComparisonResult) -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val progress = CrossCompare.getProgress(ccState)

    // If no anchor selected yet, show anchor selection
    if (anchorKey == null) {
        AnchorSelectionScreen(
            cluster = cluster,
            onSelectAnchor = onSetAnchor,
            onClose = onClose
        )
        return
    }

    // If complete, navigate to review
    LaunchedEffect(ccState.isComplete) {
        if (ccState.isComplete) onNavigateToReview()
    }

    val pair = ccState.currentPair
    if (pair == null) return

    val anchorTrace = cluster.traces.find { it.key == anchorKey }
    val otherKey = if (pair.first == anchorKey) pair.second else pair.first
    val otherTrace = cluster.traces.find { it.key == otherKey }

    if (anchorTrace == null || otherTrace == null) return
    anchorTrace.ensureCache()
    otherTrace.ensureCache()

    // Swipe state
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f
    var swipeAction by remember { mutableStateOf<String?>(null) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (swipeAction != null) {
            if (swipeAction == "positive") 1000f else -1000f
        } else offsetX,
        animationSpec = if (swipeAction != null) tween(300) else spring(),
        finishedListener = {
            if (swipeAction != null) {
                when (swipeAction) {
                    "positive" -> onRecordComparison(ComparisonResult.POSITIVE)
                    "negative" -> onRecordComparison(ComparisonResult.NEGATIVE)
                }
                swipeAction = null
                offsetX = 0f
            }
        },
        label = "swipe"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    Text(
                        "${progress.pct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        },
        floatingActionButton = {
            if (ccState.history.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onUndo(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Icon(Icons.Default.Undo, "Undo")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { progress.pct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Text(
                "${progress.completed} / ${progress.total} pairs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Anchor trace (pinned at top)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ANCHOR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(anchorTrace.trace.packageName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (anchorTrace.trace.startupDur > 0) {
                            Text(Format.fmtDur(anchorTrace.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    MiniTimeline(traceState = anchorTrace, modifier = Modifier.clip(RoundedCornerShape(4.dp)))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Hint
            val hintColor = when {
                offsetX > swipeThreshold / 2 -> PerfettoColors.POSITIVE_COLOR
                offsetX < -swipeThreshold / 2 -> PerfettoColors.NEGATIVE_COLOR
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val hintText = when {
                offsetX > swipeThreshold / 2 -> "\u2192 Positive"
                offsetX < -swipeThreshold / 2 -> "Negative \u2190"
                else -> "\u2190 Negative    |    Positive \u2192"
            }
            Text(
                hintText,
                style = MaterialTheme.typography.labelSmall,
                color = hintColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            // Swipeable comparison card
            val bgColor = when {
                offsetX > swipeThreshold / 3 -> PerfettoColors.POSITIVE_COLOR.copy(alpha = (offsetX / swipeThreshold * 0.15f).coerceIn(0f, 0.15f))
                offsetX < -swipeThreshold / 3 -> PerfettoColors.NEGATIVE_COLOR.copy(alpha = (abs(offsetX) / swipeThreshold * 0.15f).coerceIn(0f, 0.15f))
                else -> Color.Transparent
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .graphicsLayer {
                        rotationZ = animatedOffset / 50f
                    }
                    .pointerInput(otherKey) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX > swipeThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swipeAction = "positive"
                                } else if (offsetX < -swipeThreshold) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swipeAction = "negative"
                                } else {
                                    offsetX = 0f
                                }
                            },
                            onDragCancel = { offsetX = 0f }
                        ) { _, dragAmount ->
                            offsetX += dragAmount
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val idx = cluster.traces.indexOfFirst { it.key == otherKey }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(otherTrace.trace.packageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (otherTrace.trace.startupDur > 0) {
                            Text(Format.fmtDur(otherTrace.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    MiniTimeline(traceState = otherTrace, modifier = Modifier.clip(RoundedCornerShape(4.dp)))

                    // Quick breakdown
                    val isDark = com.swiperf.app.ui.theme.LocalIsDarkTheme.current
                    val breakdown = com.swiperf.app.ui.component.buildBreakdownData(otherTrace.currentSeq, otherTrace.totalDur, isDark)
                    com.swiperf.app.ui.component.BreakdownSection("States", breakdown.states, breakdown.totalDur)
                    if (breakdown.names.isNotEmpty()) {
                        com.swiperf.app.ui.component.BreakdownSection("Names", breakdown.names, breakdown.totalDur)
                    }
                }
            }

            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSkip()
                }) {
                    Icon(Icons.Default.SkipNext, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnchorSelectionScreen(
    cluster: Cluster,
    onSelectAnchor: (String) -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Select Anchor", fontWeight = FontWeight.SemiBold)
                        Text("Tap a trace to use as reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(cluster.traces, key = { _, ts -> ts.key }) { index, ts ->
                ts.ensureCache()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectAnchor(ts.key) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ts.trace.packageName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ts.trace.startupDur > 0) {
                                Text(Format.fmtDur(ts.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
