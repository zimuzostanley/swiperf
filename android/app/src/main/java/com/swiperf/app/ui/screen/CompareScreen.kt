package com.swiperf.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.compare.ComparisonResult
import com.swiperf.app.data.compare.CrossCompare
import com.swiperf.app.data.compare.CrossCompareState
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.ui.component.*
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format
import kotlinx.coroutines.launch
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

    if (anchorKey == null) {
        AnchorSelectionScreen(cluster = cluster, onSelectAnchor = onSetAnchor, onClose = onClose)
        return
    }

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

    // Local version counter to force recomposition on slider change
    var localVersion by remember { mutableStateOf(0L) }
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var anchorExpanded by remember { mutableStateOf(false) }
    var otherCollapsed by remember { mutableStateOf(false) }
    var showAnchorBreakdown by remember { mutableStateOf(false) }
    var showOtherBreakdown by remember { mutableStateOf(false) }
    var showSliceDetail by remember { mutableStateOf<Pair<MergedSlice, Long>?>(null) }
    val onRowTap: (com.swiperf.app.data.model.SummaryRow) -> Unit = { r ->
        snackScope.launch { snackbar.showSnackbar("${r.label}: ${Format.fmtDur(r.dur)}", duration = SnackbarDuration.Short) }
    }

    // Swipe state
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f
    var swipeAction by remember { mutableStateOf<String?>(null) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (swipeAction != null) {
            if (swipeAction == "positive") 1000f else -1000f
        } else offsetX,
        animationSpec = if (swipeAction != null) tween(250) else spring(stiffness = Spring.StiffnessMedium),
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

    val isDark = LocalIsDarkTheme.current
    val anchorIdx = cluster.traces.indexOfFirst { it.key == anchorKey }
    val otherIdx = cluster.traces.indexOfFirst { it.key == otherKey }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    if (ccState.history.isNotEmpty()) {
                        IconButton(onClick = {
                            onUndo()
                            offsetX = 0f
                            localVersion++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                    }
                    Text(
                        "${progress.pct}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { progress.pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${progress.completed} / ${progress.total} pairs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))

                    // Action buttons: −ve | skip | +ve
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Negative — triggers swipe animation left
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                swipeAction = "negative"
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = PerfettoColors.NEGATIVE_COLOR.copy(alpha = 0.15f),
                                contentColor = PerfettoColors.NEGATIVE_COLOR
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("\u2212ve", fontWeight = FontWeight.SemiBold) }

                        Spacer(Modifier.width(8.dp))

                        // Skip
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSkip()
                                offsetX = 0f
                                localVersion++
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("skip") }

                        Spacer(Modifier.width(8.dp))

                        // Positive — triggers swipe animation right
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                swipeAction = "positive"
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = PerfettoColors.POSITIVE_COLOR.copy(alpha = 0.15f),
                                contentColor = PerfettoColors.POSITIVE_COLOR
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("+ve", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            // ── Anchor trace (expandable) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                    .clickable { anchorExpanded = !anchorExpanded }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text("#${anchorIdx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(anchorTrace.trace.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (anchorTrace.trace.startupDur > 0) {
                        Text(Format.fmtDur(anchorTrace.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (anchorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                key(localVersion) {
                    MiniTimeline(
                        traceState = anchorTrace,
                        onSliceTapped = { _, slice -> showSliceDetail = slice to anchorTrace.totalDur },
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    )
                }

                AnimatedVisibility(visible = anchorExpanded) {
                    Column(Modifier.padding(top = 8.dp)) {
                        // Slider
                        if (anchorTrace.origN > 2) {
                            val anchorSliderVal = remember(localVersion) { anchorTrace.sliderValue }
                            val anchorSeqSize = remember(localVersion) { anchorTrace.currentSeq.size }
                            CompressionSlider(
                                label = "", value = anchorSliderVal.toFloat(),
                                valueLabel = "$anchorSeqSize",
                                range = 2f..anchorTrace.origN.toFloat(),
                                onValueChange = { anchorTrace.updateSlider(it.toInt()); localVersion++ },
                                suffix = "/ ${anchorTrace.origN}"
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showAnchorBreakdown = true }) {
                            Text("breakdown", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Swipe hint (clean, subtle) ──
            val swipeFrac = (offsetX / swipeThreshold).coerceIn(-1f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "\u2190 \u2212ve",
                    style = MaterialTheme.typography.labelSmall,
                    color = PerfettoColors.NEGATIVE_COLOR.copy(alpha = if (swipeFrac < 0) 0.4f + abs(swipeFrac) * 0.6f else 0.3f)
                )
                Text(
                    "swipe to decide",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (swipeFrac == 0f) 0.5f else 0.2f)
                )
                Text(
                    "+ve \u2192",
                    style = MaterialTheme.typography.labelSmall,
                    color = PerfettoColors.POSITIVE_COLOR.copy(alpha = if (swipeFrac > 0) 0.4f + swipeFrac * 0.6f else 0.3f)
                )
            }

            // ── Swipeable comparison card ──
            val bgColor = when {
                offsetX > swipeThreshold / 3 -> PerfettoColors.POSITIVE_COLOR.copy(alpha = (offsetX / swipeThreshold * 0.12f).coerceIn(0f, 0.12f))
                offsetX < -swipeThreshold / 3 -> PerfettoColors.NEGATIVE_COLOR.copy(alpha = (abs(offsetX) / swipeThreshold * 0.12f).coerceIn(0f, 0.12f))
                else -> Color.Transparent
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                    .graphicsLayer { rotationZ = animatedOffset / 60f }
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .background(bgColor)
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
                        ) { _, dragAmount -> offsetX += dragAmount }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${otherIdx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(otherTrace.trace.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (otherTrace.trace.startupDur > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(Format.fmtDur(otherTrace.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (otherCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        null, Modifier.size(18.dp).clickable { otherCollapsed = !otherCollapsed },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))
                key(localVersion) {
                    MiniTimeline(
                        traceState = otherTrace,
                        onSliceTapped = { _, slice -> showSliceDetail = slice to otherTrace.totalDur },
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    )
                }

                AnimatedVisibility(visible = !otherCollapsed) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Slider
                        if (otherTrace.origN > 2) {
                            val otherSliderVal = remember(localVersion) { otherTrace.sliderValue }
                            val otherSeqSize = remember(localVersion) { otherTrace.currentSeq.size }
                            CompressionSlider(
                                label = "", value = otherSliderVal.toFloat(),
                                valueLabel = "$otherSeqSize",
                                range = 2f..otherTrace.origN.toFloat(),
                                onValueChange = { otherTrace.updateSlider(it.toInt()); localVersion++ },
                                suffix = "/ ${otherTrace.origN}"
                            )
                        }

                        // Inline breakdown
                        val breakdown = buildBreakdownData(otherTrace.currentSeq, otherTrace.totalDur, isDark)
                        BreakdownSection("States", breakdown.states, breakdown.totalDur, onRowTap = onRowTap)
                        if (breakdown.names.isNotEmpty()) {
                            BreakdownSection("Names", breakdown.names, breakdown.totalDur, onRowTap = onRowTap)
                        }

                        TextButton(onClick = { showOtherBreakdown = true }) {
                            Text("full breakdown", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    // Sheets
    if (showAnchorBreakdown) {
        BreakdownSheet(traceState = anchorTrace, onDismiss = { showAnchorBreakdown = false })
    }
    if (showOtherBreakdown) {
        BreakdownSheet(traceState = otherTrace, onDismiss = { showOtherBreakdown = false })
    }
    showSliceDetail?.let { (slice, totalDur) ->
        SliceDetailSheet(slice = slice, totalDur = totalDur, onDismiss = { showSliceDetail = null })
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
                        Text("Tap the trace to compare others against", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(cluster.traces, key = { _, ts -> ts.key }) { index, ts ->
                ts.ensureCache()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onSelectAnchor(ts.key) }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(ts.trace.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (ts.trace.startupDur > 0) {
                            Text(Format.fmtDur(ts.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MiniTimeline(traceState = ts, modifier = Modifier.clip(RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
