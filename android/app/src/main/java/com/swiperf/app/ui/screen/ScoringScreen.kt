package com.swiperf.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.data.scoring.ScoringRegion
import com.swiperf.app.data.scoring.ScoringState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    scoringState: ScoringState,
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

    val scoreDisplay = if (scoringState.score.isNaN()) "—" else "${(scoringState.score * 100).toInt()}%"
    val region = scoringState.nextRegionIndex?.let { scoringState.regions[it] }

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
                    if (scoringState.history.isNotEmpty()) {
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
                        progress = {
                            if (scoringState.differingTotal > 0) scoringState.differingResolved.toFloat() / scoringState.differingTotal
                            else 1f
                        },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            "${scoringState.differingResolved} / ${scoringState.differingTotal} regions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lastCascadeCount > 0) {
                            Text(
                                "  +$lastCascadeCount matched",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (scoringState.isComplete) {
                        // Done
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("Done · $scoreDisplay", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Verdict buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val before = scoringState.differingResolved
                                    onVerdict(RegionVerdict.DIFFERENT)
                                    lastCascadeCount = (scoringState.differingResolved - before - 1).coerceAtLeast(0)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = PerfettoColors.NEGATIVE_COLOR.copy(alpha = 0.15f),
                                    contentColor = PerfettoColors.NEGATIVE_COLOR
                                ),
                                modifier = Modifier.weight(1f)
                            ) { Text("different", fontWeight = FontWeight.SemiBold) }

                            Spacer(Modifier.width(12.dp))

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val before = scoringState.differingResolved
                                    onVerdict(RegionVerdict.SAME)
                                    lastCascadeCount = (scoringState.differingResolved - before - 1).coerceAtLeast(0)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = PerfettoColors.POSITIVE_COLOR.copy(alpha = 0.15f),
                                    contentColor = PerfettoColors.POSITIVE_COLOR
                                ),
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
            // Trace names header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("anchor: $anchorName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("target: $targetName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }

            if (scoringState.isComplete) {
                // Complete state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            scoreDisplay,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("similarity score", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (region != null) {
                // Current region card
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Timeline snippets — anchor vs target side by side
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        RegionBar(
                            stateColor = PerfettoColors.stateColor(region.anchorState, region.anchorIoWait, isDark),
                            nameColor = if (region.anchorName != null) PerfettoColors.nameColor(region.anchorName) else PerfettoColors.nameRowFallback(isDark),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        RegionBar(
                            stateColor = PerfettoColors.stateColor(region.targetState, region.targetIoWait, isDark),
                            nameColor = if (region.targetName != null) PerfettoColors.nameColor(region.targetName) else PerfettoColors.nameRowFallback(isDark),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                        )
                    }

                    // Duration
                    Text(
                        "${(region.duration * 100).toInt()}% of trace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // What differs
                    val diffLabel = region.diffs.joinToString(" + ") { it.field.replace("_", " ") } + if (region.diffs.size == 1) " differs" else " differ"
                    Text(diffLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Field values
                    for (diff in region.diffs) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(52.dp))
                            if (diff.field == "state") {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(diff.anchorVal, null, isDark)))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                diff.anchorVal ?: "null",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { copyName(diff.anchorVal) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                            if (diff.field == "state") {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(PerfettoColors.stateColor(diff.targetVal, null, isDark)))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                diff.targetVal ?: "null",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { copyName(diff.targetVal) }
                            )
                        }
                        if (diff != region.diffs.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }

                }
            }
        }
    }
}

/** Single-region timeline bar: state row (top) + name row (bottom), full width. */
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
