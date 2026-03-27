package com.swiperf.app.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.OverviewFilter

private data class FilterInfo(val filter: OverviewFilter, val label: String)

private val FILTERS = listOf(
    FilterInfo(OverviewFilter.ALL, "All"),
    FilterInfo(OverviewFilter.POSITIVE, "Positive"),
    FilterInfo(OverviewFilter.NEGATIVE, "Negative"),
    FilterInfo(OverviewFilter.PENDING, "Pending"),
    FilterInfo(OverviewFilter.DISCARDED, "Discarded"),
)

@Composable
fun FilterTabRow(
    cluster: Cluster,
    activeFilter: OverviewFilter,
    onSelect: (OverviewFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (info in FILTERS) {
            val count = when (info.filter) {
                OverviewFilter.ALL -> cluster.traces.size
                OverviewFilter.POSITIVE -> cluster.counts.positive
                OverviewFilter.NEGATIVE -> cluster.counts.negative
                OverviewFilter.PENDING -> cluster.counts.pending
                OverviewFilter.DISCARDED -> cluster.counts.discarded
            }
            FilterChip(
                selected = activeFilter == info.filter,
                onClick = { onSelect(info.filter) },
                label = { Text("${info.label} $count") }
            )
        }
    }
}
