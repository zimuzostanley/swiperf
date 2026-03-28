package com.swiperf.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler { onClose() }

    val scoreDisplay = remember(version) {
        if (scoringState.score.isNaN()) "\u2014" else "${(scoringState.score * 100).toInt()}%"
    }
    val region = remember(version) { scoringState.nextRegionIndex?.let { scoringState.regions[it] } }
    val isComplete = remember(version) { scoringState.isComplete }
    val differingResolved = remember(version) { scoringState.differingResolved }
    val differingTotal = remember(version) { scoringState.differingTotal }
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
                    Text(scoreDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (differingTotal > 0) differingResolved.toFloat() / differingTotal else 1f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("$differingResolved / $differingTotal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    if (isComplete) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done \u00b7 $scoreDisplay", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVerdict(RegionVerdict.DIFFERENT) },
                                colors = ButtonDefaults.buttonColors(containerColor = PerfettoColors.NEGATIVE_COLOR, contentColor = androidx.compose.ui.graphics.Color.White),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("different", fontWeight = FontWeight.SemiBold) }
                            Button(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onVerdict(RegionVerdict.SAME) },
                                colors = ButtonDefaults.buttonColors(containerColor = PerfettoColors.POSITIVE_COLOR, contentColor = androidx.compose.ui.graphics.Color.White),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("same", fontWeight = FontWeight.SemiBold) }
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

/** Full trace timeline with the current region highlighted (rest dimmed). */
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

    Canvas(modifier = Modifier.fillMaxWidth().height(19.dp).clip(RoundedCornerShape(3.dp))) {
        if (totalDur == 0L) return@Canvas
        val scale = size.width / totalDur.toFloat()
        drawRect(PerfettoColors.canvasBg(isDark))

        for (d in seq) {
            val x = d.tsRel * scale
            val w = maxOf(d.dur * scale, minW)
            // Check overlap: slice [sliceStart, sliceEnd) vs region [regionStart, regionEnd)
            val sliceStart = d.tsRel.toDouble() / totalDur
            val sliceEnd = (d.tsRel + d.dur).toDouble() / totalDur
            val inRegion = sliceStart < regionEnd + 0.001 && sliceEnd > regionStart - 0.001
            val alpha = if (inRegion) 1f else 0.2f

            drawRect(PerfettoColors.stateColor(d.state, d.ioWait, isDark).copy(alpha = alpha), Offset(x, 0f), Size(w, stateH))
            val nc = if (d.name != null) PerfettoColors.nameColor(d.name) else PerfettoColors.nameRowFallback(isDark)
            drawRect(nc.copy(alpha = alpha), Offset(x, stateH + gapH), Size(w, nameH))
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
