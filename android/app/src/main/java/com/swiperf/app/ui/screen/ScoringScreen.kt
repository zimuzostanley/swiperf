package com.swiperf.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.data.scoring.ScoringState
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    scoringState: ScoringState,
    version: Long,
    anchorName: String,
    targetName: String,
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
    var lastCascadeCount by remember { mutableStateOf(0) }

    BackHandler { onClose() }

    // Re-read mutable state keyed on version
    val scoreDisplay = remember(version) {
        if (scoringState.score.isNaN()) "—" else "${(scoringState.score * 100).toInt()}%"
    }
    val region = remember(version) { scoringState.nextRegionIndex?.let { scoringState.regions[it] } }
    val isComplete = remember(version) { scoringState.isComplete }
    val differingResolved = remember(version) { scoringState.differingResolved }
    val differingTotal = remember(version) { scoringState.differingTotal }
    val historySize = remember(version) { scoringState.history.size }

    fun copyName(name: String?) {
        val text = name ?: "null"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", text))
        scope.launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    if (historySize > 0) {
                        IconButton(onClick = { onUndo(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                    }
                    Text(
                        scoreDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Progress
                    LinearProgressIndicator(
                        progress = { if (differingTotal > 0) differingResolved.toFloat() / differingTotal else 1f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("$differingResolved / $differingTotal regions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (lastCascadeCount > 0) {
                            Text("  +$lastCascadeCount matched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (isComplete) {
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done \u00b7 $scoreDisplay", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val before = differingResolved
                                    onVerdict(RegionVerdict.DIFFERENT)
                                    lastCascadeCount = 0 // will update on next recomposition
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PerfettoColors.NEGATIVE_COLOR,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("different", fontWeight = FontWeight.SemiBold) }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val before = differingResolved
                                    onVerdict(RegionVerdict.SAME)
                                    lastCascadeCount = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PerfettoColors.POSITIVE_COLOR,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
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
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            if (isComplete) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(scoreDisplay, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("similarity score", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (region != null) {
                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Timeline bars — anchor vs target
                    Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    RegionBar(
                        stateColor = PerfettoColors.stateColor(region.anchorState, region.anchorIoWait, isDark),
                        nameColor = if (region.anchorName != null) PerfettoColors.nameColor(region.anchorName) else PerfettoColors.nameRowFallback(isDark),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                    )
                    Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RegionBar(
                        stateColor = PerfettoColors.stateColor(region.targetState, region.targetIoWait, isDark),
                        nameColor = if (region.targetName != null) PerfettoColors.nameColor(region.targetName) else PerfettoColors.nameRowFallback(isDark),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                    )

                    // Duration
                    Text("${(region.duration * 100).toInt()}% of trace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // Always show state + name for both sides (full context)
                    // State row
                    Row(Modifier.fillMaxWidth()) {
                        Text("state", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(region.anchorState, region.anchorIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                PerfettoColors.stateLabel(region.anchorState, region.anchorIoWait),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                fontWeight = if (region.anchorState != region.targetState) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clickable { copyName(region.anchorState) }
                            )
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(region.targetState, region.targetIoWait, isDark)))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                PerfettoColors.stateLabel(region.targetState, region.targetIoWait),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                fontWeight = if (region.anchorState != region.targetState) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clickable { copyName(region.targetState) }
                            )
                        }
                    }

                    // Name row
                    Row(Modifier.fillMaxWidth()) {
                        Text("name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                        Text(
                            region.anchorName ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (region.anchorName != region.targetName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clickable { copyName(region.anchorName) }
                        )
                        Text(
                            region.targetName ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (region.anchorName != region.targetName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clickable { copyName(region.targetName) }
                        )
                    }

                    // IO wait row (only if either has it)
                    if (region.anchorIoWait != null || region.targetIoWait != null) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("io", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                            Text("${region.anchorIoWait ?: "—"}", style = MaterialTheme.typography.bodySmall, fontWeight = if (region.anchorIoWait != region.targetIoWait) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text("${region.targetIoWait ?: "—"}", style = MaterialTheme.typography.bodySmall, fontWeight = if (region.anchorIoWait != region.targetIoWait) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        }
                    }

                    // Blocked function row (only if either has it)
                    if (region.anchorBlockedFn != null || region.targetBlockedFn != null) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                            Text(region.anchorBlockedFn ?: "—", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (region.anchorBlockedFn != region.targetBlockedFn) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f).clickable { copyName(region.anchorBlockedFn) })
                            Text(region.targetBlockedFn ?: "—", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (region.anchorBlockedFn != region.targetBlockedFn) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f).clickable { copyName(region.targetBlockedFn) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionBar(
    stateColor: androidx.compose.ui.graphics.Color,
    nameColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stateH = with(density) { 10.dp.toPx() }
    val gapH = with(density) { 1.dp.toPx() }
    val nameH = with(density) { 14.dp.toPx() }

    Canvas(modifier = modifier.height(25.dp)) {
        drawRect(stateColor, Offset.Zero, Size(size.width, stateH))
        drawRect(nameColor, Offset(0f, stateH + gapH), Size(size.width, nameH))
    }
}
