package com.swiperf.app.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.SummaryRow
import com.swiperf.app.data.model.TraceState
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import kotlinx.coroutines.launch
import com.swiperf.app.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownSheet(
    traceState: TraceState,
    index: Int = -1,
    onSliderChange: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Local version to re-read mutable state after slider change
    var localVersion by remember { mutableStateOf(0L) }
    val currentSeq = remember(localVersion) { traceState.currentSeq }
    val sliderValue = remember(localVersion) { traceState.sliderValue }
    val breakdown = remember(localVersion) { buildBreakdownData(currentSeq, traceState.totalDur, isDark) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onRowTap: (SummaryRow) -> Unit = { r ->
        val text = "${r.label}: ${Format.fmtDur(r.dur)} (${Format.fmtPct(r.dur, traceState.totalDur)})"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", text))
        scope.launch { snackbar.showSnackbar("Copied: $text", duration = SnackbarDuration.Short) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                (if (index >= 0) "#${index + 1} " else "") + traceState.trace.packageName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Meta info — each chip copies full value on tap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaChip("UUID", traceState.trace.traceUuid.take(8) + "...") {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", traceState.trace.traceUuid))
                    scope.launch { snackbar.showSnackbar("Copied UUID", duration = SnackbarDuration.Short) }
                }
                if (traceState.trace.startupDur > 0) {
                    MetaChip("Startup", Format.fmtDur(traceState.trace.startupDur)) {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", "${traceState.trace.startupDur}"))
                        scope.launch { snackbar.showSnackbar("Copied startup dur", duration = SnackbarDuration.Short) }
                    }
                }
                MetaChip("Slices", "${traceState.origN}")
                MetaChip("Total", Format.fmtDur(traceState.totalDur))
            }

            // Timeline
            MiniTimeline(seq = currentSeq, totalDur = traceState.totalDur)

            // Per-trace slider
            if (onSliderChange != null && traceState.origN > 2) {
                CompressionSlider(
                    label = "",
                    value = sliderValue.toFloat(),
                    valueLabel = "${currentSeq.size}",
                    range = 2f..traceState.origN.toFloat(),
                    onValueChange = {
                        onSliderChange(it.toInt())
                        localVersion = localVersion + 1
                    },
                    suffix = "/ ${traceState.origN}"
                )
            }

            // Breakdown tables
            BreakdownSection("States", breakdown.states, breakdown.totalDur, onRowTap = onRowTap)

            if (breakdown.names.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                BreakdownSection("Names", breakdown.names, breakdown.totalDur, onRowTap = onRowTap)
            }

            if (breakdown.blockedFunctions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                BreakdownSection("Blocked Functions", breakdown.blockedFunctions, breakdown.totalDur, onRowTap = onRowTap)
            }

            // Extra fields
            traceState.trace.extra?.let { extra ->
                if (extra.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Text("Extra", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    for ((k, v) in extra.toSortedMap()) {
                        val valText = "$v"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val text = "$k: $valText"
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", text))
                                    scope.launch { snackbar.showSnackbar("Copied: $text", duration = SnackbarDuration.Short) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                            Text(valText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun MetaChip(label: String, value: String, onTap: (() -> Unit)? = null) {
    Column(
        modifier = if (onTap != null) Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onTap() }
            .padding(2.dp)
        else Modifier
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliceDetailSheet(
    slice: MergedSlice,
    totalDur: Long,
    seq: List<MergedSlice>? = null,
    initialIndex: Int = -1,
    onIndexChange: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val copyRow: (String, String) -> Unit = { label, value ->
        val text = "$label: $value"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf", text))
        scope.launch { snackbar.showSnackbar("Copied: $text", duration = SnackbarDuration.Short) }
    }

    // Navigate between slices: double-tap = next, triple-tap = previous
    var currentIdx by remember { mutableStateOf(if (initialIndex >= 0) initialIndex else seq?.indexOf(slice) ?: 0) }
    val currentSlice = if (seq != null && currentIdx in seq.indices) seq[currentIdx] else slice
    val canNavigate = seq != null && seq.size > 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header — double-tap next, triple-tap previous
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currentSlice.name ?: "unnamed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canNavigate) {
                    Text(
                        "${currentIdx + 1}/${seq!!.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canNavigate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { currentIdx--; onIndexChange?.invoke(currentIdx) },
                        enabled = currentIdx > 0
                    ) { Text("\u2190 prev", style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { currentIdx++; onIndexChange?.invoke(currentIdx) },
                        enabled = currentIdx < seq!!.size - 1
                    ) { Text("next \u2192", style = MaterialTheme.typography.labelSmall) }
                }
            }

            SliceDetailRow("State", PerfettoColors.stateLabel(currentSlice.state, currentSlice.ioWait),
                PerfettoColors.stateColor(currentSlice.state, currentSlice.ioWait, isDark), onTap = copyRow)
            SliceDetailRow("Duration", Format.fmtDur(currentSlice.dur), onTap = copyRow)
            SliceDetailRow("Percentage", Format.fmtPct(currentSlice.dur, totalDur), onTap = copyRow)
            SliceDetailRow("Start", "+" + Format.fmtDur(currentSlice.tsRel), onTap = copyRow)
            SliceDetailRow("IO Wait", if (currentSlice.ioWait != null) "${currentSlice.ioWait}" else "\u2014", onTap = copyRow)
            SliceDetailRow("Blocked", currentSlice.blockedFunction ?: "\u2014", onTap = copyRow)
            SliceDetailRow("Depth", if (currentSlice.depth != null) "${currentSlice.depth}" else "\u2014", onTap = copyRow)
            SliceDetailRow("Merged", "\u00d7${currentSlice.merged}", onTap = copyRow)
        }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SliceDetailRow(label: String, value: String, color: Color? = null, onTap: ((String, String) -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(if (onTap != null) Modifier.clickable { onTap(label, value) } else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        if (color != null) {
            Box(
                Modifier
                    .size(10.dp)
                    .padding(end = 4.dp)
                    .background(color, shape = CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
