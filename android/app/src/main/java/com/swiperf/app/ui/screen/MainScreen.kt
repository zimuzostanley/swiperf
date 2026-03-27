package com.swiperf.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.*
import com.swiperf.app.data.session.SessionMeta
import com.swiperf.app.ui.component.*
import com.swiperf.app.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    clusters: List<Cluster>,
    activeCluster: Cluster?,
    filteredTraces: List<TraceState>,
    themeMode: ThemeMode,
    sessions: List<SessionMeta>,
    currentSessionId: String?,
    loading: Boolean,
    loadProgress: String?,
    importMsg: Pair<String, Boolean>?,
    onSwitchCluster: (String) -> Unit,
    onRemoveCluster: (String) -> Unit,
    onSetOverviewFilter: (OverviewFilter) -> Unit,
    onSetVerdict: (String, Verdict) -> Unit,
    onSliderChange: (TraceState, Int) -> Unit,
    onGlobalSliderChange: (Int) -> Unit,
    onToggleSort: () -> Unit,
    onStartCompare: () -> Unit,
    onSaveSession: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onPasteText: (String) -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllData: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onExportTsv: (String) -> String,
    onExportJson: (String) -> String,
    onExportSessionJson: (String, (String?) -> Unit) -> Unit,
    onCopyToNewTab: () -> Unit,
    onClearImportMsg: () -> Unit,
    onRefreshSessions: () -> Unit
) {
    val cl = activeCluster
    val hasData = cl != null && cl.traces.isNotEmpty()
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf<TraceState?>(null) }
    var showSliceDetail by remember { mutableStateOf<Pair<MergedSlice, Long>?>(null) }
    var pendingSaveContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportFile(uri) }

    val fileCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingSaveContent != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pendingSaveContent!!.first.toByteArray()) }
            pendingSaveContent = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SwiPerf", fontWeight = FontWeight.SemiBold)
                        if (hasData) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${cl!!.counts.positive}+ ${cl.counts.negative}\u2212",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Import
                    IconButton(onClick = { filePicker.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                        Icon(Icons.Default.Add, "Import file")
                    }
                    if (hasData) {
                        // Compare
                        IconButton(onClick = onStartCompare, enabled = cl!!.traces.size >= 2) {
                            Icon(Icons.Default.Compare, "Compare")
                        }
                        // Export
                        IconButton(onClick = { showExport = true }) {
                            Icon(Icons.Default.Share, "Export")
                        }
                    }
                    // Settings
                    IconButton(onClick = { onRefreshSessions(); showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (hasData) {
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
                                if (cl!!.sortField == SortField.STARTUP_DUR)
                                    (if (cl.sortDir == 1) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward)
                                else Icons.AutoMirrored.Filled.Sort,
                                null
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (cl.sortField == SortField.STARTUP_DUR) "Startup" else "Sort",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Counts — displayed prominently
                        Text(
                            "${cl!!.traces.size} traces",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${cl.counts.positive}",
                            style = MaterialTheme.typography.labelLarge,
                            color = com.swiperf.app.ui.theme.PerfettoColors.POSITIVE_COLOR
                        )
                        Text(
                            "+",
                            style = MaterialTheme.typography.labelSmall,
                            color = com.swiperf.app.ui.theme.PerfettoColors.POSITIVE_COLOR
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${cl.counts.negative}",
                            style = MaterialTheme.typography.labelLarge,
                            color = com.swiperf.app.ui.theme.PerfettoColors.NEGATIVE_COLOR
                        )
                        Text(
                            "\u2212",
                            style = MaterialTheme.typography.labelSmall,
                            color = com.swiperf.app.ui.theme.PerfettoColors.NEGATIVE_COLOR
                        )
                        Spacer(Modifier.width(4.dp))
                    }
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
            // Error/success banner
            AnimatedVisibility(
                visible = importMsg != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                importMsg?.let { (msg, ok) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearImportMsg, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Loading
            AnimatedVisibility(visible = loading) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (loadProgress != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(loadProgress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!hasData) {
                // ── Empty state: inline import area ──
                EmptyImportArea(
                    loading = loading,
                    onOpenFile = { filePicker.launch(arrayOf("application/json", "text/*", "*/*")) },
                    onPasteText = onPasteText
                )
            } else {
                // ── Data loaded: trace list ──

                // Cluster tabs (only if multiple)
                if (clusters.size > 1) {
                    TabRow(
                        selectedTabIndex = clusters.indexOfFirst { it.id == cl!!.id }.coerceAtLeast(0),
                        containerColor = MaterialTheme.colorScheme.background
                    ) {
                        clusters.forEach { c ->
                            Tab(
                                selected = c.id == cl!!.id,
                                onClick = { onSwitchCluster(c.id) }
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "${c.name} (${c.traces.size})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter tabs
                FilterTabRow(
                    cluster = cl!!,
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
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No traces match this filter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Sheets ──

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

    // Settings ModalBottomSheet (ProcState pattern)
    if (showSettings) {
        SettingsSheet(
            themeMode = themeMode,
            sessions = sessions,
            currentSessionId = currentSessionId,
            hasData = hasData,
            clusterName = cl?.name ?: "Session",
            onSetTheme = onSetTheme,
            onSaveSession = { name -> onSaveSession(name); showSettings = false },
            onLoadSession = { id -> onLoadSession(id); showSettings = false },
            onDeleteSession = onDeleteSession,
            onDeleteAllData = { onDeleteAllData(); showSettings = false },
            onExportSession = { id ->
                onExportSessionJson(id) { json ->
                    if (json != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export session"))
                    }
                }
            },
            onDismiss = { showSettings = false }
        )
    }
}

// ── Empty state with inline import ──
@Composable
private fun EmptyImportArea(
    loading: Boolean,
    onOpenFile: () -> Unit,
    onPasteText: (String) -> Unit
) {
    var pasteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            Icons.Default.Timeline,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(12.dp))
        Text("No traces loaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Import a JSON/TSV/CSV file or paste data below",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onOpenFile,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open File")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pasteText,
            onValueChange = { pasteText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = {
                Text(
                    "Paste JSON / TSV / CSV\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = MaterialTheme.typography.labelSmall,
            enabled = !loading,
            singleLine = false,
            shape = RoundedCornerShape(4.dp)
        )

        if (pasteText.trim().isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onPasteText(pasteText); pasteText = "" },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import Pasted Data")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Supports [{ts, dur, state}] slices, {trace_uuid, slices} traces, TSV/CSV",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
