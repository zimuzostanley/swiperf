package com.swiperf.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.OverviewFilter
import com.swiperf.app.ui.theme.PerfettoColors

private data class FilterDef(
    val filter: OverviewFilter,
    val label: String,
    val countColor: @Composable () -> androidx.compose.ui.graphics.Color
)

@Composable
fun FilterTabRow(
    cluster: Cluster,
    activeFilter: OverviewFilter,
    onSelect: (OverviewFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        FilterDef(OverviewFilter.ALL, "all") { MaterialTheme.colorScheme.onSurfaceVariant },
        FilterDef(OverviewFilter.POSITIVE, "+ve") { PerfettoColors.POSITIVE_COLOR },
        FilterDef(OverviewFilter.NEGATIVE, "\u2212ve") { PerfettoColors.NEGATIVE_COLOR },
        FilterDef(OverviewFilter.PENDING, "pending") { MaterialTheme.colorScheme.onSurfaceVariant },
        FilterDef(OverviewFilter.DISCARDED, "skip") { PerfettoColors.DISCARD_COLOR },
    )

    ScrollableTabRow(
        selectedTabIndex = filters.indexOfFirst { it.filter == activeFilter }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 8.dp,
        modifier = modifier
    ) {
        for (def in filters) {
            val count = when (def.filter) {
                OverviewFilter.ALL -> cluster.traces.size
                OverviewFilter.POSITIVE -> cluster.counts.positive
                OverviewFilter.NEGATIVE -> cluster.counts.negative
                OverviewFilter.PENDING -> cluster.counts.pending
                OverviewFilter.DISCARDED -> cluster.counts.discarded
            }
            val selected = activeFilter == def.filter
            Tab(selected = selected, onClick = { onSelect(def.filter) }) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        def.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = def.countColor()
                    )
                }
            }
        }
    }
}
