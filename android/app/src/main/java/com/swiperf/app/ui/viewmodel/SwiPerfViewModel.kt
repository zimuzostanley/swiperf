package com.swiperf.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swiperf.app.data.compare.*
import com.swiperf.app.data.export.ExportHelper
import com.swiperf.app.data.model.*
import com.swiperf.app.data.parse.TraceParser
import com.swiperf.app.data.session.SessionManager
import com.swiperf.app.data.session.SessionMeta
import com.swiperf.app.ui.theme.ThemeMode
import com.swiperf.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SwiPerfViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()

    // ── Core State ──
    private val _clusters = MutableStateFlow<List<Cluster>>(emptyList())
    val clusters: StateFlow<List<Cluster>> = _clusters.asStateFlow()

    private val _activeClusterId = MutableStateFlow<String?>(null)
    val activeClusterId: StateFlow<String?> = _activeClusterId.asStateFlow()

    val activeCluster: StateFlow<Cluster?> = combine(_clusters, _activeClusterId) { cls, id ->
        cls.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Filtered traces (reacts to cluster state changes) ──
    private val _stateVersion = MutableStateFlow(0L)

    val filteredTraces: StateFlow<List<TraceState>> = combine(activeCluster, _stateVersion) { cl, _ ->
        cl?.filterTraces(cl.overviewFilter) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Import ──
    private val _importMsg = MutableStateFlow<Pair<String, Boolean>?>(null) // text, isOk
    val importMsg: StateFlow<Pair<String, Boolean>?> = _importMsg.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadProgress = MutableStateFlow<String?>(null)
    val loadProgress: StateFlow<String?> = _loadProgress.asStateFlow()

    // ── Compare ──
    private val _crossCompareState = MutableStateFlow<CrossCompareState?>(null)
    val crossCompareState: StateFlow<CrossCompareState?> = _crossCompareState.asStateFlow()

    private val _anchorKey = MutableStateFlow<String?>(null)
    val anchorKey: StateFlow<String?> = _anchorKey.asStateFlow()

    // ── Theme ──
    private val _themeMode = MutableStateFlow(ThemePrefs.load(app))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // ── Sessions ──
    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // ── Auto-save debounce ──
    private var autoSaveJob: Job? = null

    init {
        // Restore state on launch
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val restored = SessionManager.restoreCurrentState(ctx)
                if (restored != null) {
                    val (sessionId, clusters, activeId) = restored
                    if (clusters.isNotEmpty()) {
                        _clusters.value = clusters
                        _activeClusterId.value = activeId ?: clusters.firstOrNull()?.id
                        _currentSessionId.value = sessionId
                    }
                }
            } catch (_: Exception) {}
            refreshSessions()
        }
    }

    private fun notifyChange() {
        _stateVersion.value++
        // Force _clusters to re-emit so activeCluster recomposes
        _clusters.value = _clusters.value.toList()
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // Debounce 500ms
            try {
                SessionManager.saveCurrentState(
                    ctx,
                    _currentSessionId.value,
                    _activeClusterId.value,
                    _clusters.value
                )
            } catch (_: Exception) {}
        }
    }

    // ── Cluster operations ──

    fun addCluster(name: String, entries: List<TraceEntry>) {
        val allStates = entries.map { TraceState.fromEntry(it) }
        val seen = mutableSetOf<String>()
        val states = allStates.filter { seen.add(it.key) }
        if (states.isEmpty()) return

        val cl = Cluster(name = name, traces = states)
        cl.recomputeCounts()
        states.firstOrNull()?.ensureCache()

        _clusters.value = _clusters.value + cl
        _activeClusterId.value = cl.id
        notifyChange()
    }

    fun removeCluster(id: String) {
        _clusters.value = _clusters.value.filter { it.id != id }
        if (_activeClusterId.value == id) {
            _activeClusterId.value = _clusters.value.firstOrNull()?.id
        }
        notifyChange()
    }

    fun switchCluster(id: String) {
        _activeClusterId.value = id
        notifyChange()
    }

    fun renameCluster(id: String, name: String) {
        val cl = _clusters.value.find { it.id == id } ?: return
        if (name.trim().isNotEmpty()) cl.name = name.trim()
        notifyChange()
    }

    // ── Verdicts ──

    fun setVerdict(traceKey: String, verdict: Verdict) {
        activeCluster.value?.let { cl ->
            cl.setVerdict(traceKey, verdict)
            notifyChange()
        }
    }

    // ── Filtering ──

    fun setOverviewFilter(filter: OverviewFilter) {
        activeCluster.value?.let { cl ->
            cl.overviewFilter = filter
            notifyChange()
        }
    }

    fun toggleSort() {
        activeCluster.value?.let { cl ->
            if (cl.sortField == SortField.STARTUP_DUR) {
                cl.sortDir = if (cl.sortDir == 1) -1 else 1
            } else {
                cl.sortField = SortField.STARTUP_DUR
                cl.sortDir = 1
            }
            notifyChange()
        }
    }

    fun resetSort() {
        activeCluster.value?.let { cl ->
            cl.sortField = SortField.INDEX
            cl.sortDir = 1
            notifyChange()
        }
    }

    // ── Sliders ──

    fun updateSlider(ts: TraceState, value: Int) {
        ts.updateSlider(value)
        notifyChange()
    }

    fun updateGlobalSlider(pct: Int) {
        activeCluster.value?.let { cl ->
            cl.updateGlobalSlider(pct)
            notifyChange()
        }
    }

    // ── Property filters ──

    fun togglePropFilter(field: String, value: String) {
        activeCluster.value?.let { cl ->
            cl.togglePropFilter(field, value)
            notifyChange()
        }
    }

    fun clearPropFilter(field: String) {
        activeCluster.value?.let { cl ->
            cl.clearPropFilter(field)
            notifyChange()
        }
    }

    // ── Import ──

    fun importText(text: String, clusterName: String = "Import") {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _loadProgress.value = "Parsing..."
            try {
                val traces = TraceParser.parseText(text)
                withContext(Dispatchers.Main) {
                    if (traces.isEmpty()) {
                        _importMsg.value = "No valid traces found" to false
                    } else {
                        addCluster(clusterName, traces)
                        _importMsg.value = "Loaded ${traces.size} traces" to true
                    }
                }
            } catch (e: Exception) {
                _importMsg.value = (e.message ?: "Parse error") to false
            }
            _loading.value = false
            _loadProgress.value = null
        }
    }

    fun clearImportMsg() { _importMsg.value = null }

    // ── Session management ──

    fun saveSession(name: String = "Session") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = SessionManager.saveSession(
                    ctx, name, _clusters.value, _activeClusterId.value
                )
                _currentSessionId.value = id
                refreshSessions()
                _importMsg.value = "Session saved" to true
            } catch (e: Exception) {
                _importMsg.value = "Save failed: ${e.message}" to false
            }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _loadProgress.value = "Loading session..."
            try {
                val result = SessionManager.loadSession(ctx, sessionId)
                if (result != null) {
                    val (clusters, activeId) = result
                    _clusters.value = clusters
                    _activeClusterId.value = activeId ?: clusters.firstOrNull()?.id
                    _currentSessionId.value = sessionId
                    _importMsg.value = "Session loaded (${clusters.size} clusters)" to true
                    scheduleAutoSave()
                }
            } catch (e: Exception) {
                _importMsg.value = "Load failed: ${e.message}" to false
            }
            _loading.value = false
            _loadProgress.value = null
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SessionManager.deleteSession(ctx, sessionId)
            if (_currentSessionId.value == sessionId) _currentSessionId.value = null
            refreshSessions()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            SessionManager.deleteAllSessions(ctx)
            _clusters.value = emptyList()
            _activeClusterId.value = null
            _currentSessionId.value = null
            _crossCompareState.value = null
            _anchorKey.value = null
            refreshSessions()
        }
    }

    fun refreshSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _sessions.value = SessionManager.listSessions(ctx)
        }
    }

    fun getSessionJson(sessionId: String, callback: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = SessionManager.getSessionJson(ctx, sessionId)
            withContext(Dispatchers.Main) { callback(json) }
        }
    }

    fun exportCurrentAsJson(): String {
        return SessionManager.clustersToJson(_clusters.value, _activeClusterId.value)
    }

    fun importSessionFromText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val (clusters, activeId) = SessionManager.parseExternalJson(text)
                _clusters.value = clusters
                _activeClusterId.value = activeId ?: clusters.firstOrNull()?.id
                _importMsg.value = "Session restored (${clusters.size} clusters)" to true
                scheduleAutoSave()
            } catch (e: Exception) {
                _importMsg.value = "Session load failed: ${e.message}" to false
            }
            _loading.value = false
        }
    }

    // ── Theme ──

    fun setTheme(mode: ThemeMode) {
        _themeMode.value = mode
        ThemePrefs.save(ctx, mode)
    }

    // ── Cross Compare ──

    fun startCrossCompare() {
        val cl = activeCluster.value ?: return
        if (cl.traces.size < 2) return
        val keys = cl.traces.map { it.key }
        val existing = _crossCompareState.value
        if (existing != null && existing.traceKeys == keys) return // Resume
        _crossCompareState.value = CrossCompare.createState(keys)
        _anchorKey.value = null
    }

    fun setAnchor(key: String) {
        _anchorKey.value = key
        // Advance to anchor-based pair
        val state = _crossCompareState.value ?: return
        val pair = CrossCompare.nextPairForAnchor(state, key)
        if (pair != null) {
            state.currentPair = pair
            state.isComplete = false
        }
        _crossCompareState.value = state
        notifyChange()
    }

    fun recordComparison(result: ComparisonResult) {
        val state = _crossCompareState.value ?: return
        val pair = state.currentPair ?: return
        CrossCompare.recordComparison(state, pair.first, pair.second, result)
        advancePair(state)
        _crossCompareState.value = state
        notifyChange()
    }

    fun skipComparison() {
        val state = _crossCompareState.value ?: return
        CrossCompare.skipCurrentPair(state)
        advancePair(state)
        _crossCompareState.value = state
        notifyChange()
    }

    fun undoComparison() {
        val state = _crossCompareState.value ?: return
        if (state.history.isEmpty()) return
        CrossCompare.undoComparison(state)
        val anchor = _anchorKey.value
        if (anchor != null && anchor !in state.discardedKeys) {
            val pair = CrossCompare.nextPairForAnchor(state, anchor)
            if (pair != null) {
                state.currentPair = pair
                state.isComplete = false
            }
        }
        _crossCompareState.value = state
        notifyChange()
    }

    fun discardCompareTrace(side: Side) {
        val state = _crossCompareState.value ?: return
        val pair = state.currentPair ?: return
        val cl = activeCluster.value ?: return
        val key = if (side == Side.LEFT) pair.first else pair.second
        cl.setVerdict(key, Verdict.DISCARD)
        CrossCompare.discardTrace(state, key)
        advancePair(state)
        _crossCompareState.value = state
        notifyChange()
    }

    fun applyCrossCompareResults(positiveIdx: Int = 0, negativeIdx: Int = -1) {
        val state = _crossCompareState.value ?: return
        val cl = activeCluster.value ?: return
        val results = CrossCompare.getResults(state)

        if (positiveIdx in results.groups.indices) {
            for (key in results.groups[positiveIdx]) cl.verdicts[key] = Verdict.LIKE
        }
        if (negativeIdx == -1) {
            // Pure anchor: all except positive → negative
            for (i in results.groups.indices) {
                if (i == positiveIdx) continue
                for (key in results.groups[i]) cl.verdicts[key] = Verdict.DISLIKE
            }
        } else if (negativeIdx in results.groups.indices) {
            for (key in results.groups[negativeIdx]) cl.verdicts[key] = Verdict.DISLIKE
        }
        cl.recomputeCounts()
        _crossCompareState.value = null
        _anchorKey.value = null
        notifyChange()
    }

    fun resetCrossCompare() {
        val cl = activeCluster.value ?: return
        val keys = cl.traces.map { it.key }
        _crossCompareState.value = CrossCompare.createState(keys)
        _anchorKey.value = null
        notifyChange()
    }

    fun closeCrossCompare() {
        _crossCompareState.value = null
        _anchorKey.value = null
    }

    private fun advancePair(state: CrossCompareState) {
        val anchor = _anchorKey.value
        if (anchor != null && anchor !in state.discardedKeys) {
            state.currentPair = CrossCompare.nextPairForAnchor(state, anchor)
            if (state.currentPair == null) {
                // Check if pure anchor session
                val isPureAnchor = state.history.all { entry ->
                    entry is HistoryEntry.Discard ||
                    (entry is HistoryEntry.Compare && (entry.keyA == anchor || entry.keyB == anchor))
                }
                if (!isPureAnchor) state.currentPair = CrossCompare.nextPair(state)
            }
        } else {
            state.currentPair = CrossCompare.nextPair(state)
        }
        if (state.currentPair == null) state.isComplete = true
        state.selectedSide = null
    }

    // ── Export ──

    fun buildExportRows(scope: String = "tab"): List<com.swiperf.app.data.export.ExportRow> {
        val cl = activeCluster.value ?: return emptyList()
        val clusters = if (scope == "all") _clusters.value else listOf(cl)
        return ExportHelper.buildRows(clusters)
    }

    fun exportTsv(scope: String = "tab"): String {
        return ExportHelper.rowsToTsv(buildExportRows(scope))
    }

    fun exportJson(scope: String = "tab"): String {
        return ExportHelper.rowsToJson(buildExportRows(scope))
    }

    // ── Copy filtered to new tab ──

    fun copyFilteredToNewTab() {
        val cl = activeCluster.value ?: return
        val filtered = filteredTraces.value
        if (filtered.isEmpty()) return

        val entries = filtered.map { ts ->
            TraceEntry(ts.trace.traceUuid, ts.trace.packageName, ts.trace.startupDur, ts.trace.slices, ts.trace.extra?.toMap())
        }
        val newStates = entries.map { TraceState.fromEntry(it) }
        val newCl = Cluster(name = cl.name + " (copy)", traces = newStates)
        // Carry over verdicts
        for (ts in newStates) {
            val v = cl.verdicts[ts.key]
            if (v != null) newCl.verdicts[ts.key] = v
        }
        newCl.recomputeCounts()
        newStates.firstOrNull()?.ensureCache()

        _clusters.value = _clusters.value + newCl
        _activeClusterId.value = newCl.id
        notifyChange()
    }
}
