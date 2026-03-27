package com.swiperf.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.MergedSlice
import com.swiperf.app.data.model.SummaryRow
import com.swiperf.app.ui.theme.LocalIsDarkTheme
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.util.Format

data class BreakdownData(
    val states: List<SummaryRow>,
    val names: List<SummaryRow>,
    val blockedFunctions: List<SummaryRow>,
    val totalDur: Long
)

fun buildBreakdownData(data: List<MergedSlice>, totalDur: Long, isDark: Boolean): BreakdownData {
    val stateMap = mutableMapOf<String, Triple<Long, Int, Color>>()
    val nameMap = mutableMapOf<String, Pair<Long, Int>>()
    val bfMap = mutableMapOf<String, Pair<Long, Int>>()

    for (d in data) {
        val sk = PerfettoColors.stateLabel(d.state, d.ioWait)
        val existing = stateMap[sk]
        val color = PerfettoColors.stateColor(d.state, d.ioWait, isDark)
        stateMap[sk] = Triple(
            (existing?.first ?: 0L) + d.dur,
            (existing?.second ?: 0) + 1,
            color
        )

        if (d.name != null) {
            val ne = nameMap[d.name]
            nameMap[d.name] = Pair((ne?.first ?: 0L) + d.dur, (ne?.second ?: 0) + 1)
        }

        if (d.blockedFunction != null) {
            val be = bfMap[d.blockedFunction]
            bfMap[d.blockedFunction] = Pair((be?.first ?: 0L) + d.dur, (be?.second ?: 0) + 1)
        }
    }

    val maxStateDur = stateMap.values.maxOfOrNull { it.first } ?: 1L
    val maxNameDur = nameMap.values.maxOfOrNull { it.first } ?: 1L
    val maxBfDur = bfMap.values.maxOfOrNull { it.first } ?: 1L

    val states = stateMap.map { (k, v) ->
        SummaryRow(k, k, v.first, v.second, v.third.value.toLong(), if (maxStateDur > 0) v.first.toFloat() / maxStateDur else 0f)
    }
    val names = nameMap.map { (k, v) ->
        val c = PerfettoColors.nameColor(k)
        SummaryRow(k, k, v.first, v.second, c.value.toLong(), if (maxNameDur > 0) v.first.toFloat() / maxNameDur else 0f)
    }
    val bfs = bfMap.map { (k, v) ->
        SummaryRow(k, k, v.first, v.second, Color(0xFFC62828).value.toLong(), if (maxBfDur > 0) v.first.toFloat() / maxBfDur else 0f)
    }

    return BreakdownData(states, names, bfs, totalDur)
}

@Composable
fun BreakdownSection(
    title: String,
    rows: List<SummaryRow>,
    totalDur: Long,
    modifier: Modifier = Modifier,
    onRowTap: ((SummaryRow) -> Unit)? = null
) {
    var sortCol by remember { mutableStateOf("dur") }
    var sortDir by remember { mutableIntStateOf(-1) }

    val sorted = remember(rows, sortCol, sortDir) {
        rows.sortedWith(compareBy<SummaryRow> {
            when (sortCol) {
                "label" -> it.label.lowercase()
                "dur" -> it.dur.toString().padStart(20, '0')
                "pct" -> it.pct.toString().padStart(20, '0')
                "count" -> it.count.toString().padStart(10, '0')
                else -> it.dur.toString().padStart(20, '0')
            }
        }.let { if (sortDir == -1) it.reversed() else it })
    }

    if (rows.isEmpty()) return

    Column(modifier = modifier) {
        // Section header
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Column headers
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortHeader("Name", "label", sortCol, sortDir, Modifier.weight(1f)) { col ->
                if (sortCol == col) sortDir = if (sortDir == -1) 1 else -1
                else { sortCol = col; sortDir = if (col == "label") 1 else -1 }
            }
            SortHeader("Duration", "dur", sortCol, sortDir, Modifier.width(72.dp)) { col ->
                if (sortCol == col) sortDir = if (sortDir == -1) 1 else -1
                else { sortCol = col; sortDir = -1 }
            }
            SortHeader("%", "pct", sortCol, sortDir, Modifier.width(48.dp)) { col ->
                if (sortCol == col) sortDir = if (sortDir == -1) 1 else -1
                else { sortCol = col; sortDir = -1 }
            }
            SortHeader("#", "count", sortCol, sortDir, Modifier.width(32.dp)) { col ->
                if (sortCol == col) sortDir = if (sortDir == -1) 1 else -1
                else { sortCol = col; sortDir = -1 }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Rows
        for (row in sorted) {
            BreakdownRow(row = row, totalDur = totalDur, onTap = onRowTap)
        }
    }
}

@Composable
private fun SortHeader(
    label: String,
    col: String,
    activeCol: String,
    dir: Int,
    modifier: Modifier,
    onClick: (String) -> Unit
) {
    val arrow = if (activeCol == col) (if (dir == -1) " \u2193" else " \u2191") else ""
    Text(
        label + arrow,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (activeCol == col) FontWeight.Bold else FontWeight.Normal,
        color = if (activeCol == col) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.clickable { onClick(col) }
    )
}

@Composable
private fun BreakdownRow(row: SummaryRow, totalDur: Long, onTap: ((SummaryRow) -> Unit)? = null) {
    val color = Color(row.color.toULong())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable { onTap(row) } else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color swatch + name + bar
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    row.short,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Thin bar
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.pct.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color.copy(alpha = 0.7f))
            )
        }

        // Duration
        Text(
            Format.fmtDur(row.dur),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(72.dp)
        )

        // Percentage
        Text(
            Format.fmtPct(row.dur, totalDur),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp)
        )

        // Count
        Text(
            "${row.count}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp)
        )
    }
}
