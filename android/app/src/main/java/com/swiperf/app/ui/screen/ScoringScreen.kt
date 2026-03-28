package com.swiperf.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

    // Manual scoring stats (excludes auto-matched)
    val manualSamePct = remember(version) { scoringState.manualSamePct }
    val manualDiffPct = remember(version) { scoringState.manualDiffPct }
    val manualReviewedPct = remember(version) { scoringState.manualReviewedPct }
    val autoMatchedPct = remember(version) { scoringState.autoSamePct }
    val scoreDisplay = if (manualReviewedPct == 0) "\u2014" else "${manualSamePct}%"
    val region = remember(version) { scoringState.nextRegionIndex?.let { scoringState.regions[it] } }
    val isComplete = remember(version) { scoringState.isComplete }
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
                        IconButton(onClick = { onReset() }) {
                            Icon(Icons.Default.Refresh, "Reset")
                        }
                    }
                    Text(scoreDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Progress of manual review
                    LinearProgressIndicator(
                        progress = { manualReviewedPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${manualReviewedPct}% reviewed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${autoMatchedPct}% auto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isComplete) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done \u00b7 $scoreDisplay", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Swipe area with growing fill bar
                        var swipeOffset by remember { mutableFloatStateOf(0f) }
                        val swipeThreshold = 150f // ~40% of typical screen width
                        val frac = (swipeOffset / swipeThreshold).coerceIn(-1f, 1f)
                        val absFrac = kotlin.math.abs(frac)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (swipeOffset > swipeThreshold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onVerdict(RegionVerdict.SAME)
                                            } else if (swipeOffset < -swipeThreshold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onVerdict(RegionVerdict.DIFFERENT)
                                            }
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = { swipeOffset = 0f }
                                    ) { _, dragAmount -> swipeOffset += dragAmount }
                                }
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
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
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
                    Text("${(region.duration * 100).toInt()}% of trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // ── Details by field section ──
                    FieldSection("state", region, isDark, ::copy) { state, ioWait ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(state, ioWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(PerfettoColors.stateLabel(state, ioWait), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { copy(state) })
                        }
                    }

                    FieldSection("name", region, isDark, ::copy) { name, _ ->
                        Text(name ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { copy(name) })
                    }

                    if (region.anchorIoWait != null || region.targetIoWait != null) {
                        FieldRow("io wait", "${region.anchorIoWait ?: "\u2014"}", "${region.targetIoWait ?: "\u2014"}", region.anchorIoWait != region.targetIoWait, ::copy)
                    }

                    if (region.anchorBlockedFn != null || region.targetBlockedFn != null) {
                        FieldRow("blocked fn", region.anchorBlockedFn ?: "\u2014", region.targetBlockedFn ?: "\u2014", region.anchorBlockedFn != region.targetBlockedFn, ::copy)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldSection(
    label: String,
    region: ScoringRegion,
    isDark: Boolean,
    copy: (String?) -> Unit,
    content: @Composable (anchorVal: String?, ioWait: Int?) -> Unit
) {
    val anchorVal = when (label) { "state" -> region.anchorState; "name" -> region.anchorName; else -> null }
    val targetVal = when (label) { "state" -> region.targetState; "name" -> region.targetName; else -> null }
    val differs = anchorVal != targetVal

    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal, color = if (differs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { content(anchorVal, region.anchorIoWait) }
            Box(Modifier.weight(1f)) { content(targetVal, region.targetIoWait) }
        }
    }
}

@Composable
private fun FieldRow(label: String, anchorVal: String, targetVal: String, differs: Boolean, copy: (String?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (differs) FontWeight.SemiBold else FontWeight.Normal, color = if (differs) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(anchorVal, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { copy(anchorVal) })
            Text(targetVal, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { copy(targetVal) })
        }
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
