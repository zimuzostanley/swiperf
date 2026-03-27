package com.swiperf.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val breakdown = buildBreakdownData(traceState.currentSeq, traceState.totalDur, isDark)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val onRowTap: (SummaryRow) -> Unit = { r ->
        scope.launch { snackbar.showSnackbar("${r.label}: ${Format.fmtDur(r.dur)} (${Format.fmtPct(r.dur, traceState.totalDur)})", duration = SnackbarDuration.Short) }
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
                "${traceState.trace.packageName} — Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Meta info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetaChip("UUID", traceState.trace.traceUuid.take(8) + "...")
                if (traceState.trace.startupDur > 0) {
                    MetaChip("Startup", Format.fmtDur(traceState.trace.startupDur))
                }
                MetaChip("Slices", "${traceState.origN}")
                MetaChip("Total", Format.fmtDur(traceState.totalDur))
            }

            // Timeline
            MiniTimeline(traceState = traceState)

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
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                            Text("$v", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliceDetailSheet(
    slice: MergedSlice,
    totalDur: Long,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                slice.name ?: "unnamed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            SliceDetailRow("State", PerfettoColors.stateLabel(slice.state, slice.ioWait),
                PerfettoColors.stateColor(slice.state, slice.ioWait, isDark))
            SliceDetailRow("Duration", Format.fmtDur(slice.dur))
            SliceDetailRow("Percentage", Format.fmtPct(slice.dur, totalDur))
            SliceDetailRow("Start", "+" + Format.fmtDur(slice.tsRel))
            SliceDetailRow("IO Wait", if (slice.ioWait != null) "${slice.ioWait}" else "\u2014")
            SliceDetailRow("Blocked", slice.blockedFunction ?: "\u2014")
            SliceDetailRow("Depth", if (slice.depth != null) "${slice.depth}" else "\u2014")
            SliceDetailRow("Merged", "\u00d7${slice.merged}")
        }
    }
}

@Composable
private fun SliceDetailRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
