package com.swiperf.app.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.OverviewFilter

private val FILTERS = listOf(
    OverviewFilter.ALL to "All",
    OverviewFilter.POSITIVE to "Positive",
    OverviewFilter.NEGATIVE to "Negative",
    OverviewFilter.PENDING to "Pending",
    OverviewFilter.DISCARDED to "Discarded",
)

@Composable
fun FilterTabRow(
    cluster: Cluster,
    activeFilter: OverviewFilter,
    onSelect: (OverviewFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollableTabRow(
        selectedTabIndex = FILTERS.indexOfFirst { it.first == activeFilter }.coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 12.dp,
        modifier = modifier
    ) {
        for ((filter, label) in FILTERS) {
            val count = when (filter) {
                OverviewFilter.ALL -> cluster.traces.size
                OverviewFilter.POSITIVE -> cluster.counts.positive
                OverviewFilter.NEGATIVE -> cluster.counts.negative
                OverviewFilter.PENDING -> cluster.counts.pending
                OverviewFilter.DISCARDED -> cluster.counts.discarded
            }
            Tab(
                selected = activeFilter == filter,
                onClick = { onSelect(filter) }
            ) {
                Text(
                    "$label $count",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }
        }
    }
}
