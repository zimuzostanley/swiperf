package com.swiperf.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.scoring.GlobalScoringState
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalScoringScreen(
    globalState: GlobalScoringState,
    version: Long,
    onVerdict: (RegionVerdict, Int) -> Unit, // verdict + entry index
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    BackHandler { onClose() }

    val avgScore = remember(version) { globalState.avgScore }
    val scoreDisplay = if (avgScore == 0 && globalState.resolvedCount == 0) "\u2014" else "${avgScore}%"
    val isComplete = remember(version) { globalState.isComplete }
    val resolvedCount = remember(version) { globalState.resolvedCount }
    val totalEntries = globalState.entries.size
    val progress = if (totalEntries > 0) resolvedCount.toFloat() / totalEntries else 0f

    val currentEntryIdx = remember(version) { globalState.nextEntryIndex }
    val entry = currentEntryIdx?.let { globalState.entries[it] }

    // Swipe state
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f
    val frac = (swipeOffset / swipeThreshold).coerceIn(-1f, 1f)
    val absFrac = kotlin.math.abs(frac)

    fun onSwipeEnd() {
        val idx = currentEntryIdx ?: return
        if (swipeOffset > swipeThreshold) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onVerdict(RegionVerdict.SAME, idx)
        } else if (swipeOffset < -swipeThreshold) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onVerdict(RegionVerdict.DIFFERENT, idx)
        }
        swipeOffset = 0f
    }

    val swipeModifier = if (!isComplete) Modifier
        .pointerInput(version) {
            detectHorizontalDragGestures(
                onDragEnd = { onSwipeEnd() },
                onDragCancel = { swipeOffset = 0f }
            ) { _, dragAmount -> swipeOffset += dragAmount }
        }
        .pointerInput(version) {
            detectVerticalDragGestures(
                onDragEnd = { onSwipeEnd() },
                onDragCancel = { swipeOffset = 0f }
            ) { _, dragAmount -> swipeOffset -= dragAmount }
        }
    else Modifier

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare All", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = {
                    if (resolvedCount > 0) {
                        IconButton(onClick = { onUndo(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                    }
                    IconButton(onClick = { onReset() }) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                    Text(scoreDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$resolvedCount / $totalEntries entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (isComplete) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done \u00b7 $scoreDisplay avg", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .then(swipeModifier)
                        ) {
                            // Growing fill bar from edge
                            if (absFrac > 0.03f) {
                                val fillColor = if (frac > 0) PerfettoColors.POSITIVE_COLOR else PerfettoColors.NEGATIVE_COLOR
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(absFrac)
                                        .align(if (frac > 0) Alignment.CenterStart else Alignment.CenterEnd)
                                        .background(fillColor.copy(alpha = 0.15f + absFrac * 0.15f))
                                )
                            }

                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "\u2190 Different",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (frac < -0.5f) FontWeight.Bold else FontWeight.Medium,
                                    color = if (frac < -0.1f)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f + absFrac * 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    "Same \u2192",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (frac > 0.5f) FontWeight.Bold else FontWeight.Medium,
                                    color = if (frac > 0.1f)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f + frac * 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).then(swipeModifier)
        ) {
            if (isComplete) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(scoreDisplay, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("average similarity across ${globalState.totalTraces} traces", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (entry != null) {
                // Info header
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "affects ${entry.traceCount} trace${if (entry.traceCount != 1) "s" else ""} \u00b7 ${"%.2f".format(entry.totalDurationPct)}% avg",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Field cards (same layout as ScoringScreen)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Duration
                    Text("${"%.2f".format(entry.totalDurationPct)}% avg of trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Zoomed bars
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(2.dp))
                            GlobalRegionBar(
                                stateColor = PerfettoColors.stateColor(entry.anchorState, entry.anchorIoWait, isDark),
                                nameColor = if (entry.anchorName != null) PerfettoColors.nameColor(entry.anchorName) else PerfettoColors.nameRowFallback(isDark),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            GlobalRegionBar(
                                stateColor = PerfettoColors.stateColor(entry.targetState, entry.targetIoWait, isDark),
                                nameColor = if (entry.targetName != null) PerfettoColors.nameColor(entry.targetName) else PerfettoColors.nameRowFallback(isDark),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                FieldCard("state", entry.anchorState != entry.targetState) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(entry.anchorState, entry.anchorIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(PerfettoColors.stateLabel(entry.anchorState, entry.anchorIoWait), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(entry.targetState, entry.targetIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(PerfettoColors.stateLabel(entry.targetState, entry.targetIoWait), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                FieldCard("name", entry.anchorName != entry.targetName) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (entry.anchorName != null) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.nameColor(entry.anchorName)))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(entry.anchorName ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (entry.targetName != null) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.nameColor(entry.targetName)))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(entry.targetName ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (entry.anchorIoWait != null || entry.targetIoWait != null) {
                    FieldCard("io wait", entry.anchorIoWait != entry.targetIoWait) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${entry.anchorIoWait ?: "\u2014"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${entry.targetIoWait ?: "\u2014"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (entry.anchorBlockedFn != null || entry.targetBlockedFn != null) {
                    FieldCard("blocked fn", entry.anchorBlockedFn != entry.targetBlockedFn) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.anchorBlockedFn ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(entry.targetBlockedFn ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalRegionBar(stateColor: androidx.compose.ui.graphics.Color, nameColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.Canvas(modifier = modifier.height(22.dp)) {
        val stateH = with(density) { 8.dp.toPx() }
        val gapH = with(density) { 1.dp.toPx() }
        val nameH = with(density) { 12.dp.toPx() }
        drawRect(stateColor, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(size.width, stateH))
        drawRect(nameColor, androidx.compose.ui.geometry.Offset(0f, stateH + gapH), androidx.compose.ui.geometry.Size(size.width, nameH))
    }
}
