package com.swiperf.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swiperf.app.data.export.ExportHelper
import com.swiperf.app.data.model.*
import com.swiperf.app.data.parse.TraceParser
import com.swiperf.app.data.scoring.DictEntry
import com.swiperf.app.data.scoring.GlobalScoring
import com.swiperf.app.data.scoring.GlobalScoringState
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.data.scoring.ScoringDictionary
import com.swiperf.app.data.scoring.ScoringEngine
import com.swiperf.app.data.scoring.ScoringState
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
    val stateVersion: StateFlow<Long> = _stateVersion.asStateFlow()

    val filteredTraces: StateFlow<List<TraceState>> = combine(activeCluster, _stateVersion) { cl, _ ->
        cl?.filterTraces(cl.overviewFilter) ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Import ──
    private val _importMsg = MutableStateFlow<Pair<String, Boolean>?>(null) // text, isOk
    val importMsg: StateFlow<Pair<String, Boolean>?> = _importMsg.asStateFlow()

    private val _loading = MutableStateFlow(true) // Start true — restoring state
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadProgress = MutableStateFlow<String?>(null)
    val loadProgress: StateFlow<String?> = _loadProgress.asStateFlow()

    // ── Pin (derived from active cluster) ──
    val pinnedKey: StateFlow<String?> = activeCluster.map { it?.pinnedKey }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Dictionary (global across all tabs) ──
    val scoringDict = ScoringDictionary()
    private val _scoringUseDict = MutableStateFlow(true)
    val scoringUseDict: StateFlow<Boolean> = _scoringUseDict.asStateFlow()
    private val _scoringNormalizeDigits = MutableStateFlow(false)
    val scoringNormalizeDigits: StateFlow<Boolean> = _scoringNormalizeDigits.asStateFlow()

    // ── Scoring ──
    private var _scoringState: ScoringState? = null
    private val _scoringVersion = MutableStateFlow(0L)
    val scoringState: StateFlow<ScoringState?> = _scoringVersion.map { _scoringState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _scoringTargetKey = MutableStateFlow<String?>(null)
    val scoringTargetKey: StateFlow<String?> = _scoringTargetKey.asStateFlow()

    // ── Global Scoring ──
    private var _globalScoringState: GlobalScoringState? = null
    private val _globalScoringVersion = MutableStateFlow(0L)
    val globalScoringState: StateFlow<GlobalScoringState?> = _globalScoringVersion.map { _globalScoringState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Display trim length for scoring ──
    private val TRIM_STEPS = listOf(5, 10, 15, 20, 30, 50, 0) // 0 = all
    private val _trimLength = MutableStateFlow(
        ctx.getSharedPreferences("swiperf", Context.MODE_PRIVATE).getInt("trim_length", 0)
    )
    val trimLength: StateFlow<Int> = _trimLength.asStateFlow()

    fun cycleTrimLength() {
        val currentIdx = TRIM_STEPS.indexOf(_trimLength.value)
        val nextIdx = (currentIdx + 1) % TRIM_STEPS.size
        _trimLength.value = TRIM_STEPS[nextIdx]
        ctx.getSharedPreferences("swiperf", Context.MODE_PRIVATE).edit().putInt("trim_length", _trimLength.value).apply()
    }

    fun trimText(s: String?): String {
        if (s == null) return "\u2014"
        val len = _trimLength.value
        if (len == 0 || s.length <= len) return s
        return s.take(len) + "\u2026"
    }

    // ── Auto-pin first trace ──
    private val _autoPinFirst = MutableStateFlow(
        ctx.getSharedPreferences("swiperf", Context.MODE_PRIVATE).getBoolean("auto_pin_first", true)
    )
    val autoPinFirst: StateFlow<Boolean> = _autoPinFirst.asStateFlow()

    fun toggleAutoPinFirst() {
        val newVal = !_autoPinFirst.value
        _autoPinFirst.value = newVal
        ctx.getSharedPreferences("swiperf", Context.MODE_PRIVATE).edit().putBoolean("auto_pin_first", newVal).apply()
    }

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
                    if (restored.clusters.isNotEmpty()) {
                        _clusters.value = restored.clusters
                        _activeClusterId.value = restored.activeClusterId ?: restored.clusters.firstOrNull()?.id
                        _currentSessionId.value = restored.sessionId
                        // Apply saved global slider zoom to all traces
                        for (cl in restored.clusters) {
                            if (cl.globalSlider < 100) {
                                cl.updateGlobalSlider(cl.globalSlider)
                            }
                        }
                        _stateVersion.value++
                    }
                    restored.applyDictTo(scoringDict, _scoringUseDict, _scoringNormalizeDigits)
                    // Auto-pin first trace per cluster
                    if (_autoPinFirst.value) {
                        for (cl in restored.clusters) {
                            if (cl.pinnedKey == null && cl.traces.isNotEmpty()) {
                                cl.pinnedKey = cl.traces[0].key
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            _loading.value = false
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
                    _clusters.value,
                    scoringDict = scoringDict,
                    scoringUseDict = _scoringUseDict.value,
                    scoringNormalizeDigits = _scoringNormalizeDigits.value
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

        _clusters.value = _clusters.value + cl
        _activeClusterId.value = cl.id

        if (_autoPinFirst.value && states.isNotEmpty()) {
            cl.pinnedKey = states[0].key
        }

        notifyChange()

        // Pre-compute caches in background
        viewModelScope.launch(Dispatchers.Default) {
            for (ts in states) ts.ensureCache()
        }
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

    fun setSortField(field: SortField) {
        activeCluster.value?.let { cl ->
            if (cl.sortField == field) {
                cl.sortDir = if (cl.sortDir == 1) -1 else 1
            } else {
                cl.sortField = field
                cl.sortDir = 1
            }
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
                    ctx, name, _clusters.value, _activeClusterId.value,
                    scoringDict = scoringDict,
                    scoringUseDict = _scoringUseDict.value,
                    scoringNormalizeDigits = _scoringNormalizeDigits.value
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
                    _clusters.value = result.clusters
                    _activeClusterId.value = result.activeClusterId ?: result.clusters.firstOrNull()?.id
                    _currentSessionId.value = sessionId
                    result.applyDictTo(scoringDict, _scoringUseDict, _scoringNormalizeDigits)
                    // Apply saved global slider zoom
                    for (cl in result.clusters) {
                        if (cl.globalSlider < 100) cl.updateGlobalSlider(cl.globalSlider)
                    }
                    if (_autoPinFirst.value) {
                        for (cl in result.clusters) {
                            if (cl.pinnedKey == null && cl.traces.isNotEmpty()) {
                                cl.pinnedKey = cl.traces[0].key
                            }
                        }
                    }
                    _importMsg.value = "Session loaded (${result.clusters.size} clusters)" to true
                    _stateVersion.value++
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
            scoringDict.clear()
            _scoringUseDict.value = true
            _scoringNormalizeDigits.value = false
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
        return SessionManager.clustersToJson(
            _clusters.value, _activeClusterId.value,
            scoringDict = scoringDict,
            scoringUseDict = _scoringUseDict.value,
            scoringNormalizeDigits = _scoringNormalizeDigits.value
        )
    }

    fun importSessionFromText(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val result = SessionManager.parseExternalJson(text)
                _clusters.value = result.clusters
                _activeClusterId.value = result.activeClusterId ?: result.clusters.firstOrNull()?.id
                result.applyDictTo(scoringDict, _scoringUseDict, _scoringNormalizeDigits)
                for (cl in result.clusters) { if (cl.globalSlider < 100) cl.updateGlobalSlider(cl.globalSlider) }
                _importMsg.value = "Session restored (${result.clusters.size} clusters)" to true
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

    // ── Pin ──

    fun togglePin(key: String) {
        if (_scoringState != null) closeScoring()
        val cl = activeCluster.value ?: return
        val newKey = if (cl.pinnedKey == key) null else key
        if (newKey != cl.pinnedKey) {
            cl.scores.clear(); cl.scoreAnchorKey = null
        }
        cl.pinnedKey = newKey
        notifyChange()
    }

    fun clearPin() {
        val cl = activeCluster.value ?: return
        cl.pinnedKey = null
        notifyChange()
    }

    // ── Scoring operations ──

    fun startScoring(targetKey: String) {
        val cl = activeCluster.value ?: return
        val anchor = cl.traces.find { it.key == cl.pinnedKey } ?: return
        val target = cl.traces.find { it.key == targetKey } ?: return
        anchor.ensureCache()
        target.ensureCache()
        cl.scoreAnchorKey = cl.pinnedKey
        _scoringTargetKey.value = targetKey
        // Use compressed slices (current zoom level) for scoring
        val state = ScoringEngine.createStateFromMerged(
            anchor.currentSeq, anchor.totalDur,
            target.currentSeq, target.totalDur,
            normalize = _scoringNormalizeDigits.value
        )
        if (_scoringUseDict.value) {
            scoringDict.applyTo(state)
        }
        _scoringState = state
        _scoringVersion.value++
    }

    fun scoringVerdict(verdict: RegionVerdict, regionIndex: Int) {
        val state = _scoringState ?: return
        val idx = regionIndex
        val action = ScoringEngine.recordVerdict(state, idx, verdict)
        // Save score to cluster
        val cl = activeCluster.value
        val targetKey = _scoringTargetKey.value
        if (cl != null && targetKey != null) {
            cl.scores[targetKey] = state.manualSamePct / 100f
        }
        // Bump hit counts for cascaded entries in the dictionary
        if (action.learnedSignature != null && action.cascadedIndices.isNotEmpty()) {
            scoringDict.bumpHitCount(action.learnedSignature)
        }
        _scoringVersion.value++
        notifyChange()
    }

    fun scoringUndo() {
        val state = _scoringState ?: return
        ScoringEngine.undo(state)
        _scoringVersion.value++
        notifyChange()
    }

    fun scoringReset() {
        val cl = activeCluster.value ?: return
        val anchor = cl.traces.find { it.key == cl.pinnedKey } ?: return
        val target = cl.traces.find { it.key == _scoringTargetKey.value } ?: return
        anchor.ensureCache()
        target.ensureCache()
        // Fresh state — no dictionary applied
        _scoringState = ScoringEngine.createStateFromMerged(
            anchor.currentSeq, anchor.totalDur,
            target.currentSeq, target.totalDur,
            normalize = _scoringNormalizeDigits.value
        )
        _scoringVersion.value++
        notifyChange()
    }

    fun closeScoring() {
        val state = _scoringState
        val cl = activeCluster.value
        val targetKey = _scoringTargetKey.value
        if (state != null && cl != null && targetKey != null) {
            cl.scores[targetKey] = state.breakdown.samePct / 100f
        }
        // Save learned entries back to dictionary (tagged with normalize flag)
        if (state != null) {
            scoringDict.addFromState(state, normalized = _scoringNormalizeDigits.value)
        }
        _scoringState = null
        _scoringTargetKey.value = null
        _scoringVersion.value++
        notifyChange()
    }

    // ── Global Scoring operations ──

    fun startGlobalScoring() {
        val cl = activeCluster.value ?: return
        val anchor = cl.traces.find { it.key == cl.pinnedKey } ?: return
        val targets = cl.traces.filter { it.key != cl.pinnedKey }
        if (targets.isEmpty()) return
        _globalScoringState = GlobalScoring.createState(
            anchor, targets,
            normalize = _scoringNormalizeDigits.value,
            dict = if (_scoringUseDict.value) scoringDict else null
        )
        _globalScoringVersion.value++
    }

    fun globalScoringVerdict(verdict: RegionVerdict, entryIndex: Int) {
        val state = _globalScoringState ?: return
        GlobalScoring.recordVerdict(state, entryIndex, verdict)
        // Update scores for all traces
        val cl = activeCluster.value ?: return
        for ((traceKey, traceState) in state.perTrace) {
            cl.scores[traceKey] = traceState.breakdown.samePct / 100f
        }
        _globalScoringVersion.value++
        notifyChange()
    }

    fun globalScoringUndo() {
        val state = _globalScoringState ?: return
        GlobalScoring.undo(state)
        _globalScoringVersion.value++
        notifyChange()
    }

    fun closeGlobalScoring() {
        val state = _globalScoringState ?: return
        val cl = activeCluster.value ?: return
        for ((traceKey, traceState) in state.perTrace) {
            cl.scores[traceKey] = traceState.breakdown.samePct / 100f
        }
        // Save to dictionary
        for ((_, traceState) in state.perTrace) {
            scoringDict.addFromState(traceState, normalized = _scoringNormalizeDigits.value)
        }
        _globalScoringState = null
        _globalScoringVersion.value++
        notifyChange()
    }

    fun resetGlobalScoring() {
        val cl = activeCluster.value ?: return
        val anchor = cl.traces.find { it.key == cl.pinnedKey } ?: return
        val targets = cl.traces.filter { it.key != cl.pinnedKey }
        _globalScoringState = GlobalScoring.createState(anchor, targets, normalize = _scoringNormalizeDigits.value)
        _globalScoringVersion.value++
        notifyChange()
    }

    // ── Dictionary operations ──

    fun removeDictEntries(entries: List<DictEntry>) {
        scoringDict.removeAll(entries)
        notifyChange()
    }

    fun clearDict() {
        scoringDict.clear()
        notifyChange()
    }

    fun importDict(json: String, merge: Boolean = true) {
        if (!merge) scoringDict.clear()
        val imported = ScoringDictionary.fromJson(json)
        for (entry in imported.all) {
            scoringDict.addFromState(ScoringState(
                emptyList(),
                sameSignatures = if (entry.verdict == RegionVerdict.SAME) mutableSetOf(entry.signature) else mutableSetOf(),
                diffSignatures = if (entry.verdict == RegionVerdict.DIFFERENT) mutableSetOf(entry.signature) else mutableSetOf()
            ), normalized = entry.normalized)
        }
        notifyChange()
    }

    fun toggleScoringUseDict() {
        _scoringUseDict.value = !_scoringUseDict.value
        notifyChange()
    }

    fun toggleScoringNormalizeDigits() {
        _scoringNormalizeDigits.value = !_scoringNormalizeDigits.value
        notifyChange()
    }

    // ── Remote sync ──

    val remoteEnabled: Boolean get() = com.swiperf.app.data.remote.RemoteSource.isEnabled

    fun syncFromRemote() {
        if (!remoteEnabled) {
            _importMsg.value = "No endpoint configured" to false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _loadProgress.value = "Fetching from server..."
            try {
                val result = com.swiperf.app.data.remote.RemoteSource.fetch()
                val text = result.getOrThrow()
                withContext(Dispatchers.Main) {
                    // Try as session first, then as raw trace data
                    if (text.trimStart().startsWith("{") && text.contains("\"version\"") && text.contains("\"clusters\"")) {
                        val result = SessionManager.parseExternalJson(text)
                        _clusters.value = result.clusters
                        _activeClusterId.value = result.activeClusterId ?: result.clusters.firstOrNull()?.id
                        result.applyDictTo(scoringDict, _scoringUseDict, _scoringNormalizeDigits)
                        _stateVersion.value++
                        _importMsg.value = "Synced ${result.clusters.sumOf { it.traces.size }} traces" to true
                    } else {
                        val traces = TraceParser.parseText(text)
                        if (traces.isNotEmpty()) {
                            addCluster("Remote", traces)
                            _importMsg.value = "Loaded ${traces.size} traces from server" to true
                        } else {
                            _importMsg.value = "No traces in server response" to false
                        }
                    }
                    scheduleAutoSave()
                }
            } catch (e: Exception) {
                _importMsg.value = "Sync failed: ${e.message}" to false
            }
            _loading.value = false
            _loadProgress.value = null
        }
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
