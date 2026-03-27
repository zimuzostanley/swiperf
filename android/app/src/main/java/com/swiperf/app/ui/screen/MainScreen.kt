package com.swiperf.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.*
import com.swiperf.app.ui.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    clusters: List<Cluster>,
    activeCluster: Cluster?,
    filteredTraces: List<TraceState>,
    onSwitchCluster: (String) -> Unit,
    onRemoveCluster: (String) -> Unit,
    onSetOverviewFilter: (OverviewFilter) -> Unit,
    onSetVerdict: (String, Verdict) -> Unit,
    onSliderChange: (TraceState, Int) -> Unit,
    onGlobalSliderChange: (Int) -> Unit,
    onToggleSort: () -> Unit,
    onStartCompare: () -> Unit,
    onSaveSession: () -> Unit,
    onOpenDrawer: () -> Unit,
    onExportTsv: (String) -> String,
    onExportJson: (String) -> String,
    onCopyToNewTab: () -> Unit,
    importMsg: Pair<String, Boolean>?,
    onClearImportMsg: () -> Unit
) {
    val cl = activeCluster ?: return
    val context = LocalContext.current

    var showBreakdown by remember { mutableStateOf<TraceState?>(null) }
    var showSliceDetail by remember { mutableStateOf<Pair<MergedSlice, Long>?>(null) }
    var showExport by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri ->
        // Will be handled via callback
    }

    // Save file helper
    var pendingSaveContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    val fileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingSaveContent != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(pendingSaveContent!!.first.toByteArray())
            }
            pendingSaveContent = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(cl.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${cl.traces.size} traces \u00b7 ${cl.counts.positive} pos \u00b7 ${cl.counts.negative} neg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleSort) {
                        Icon(
                            if (cl.sortField == SortField.STARTUP_DUR)
                                (if (cl.sortDir == 1) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward)
                            else Icons.Default.Sort,
                            "Sort"
                        )
                    }
                    IconButton(onClick = onStartCompare, enabled = cl.traces.size >= 2) {
                        Icon(Icons.Default.Compare, "Compare")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Share, "Export")
                    }
                    IconButton(onClick = onSaveSession) {
                        Icon(Icons.Default.Save, "Save session")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Import message
            AnimatedVisibility(visible = importMsg != null) {
                importMsg?.let { (msg, ok) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (ok) "\u2713 " else "\u2717 ") + msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearImportMsg, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Cluster tabs (if multiple)
            if (clusters.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = clusters.indexOfFirst { it.id == cl.id }.coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 12.dp
                ) {
                    clusters.forEach { c ->
                        Tab(
                            selected = c.id == cl.id,
                            onClick = { onSwitchCluster(c.id) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${c.name} (${c.traces.size})")
                                    if (clusters.size > 1) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onRemoveCluster(c.id) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Close", Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Filter tabs
            FilterTabRow(
                cluster = cl,
                activeFilter = cl.overviewFilter,
                onSelect = onSetOverviewFilter
            )

            // Global slider
            CompressionSlider(
                label = "All",
                value = cl.globalSlider.toFloat(),
                valueLabel = "${cl.globalSlider}%",
                range = 1f..100f,
                onValueChange = { onGlobalSliderChange(it.toInt()) }
            )

            // Trace list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                val indexMap = mutableMapOf<String, Int>()
                cl.traces.forEachIndexed { i, ts -> indexMap[ts.key] = i }

                itemsIndexed(
                    items = filteredTraces,
                    key = { _, ts -> ts.key }
                ) { _, ts ->
                    ts.ensureCache()
                    TraceCard(
                        traceState = ts,
                        index = indexMap[ts.key] ?: 0,
                        verdict = cl.verdicts[ts.key],
                        onVerdictChange = { v -> onSetVerdict(ts.key, v) },
                        onCardClick = { showBreakdown = ts },
                        onSliderChange = { v -> onSliderChange(ts, v) },
                        onSliceTap = { slice -> showSliceDetail = slice to ts.totalDur }
                    )
                }

                if (filteredTraces.isEmpty()) {
                    item {
                        Text(
                            "No traces match this filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }

    // Breakdown sheet
    showBreakdown?.let { ts ->
        BreakdownSheet(traceState = ts, onDismiss = { showBreakdown = null })
    }

    // Slice detail sheet
    showSliceDetail?.let { (slice, totalDur) ->
        SliceDetailSheet(slice = slice, totalDur = totalDur, onDismiss = { showSliceDetail = null })
    }

    // Export sheet
    if (showExport) {
        ExportSheet(
            hasClusters = clusters.isNotEmpty(),
            clusterCount = clusters.size,
            onExportTsv = onExportTsv,
            onExportJson = onExportJson,
            onSaveFile = { content, filename ->
                pendingSaveContent = content to filename
                val mime = if (filename.endsWith(".json")) "application/json" else "text/tab-separated-values"
                fileCreator.launch(filename)
            },
            onDismiss = { showExport = false }
        )
    }
}
