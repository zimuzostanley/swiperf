package com.swiperf.app.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.Cluster
import com.swiperf.app.data.model.OverviewFilter
import com.swiperf.app.ui.theme.PerfettoColors

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
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
            val isActive = activeFilter == info.filter
            FilterChip(
                selected = isActive,
                onClick = { onSelect(info.filter) },
                label = {
                    Text(
                        "${info.label} $count",
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (info.filter) {
                        OverviewFilter.POSITIVE -> PerfettoColors.POSITIVE_COLOR.copy(alpha = 0.15f)
                        OverviewFilter.NEGATIVE -> PerfettoColors.NEGATIVE_COLOR.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    selectedLabelColor = when (info.filter) {
                        OverviewFilter.POSITIVE -> PerfettoColors.POSITIVE_COLOR
                        OverviewFilter.NEGATIVE -> PerfettoColors.NEGATIVE_COLOR
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            )
        }
    }
}
