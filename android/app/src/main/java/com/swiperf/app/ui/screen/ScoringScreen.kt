package com.swiperf.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.data.scoring.ScoringRegion
import com.swiperf.app.data.scoring.ScoringState
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    scoringState: ScoringState,
    version: Long,
    anchorSeq: List<MergedSlice>,
    anchorTotalDur: Long,
    targetSeq: List<MergedSlice>,
    targetTotalDur: Long,
    onVerdict: (RegionVerdict) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler { onClose() }

    val b = remember(version) { scoringState.breakdown }
    val remaining = b.remainingPct
    val scoreDisplay = if (b.samePct + b.diffPct == 0) "\u2014" else "${b.samePct}%"
    val region = remember(version) { scoringState.nextRegionIndex?.let { scoringState.regions[it] } }
    val isComplete = remember(version) { scoringState.isComplete }

    // Swipe state — shared between content area and bottom bar
    // Swipe: right/up = same, left/down = different
    var swipeX by remember { mutableFloatStateOf(0f) }
    var swipeY by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f
    // Use whichever axis has more movement
    val dominant = if (kotlin.math.abs(swipeX) >= kotlin.math.abs(swipeY)) swipeX else -swipeY // up is negative Y, map to positive
    val frac = (dominant / swipeThreshold).coerceIn(-1f, 1f)
    val absFrac = kotlin.math.abs(frac)
    val swipeModifier = if (!isComplete) Modifier.pointerInput(version) {
        detectDragGestures(
            onDragEnd = {
                if (dominant > swipeThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVerdict(RegionVerdict.SAME)
                } else if (dominant < -swipeThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVerdict(RegionVerdict.DIFFERENT)
                }
                swipeX = 0f; swipeY = 0f
            },
            onDragCancel = { swipeX = 0f; swipeY = 0f }
        ) { _, dragAmount -> swipeX += dragAmount.x; swipeY += dragAmount.y }
    } else Modifier
    val historySize = remember(version) { scoringState.history.size }

    fun copy(text: String?) {
        val t = text ?: "null"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", t))
        scope.launch { snackbar.showSnackbar(t, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = {
                    if (historySize > 0) {
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
                        progress = { (100 - remaining) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(6.dp))
                    Spacer(Modifier.height(8.dp))
                    if (isComplete) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done \u00b7 $scoreDisplay", fontWeight = FontWeight.SemiBold)
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

                            // Labels
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "\u2190 different",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (frac < -0.5f) FontWeight.SemiBold else FontWeight.Normal,
                                    color = PerfettoColors.NEGATIVE_COLOR.copy(alpha = if (frac < -0.1f) 0.5f + absFrac * 0.5f else 0.3f)
                                )
                                Text(
                                    "same \u2192",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (frac > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                                    color = PerfettoColors.POSITIVE_COLOR.copy(alpha = if (frac > 0.1f) 0.5f + frac * 0.5f else 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).then(swipeModifier).verticalScroll(rememberScrollState())
        ) {
            if (isComplete) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(scoreDisplay, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("similarity score", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (region != null) {
                // ── Full tracks with highlighted region ──
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    FullTrackWithHighlight(seq = anchorSeq, totalDur = anchorTotalDur, regionStart = region.start, regionEnd = region.end, isDark = isDark)
                    Spacer(Modifier.height(6.dp))
                    Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    FullTrackWithHighlight(seq = targetSeq, totalDur = targetTotalDur, regionStart = region.start, regionEnd = region.end, isDark = isDark)
                }

                Spacer(Modifier.height(8.dp))

                // ── Zoomed region card ──
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
                    Text("${"%.2f".format(region.duration * 100)}% of trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Zoomed bars
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(2.dp))
                            RegionBar(
                                stateColor = PerfettoColors.stateColor(region.anchorState, region.anchorIoWait, isDark),
                                nameColor = if (region.anchorName != null) PerfettoColors.nameColor(region.anchorName) else PerfettoColors.nameRowFallback(isDark),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            RegionBar(
                                stateColor = PerfettoColors.stateColor(region.targetState, region.targetIoWait, isDark),
                                nameColor = if (region.targetName != null) PerfettoColors.nameColor(region.targetName) else PerfettoColors.nameRowFallback(isDark),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                }

                // ── Field details in separate containers ──
                Spacer(Modifier.height(6.dp))

                FieldCard("state", region.anchorState != region.targetState) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(region.anchorState, region.anchorIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(PerfettoColors.stateLabel(region.anchorState, region.anchorIoWait), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { copy(region.anchorState) })
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(region.targetState, region.targetIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(PerfettoColors.stateLabel(region.targetState, region.targetIoWait), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { copy(region.targetState) })
                        }
                    }
                }

                FieldCard("name", region.anchorName != region.targetName) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (region.anchorName != null) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.nameColor(region.anchorName)))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(region.anchorName ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { copy(region.anchorName) })
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (region.targetName != null) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.nameColor(region.targetName)))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(region.targetName ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { copy(region.targetName) })
                        }
                    }
                }

                if (region.anchorIoWait != null || region.targetIoWait != null) {
                    FieldCard("io wait", region.anchorIoWait != region.targetIoWait) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${region.anchorIoWait ?: "\u2014"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${region.targetIoWait ?: "\u2014"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (region.anchorBlockedFn != null || region.targetBlockedFn != null) {
                    FieldCard("blocked fn", region.anchorBlockedFn != region.targetBlockedFn) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(region.anchorBlockedFn ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { copy(region.anchorBlockedFn) })
                            Text(region.targetBlockedFn ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { copy(region.targetBlockedFn) })
                        }
                    }
                }
            }
        }
    }
}

/** Individual field container with label and border highlight when differs. */
@Composable
private fun FieldCard(label: String, differs: Boolean, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(
                0.5.dp,
                if (differs) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(6.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal,
            color = if (differs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/** Full trace timeline with the current region highlighted (rest dimmed).
 *  Draws all slices at full alpha, then overlays dimming rectangles OUTSIDE
 *  the region. Both anchor and target use the same regionStart/regionEnd
 *  so the highlight is perfectly aligned. */
@Composable
private fun FullTrackWithHighlight(
    seq: List<MergedSlice>,
    totalDur: Long,
    regionStart: Double,
    regionEnd: Double,
    isDark: Boolean
) {
    val density = LocalDensity.current
    val minW = with(density) { 0.5.dp.toPx() }
    val stateH = with(density) { 8.dp.toPx() }
    val gapH = with(density) { 1.dp.toPx() }
    val nameH = with(density) { 10.dp.toPx() }
    val dimColor = if (isDark) androidx.compose.ui.graphics.Color(0xCC111318) else androidx.compose.ui.graphics.Color(0xCCF5F4F0)

    Canvas(modifier = Modifier.fillMaxWidth().height(19.dp).clip(RoundedCornerShape(3.dp))) {
        if (totalDur == 0L) return@Canvas
        val scale = size.width / totalDur.toFloat()
        drawRect(PerfettoColors.canvasBg(isDark))

        // Draw all slices at full alpha
        for (d in seq) {
            val x = d.tsRel * scale
            val w = maxOf(d.dur * scale, minW)
            drawRect(PerfettoColors.stateColor(d.state, d.ioWait, isDark), Offset(x, 0f), Size(w, stateH))
            val nc = if (d.name != null) PerfettoColors.nameColor(d.name) else PerfettoColors.nameRowFallback(isDark)
            drawRect(nc, Offset(x, stateH + gapH), Size(w, nameH))
        }

        // Dim everything outside the region with an overlay
        val regionStartPx = (regionStart * size.width).toFloat()
        val regionEndPx = (regionEnd * size.width).toFloat()
        if (regionStartPx > 0) {
            drawRect(dimColor, Offset.Zero, Size(regionStartPx, size.height))
        }
        if (regionEndPx < size.width) {
            drawRect(dimColor, Offset(regionEndPx, 0f), Size(size.width - regionEndPx, size.height))
        }
    }
}

@Composable
private fun RegionBar(stateColor: androidx.compose.ui.graphics.Color, nameColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Canvas(modifier = modifier.height(22.dp)) {
        val stateH = with(density) { 8.dp.toPx() }
        val gapH = with(density) { 1.dp.toPx() }
        val nameH = with(density) { 12.dp.toPx() }
        drawRect(stateColor, Offset.Zero, Size(size.width, stateH))
        drawRect(nameColor, Offset(0f, stateH + gapH), Size(size.width, nameH))
    }
}
