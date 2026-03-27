package com.swiperf.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.compare.CrossCompare
import com.swiperf.app.data.compare.CrossCompareState
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.TraceState
import com.swiperf.app.ui.component.MiniTimeline
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareReviewScreen(
    cluster: Cluster,
    ccState: CrossCompareState,
    anchorKey: String?,
    onApply: (positiveIdx: Int, negativeIdx: Int) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val results = CrossCompare.getResults(ccState)

    // Determine positive/negative groups
    val positiveIdx: Int
    val negativeGroup: List<String>
    val positiveGroup: List<String>
    val negIdx: Int

    if (anchorKey != null) {
        val ai = results.groups.indexOfFirst { it.contains(anchorKey) }
        positiveIdx = if (ai >= 0) ai else 0
        positiveGroup = if (positiveIdx < results.groups.size) results.groups[positiveIdx] else emptyList()
        negativeGroup = results.groups.flatMapIndexed { i, g -> if (i == positiveIdx) emptyList() else g }
        negIdx = -1
    } else {
        positiveIdx = 0
        negIdx = if (results.groups.size > 1) 1 else -1
        positiveGroup = if (positiveIdx < results.groups.size) results.groups[positiveIdx] else emptyList()
        negativeGroup = if (negIdx >= 0 && negIdx < results.groups.size) results.groups[negIdx] else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Results", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                        Text("Reset")
                    }
                    Button(
                        onClick = { onApply(positiveIdx, negIdx) },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfettoColors.POSITIVE_COLOR)
                    ) {
                        Text("Apply", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Negative column
            Column(
                modifier = Modifier.weight(1f).padding(4.dp)
            ) {
                Text(
                    "Negative (${negativeGroup.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PerfettoColors.NEGATIVE_COLOR,
                    modifier = Modifier.padding(8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(negativeGroup) { key ->
                        CompactTraceRow(cluster, key)
                    }
                }
            }

            // Divider
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp))

            // Positive column
            Column(
                modifier = Modifier.weight(1f).padding(4.dp)
            ) {
                Text(
                    "Positive (${positiveGroup.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PerfettoColors.POSITIVE_COLOR,
                    modifier = Modifier.padding(8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(positiveGroup) { key ->
                        CompactTraceRow(cluster, key)
                    }
                }
            }
        }

        // Discarded count
        if (results.discarded.isNotEmpty()) {
            Text(
                "${results.discarded.size} discarded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun CompactTraceRow(cluster: Cluster, traceKey: String) {
    val ts = cluster.traces.find { it.key == traceKey } ?: return
    ts.ensureCache()
    val idx = cluster.traces.indexOf(ts)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(ts.trace.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (ts.trace.startupDur > 0) {
                    Text(Format.fmtDur(ts.trace.startupDur), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            MiniTimeline(traceState = ts, modifier = Modifier.height(20.dp).clip(RoundedCornerShape(2.dp)))
        }
    }
}
