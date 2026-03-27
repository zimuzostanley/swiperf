package com.swiperf.app.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cl.name, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${cl.traces.size} traces",
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
                    IconButton(onClick = onStartCompare, enabled = cl.traces.size >= 2) {
                        Icon(Icons.Default.Compare, "Compare")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Share, "Export")
                    }
                    IconButton(onClick = onSaveSession) {
                        Icon(Icons.Default.Save, "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort
                    TextButton(onClick = onToggleSort) {
                        Icon(
                            if (cl.sortField == SortField.STARTUP_DUR)
                                (if (cl.sortDir == 1) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward)
                            else Icons.AutoMirrored.Filled.Sort,
                            null,
                            Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (cl.sortField == SortField.STARTUP_DUR) "Startup" else "Sort",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Counts
                    Text(
                        "${cl.counts.positive}+ ${cl.counts.negative}\u2212",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Error banner
            AnimatedVisibility(
                visible = importMsg != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                importMsg?.let { (msg, ok) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (ok) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearImportMsg, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Cluster tabs
            if (clusters.size > 1) {
                TabRow(
                    selectedTabIndex = clusters.indexOfFirst { it.id == cl.id }.coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    clusters.forEach { c ->
                        Tab(
                            selected = c.id == cl.id,
                            onClick = { onSwitchCluster(c.id) }
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("${c.name} (${c.traces.size})")
                                IconButton(
                                    onClick = { onRemoveCluster(c.id) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, Modifier.size(12.dp))
                                }
                            }
                        }
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
            val indexMap = remember(cl.traces) {
                val m = mutableMapOf<String, Int>()
                cl.traces.forEachIndexed { i, ts -> m[ts.key] = i }
                m
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No traces match this filter",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Sheets
    showBreakdown?.let { ts ->
        BreakdownSheet(traceState = ts, onDismiss = { showBreakdown = null })
    }
    showSliceDetail?.let { (slice, totalDur) ->
        SliceDetailSheet(slice = slice, totalDur = totalDur, onDismiss = { showSliceDetail = null })
    }
    if (showExport) {
        ExportSheet(
            hasClusters = clusters.isNotEmpty(),
            clusterCount = clusters.size,
            onExportTsv = onExportTsv,
            onExportJson = onExportJson,
            onSaveFile = { content, filename ->
                pendingSaveContent = content to filename
                fileCreator.launch(filename)
            },
            onDismiss = { showExport = false }
        )
    }
}
