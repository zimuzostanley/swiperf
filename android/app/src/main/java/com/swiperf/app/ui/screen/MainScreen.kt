package com.swiperf.app.ui.screen

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.model.*
import com.swiperf.app.data.session.SessionMeta
import com.swiperf.app.ui.component.*
import com.swiperf.app.ui.theme.PerfettoColors
import com.swiperf.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    clusters: List<Cluster>,
    activeCluster: Cluster?,
    filteredTraces: List<TraceState>,
    stateVersion: Long,
    themeMode: ThemeMode,
    sessions: List<SessionMeta>,
    currentSessionId: String?,
    loading: Boolean,
    loadProgress: String?,
    importMsg: Pair<String, Boolean>?,
    onSwitchCluster: (String) -> Unit,
    onRemoveCluster: (String) -> Unit,
    onRenameCluster: (String, String) -> Unit,
    onSetOverviewFilter: (OverviewFilter) -> Unit,
    onSetVerdict: (String, Verdict) -> Unit,
    onSliderChange: (TraceState, Int) -> Unit,
    onGlobalSliderChange: (Int) -> Unit,
    onSetSortField: (SortField) -> Unit,
    pinnedKey: String?,
    onTogglePin: (String) -> Unit,
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
    onRefreshSessions: () -> Unit,
    onSyncRemote: (() -> Unit)? = null,
    scores: Map<String, Float> = emptyMap(),
    onStartScoring: ((String) -> Unit)? = null,
    scoringDict: com.swiperf.app.data.scoring.ScoringDictionary = com.swiperf.app.data.scoring.ScoringDictionary(),
    scoringUseDict: Boolean = true,
    scoringNormalizeDigits: Boolean = false,
    onToggleUseDict: (() -> Unit)? = null,
    onToggleNormalizeDigits: (() -> Unit)? = null,
    onRemoveDictEntries: ((List<com.swiperf.app.data.scoring.DictEntry>) -> Unit)? = null,
    onClearDict: (() -> Unit)? = null,
    onImportDict: ((json: String, merge: Boolean) -> Unit)? = null,
    autoPinFirst: Boolean = true,
    onToggleAutoPinFirst: (() -> Unit)? = null,
    tapToScore: Boolean = false,
    onToggleTapToScore: (() -> Unit)? = null,
    onStartGlobalScoring: (() -> Unit)? = null
) {
    val cl = activeCluster
    val hasData = cl != null && cl.traces.isNotEmpty()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var renameClusterId by remember { mutableStateOf<String?>(null) }
    var longPressClusterId by remember { mutableStateOf<String?>(null) }
    var showBreakdown by remember { mutableStateOf<Pair<TraceState, Int>?>(null) }
    // Slice detail: slice, totalDur, full seq, index in seq, highlight callback
    data class SliceDetailState(val slice: MergedSlice, val totalDur: Long, val seq: List<MergedSlice>, val index: Int, val onHighlightChange: (Int?) -> Unit)
    var showSliceDetail by remember { mutableStateOf<SliceDetailState?>(null) }
    var pendingSaveContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportFile(uri)
    }
    val fileCreator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && pendingSaveContent != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pendingSaveContent!!.first.toByteArray()) }
            pendingSaveContent = null
        }
    }

    // Show snackbar for import messages
    LaunchedEffect(importMsg) {
        importMsg?.let { (msg, _) ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            onClearImportMsg()
        }
    }

    // Read mutable cluster values keyed on stateVersion
    val overviewFilter = remember(stateVersion) { cl?.overviewFilter ?: OverviewFilter.ALL }
    val globalSlider = remember(stateVersion) { cl?.globalSlider ?: 100 }
    val sortField = remember(stateVersion) { cl?.sortField ?: SortField.INDEX }
    val sortDir = remember(stateVersion) { cl?.sortDir ?: 1 }
    val counts = remember(stateVersion) { cl?.counts ?: VerdictCounts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SwiPerf",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onDoubleClick = { showPaste = true }
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                        Icon(Icons.Default.Add, "Import")
                    }
                    if (hasData && cl != null) {
                        if (pinnedKey != null && cl.traces.size > 1 && onStartGlobalScoring != null) {
                            IconButton(onClick = { onStartGlobalScoring() }) {
                                Icon(Icons.Default.CompareArrows, "Compare All")
                            }
                        }
                        IconButton(onClick = { showExport = true }) {
                            Icon(Icons.Default.Share, "Export")
                        }
                    }
                    IconButton(onClick = { onRefreshSessions(); showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (hasData) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sort — tap toggles direction, opens dialog
                        val sortLabel = when (sortField) {
                            SortField.STARTUP_DUR -> "startup"
                            SortField.COSINE_SIMILARITY -> "cosine"
                            SortField.MANUAL_SCORE -> "score"
                            SortField.INDEX -> "sort"
                        }
                        TextButton(onClick = { showSortDialog = true }) {
                            Icon(
                                if (sortField != SortField.INDEX)
                                    (if (sortDir == 1) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward)
                                else Icons.AutoMirrored.Filled.Sort,
                                null, Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(sortLabel, style = MaterialTheme.typography.labelLarge)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
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

            if (!hasData && loading) {
                // ── Restoring state ──
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (!hasData) {
                // ── Empty state ──
                EmptyImportArea(loading = loading, onOpenFile = { filePicker.launch(arrayOf("application/json", "text/*", "*/*")) }, onPasteText = onPasteText)
            } else {
                val c = cl ?: return@Column // Safe: hasData guarantees cl != null
                // ── Cluster tabs ──
                if (clusters.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = clusters.indexOfFirst { it.id == c.id }.coerceAtLeast(0),
                        containerColor = MaterialTheme.colorScheme.background,
                        edgePadding = 12.dp
                    ) {
                        clusters.forEach { tab ->
                            Tab(
                                selected = tab.id == c.id,
                                onClick = { onSwitchCluster(tab.id) }
                            ) {
                                Text(
                                    tab.name,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .combinedClickable(
                                            onClick = { onSwitchCluster(tab.id) },
                                            onDoubleClick = { renameClusterId = tab.id },
                                            onLongClick = { longPressClusterId = tab.id }
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Filter tabs
                FilterTabRow(cluster = c, activeFilter = overviewFilter, onSelect = onSetOverviewFilter)

                // Global slider
                CompressionSlider(
                    label = "All",
                    value = globalSlider.toFloat(),
                    valueLabel = "$globalSlider%",
                    range = 1f..100f,
                    onValueChange = { onGlobalSliderChange(it.toInt()) }
                )

                // Trace list
                val indexMap = remember(c.traces) {
                    val m = mutableMapOf<String, Int>()
                    c.traces.forEachIndexed { i, ts -> m[ts.key] = i }
                    m
                }

                // Pinned trace (rendered above the scrollable list)
                val pinnedTrace = if (pinnedKey != null) c.traces.find { it.key == pinnedKey } else null
                val unpinnedTraces = filteredTraces.filter { it.key != pinnedKey }

                if (pinnedTrace != null) {
                    pinnedTrace.ensureCache()
                    val pVerdict = remember(stateVersion) { c.verdicts[pinnedTrace.key] }
                    val pSeq = remember(stateVersion) { pinnedTrace.currentSeq }
                    TraceCard(
                        packageName = pinnedTrace.trace.packageName,
                        startupDur = pinnedTrace.trace.startupDur,
                        cosineSimilarity = cosineTag(pinnedTrace.trace.extra),
                        score = remember(stateVersion) { scores[pinnedTrace.key] },
                        index = indexMap[pinnedTrace.key] ?: 0,
                        verdict = pVerdict,
                        seq = pSeq,
                        totalDur = pinnedTrace.totalDur,
                        onVerdictChange = { v -> onSetVerdict(pinnedTrace.key, v) },
                        onCardClick = { showBreakdown = pinnedTrace to (indexMap[pinnedTrace.key] ?: 0) },
                        onSliceTap = { idx, slice, s, td, onHl ->
                            showSliceDetail = SliceDetailState(slice, td, s, idx, onHl)
                        },
                        isPinned = true,
                        onTogglePin = { onTogglePin(pinnedTrace.key) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(
                        count = unpinnedTraces.size,
                        key = { unpinnedTraces[it].key },
                        contentType = { "trace" }
                    ) { i ->
                        val ts = unpinnedTraces[i]
                        ts.ensureCache()
                        val v = remember(stateVersion) { c.verdicts[ts.key] }
                        val s = remember(stateVersion) { ts.currentSeq }
                        TraceCard(
                            packageName = ts.trace.packageName,
                            startupDur = ts.trace.startupDur,
                            cosineSimilarity = cosineTag(ts.trace.extra),
                            score = remember(stateVersion) { scores[ts.key] },
                            index = indexMap[ts.key] ?: 0,
                            verdict = v,
                            seq = s,
                            totalDur = ts.totalDur,
                            onVerdictChange = { vd -> onSetVerdict(ts.key, vd) },
                            onCardClick = { showBreakdown = ts to (indexMap[ts.key] ?: 0) },
                            onSliceTap = { idx, slice, s, td, onHl ->
                                showSliceDetail = SliceDetailState(slice, td, s, idx, onHl)
                            },
                            isPinned = false,
                            onTogglePin = { onTogglePin(ts.key) },
                            onStartScoring = if (pinnedTrace != null && ts.key != pinnedKey) {
                                { onStartScoring?.invoke(ts.key) }
                            } else null
                        )
                    }
                    if (unpinnedTraces.isEmpty() && pinnedTrace == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("No traces match this filter", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Sort dialog ──
    if (showSortDialog) {
        val options = listOf(
            SortField.INDEX to "Default order",
            SortField.STARTUP_DUR to "Startup duration",
            SortField.COSINE_SIMILARITY to "Cosine similarity",
            SortField.MANUAL_SCORE to "Manual score",
        )
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    for ((field, label) in options) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetSortField(field); showSortDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sortField == field, onClick = { onSetSortField(field); showSortDialog = false })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Close") } }
        )
    }

    // ── Long-press cluster: remove ──
    longPressClusterId?.let { id ->
        val name = clusters.find { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { longPressClusterId = null },
            title = { Text("Remove \u201c$name\u201d?") },
            text = { Text("This will close this tab. Data is not deleted from saved sessions.") },
            confirmButton = {
                Button(
                    onClick = { onRemoveCluster(id); longPressClusterId = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { longPressClusterId = null }) { Text("Cancel") }
            }
        )
    }

    // ── Rename cluster dialog ──
    renameClusterId?.let { id ->
        val current = clusters.find { it.id == id }?.name ?: ""
        var name by remember(id) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renameClusterId = null },
            title = { Text("Rename tab") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Tab name") },
                    shape = RoundedCornerShape(4.dp)
                )
            },
            confirmButton = {
                Button(onClick = { onRenameCluster(id, name); renameClusterId = null }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameClusterId = null }) { Text("Cancel") }
            }
        )
    }

    // ── Sheets ──
    if (showPaste) {
        PasteSheet(onPaste = { text -> onPasteText(text); showPaste = false }, onDismiss = { showPaste = false })
    }
    showBreakdown?.let { (ts, idx) ->
        BreakdownSheet(
            traceState = ts,
            index = idx,
            onSliderChange = { v -> onSliderChange(ts, v) },
            onDismiss = { showBreakdown = null }
        )
    }
    showSliceDetail?.let { state ->
        SliceDetailSheet(
            slice = state.slice,
            totalDur = state.totalDur,
            seq = state.seq,
            initialIndex = state.index,
            onIndexChange = { newIdx -> state.onHighlightChange(newIdx) },
            onDismiss = {
                state.onHighlightChange(null) // Clear highlight
                showSliceDetail = null
            }
        )
    }
    if (showExport) {
        ExportSheet(hasClusters = clusters.isNotEmpty(), clusterCount = clusters.size, onExportTsv = onExportTsv, onExportJson = onExportJson,
            onSaveFile = { content, filename -> pendingSaveContent = content to filename; fileCreator.launch(filename) },
            onDismiss = { showExport = false })
    }
    if (showSettings) {
        SettingsSheet(themeMode = themeMode, sessions = sessions, currentSessionId = currentSessionId, hasData = hasData, clusterName = cl?.name ?: "Session",
            onSetTheme = onSetTheme, onSaveSession = { name -> onSaveSession(name); showSettings = false },
            onLoadSession = { id -> onLoadSession(id); showSettings = false }, onDeleteSession = onDeleteSession,
            onDeleteAllData = { onDeleteAllData(); showSettings = false },
            onExportSession = { id ->
                onExportSessionJson(id) { json ->
                    if (json != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, json) }
                        context.startActivity(Intent.createChooser(intent, "Export session"))
                    }
                }
            },
            onSyncRemote = onSyncRemote,
            scoringDict = scoringDict,
            scoringUseDict = scoringUseDict,
            scoringNormalizeDigits = scoringNormalizeDigits,
            onToggleUseDict = onToggleUseDict,
            onToggleNormalizeDigits = onToggleNormalizeDigits,
            onRemoveDictEntries = onRemoveDictEntries,
            onClearDict = onClearDict,
            onImportDict = onImportDict,
            autoPinFirst = autoPinFirst,
            onToggleAutoPinFirst = onToggleAutoPinFirst,
            tapToScore = tapToScore,
            onToggleTapToScore = onToggleTapToScore,
            onDismiss = { showSettings = false })
    }
}

@Composable
private fun EmptyImportArea(loading: Boolean, onOpenFile: () -> Unit, onPasteText: (String) -> Unit) {
    var pasteText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Default.Timeline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text("No traces loaded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Open a JSON/TSV/CSV file or paste data below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp))

        Button(onClick = onOpenFile, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open File")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pasteText,
            onValueChange = { pasteText = it },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("Paste JSON / TSV / CSV\u2026", style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.labelSmall,
            enabled = !loading,
            singleLine = false,
            shape = RoundedCornerShape(4.dp)
        )

        if (pasteText.trim().isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onPasteText(pasteText); pasteText = "" }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("Import Pasted Data")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Supports [{ts, dur, state}] slices, {trace_uuid, slices} traces, TSV/CSV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteSheet(onPaste: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Paste Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("JSON, TSV, or CSV with trace data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text("Paste JSON / TSV / CSV\u2026", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.labelSmall,
                singleLine = false,
                shape = RoundedCornerShape(4.dp)
            )

            Button(
                onClick = { if (text.trim().isNotEmpty()) onPaste(text) },
                enabled = text.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import")
            }
        }
    }
}

private fun cosineTag(extra: Map<String, Any?>?): String? {
    val raw = extra?.get("cosine_similarity") ?: extra?.get("Cosine Similarity") ?: return null
    val n = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull() ?: return null
        else -> return null
    }
    return "%.2f".format(n)
}
