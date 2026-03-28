package com.swiperf.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swiperf.app.ui.screen.*
import com.swiperf.app.ui.viewmodel.SwiPerfViewModel

sealed class Route(val route: String) {
    data object Main : Route("main")
    data object Scoring : Route("scoring")
}

@Composable
fun SwiPerfApp(vm: SwiPerfViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val clusters by vm.clusters.collectAsState()
    val activeCluster by vm.activeCluster.collectAsState()
    val filteredTraces by vm.filteredTraces.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()
    val loading by vm.loading.collectAsState()
    val loadProgress by vm.loadProgress.collectAsState()
    val importMsg by vm.importMsg.collectAsState()
    val pinnedKey by vm.pinnedKey.collectAsState()
    val stateVersion by vm.stateVersion.collectAsState()
    val scoringState by vm.scoringState.collectAsState()
    val scoringTargetKey by vm.scoringTargetKey.collectAsState()
    val autoPinFirst by vm.autoPinFirst.collectAsState()
    val scoringUseDict by vm.scoringUseDict.collectAsState()
    val scoringNormalizeDigits by vm.scoringNormalizeDigits.collectAsState()

    fun importFile(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        if (text.trimStart().startsWith("{") && text.contains("\"version\"") && text.contains("\"clusters\"")) {
            vm.importSessionFromText(text)
        } else {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Import"
            vm.importText(text, name)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.Main.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Route.Main.route) {
            MainScreen(
                clusters = clusters,
                activeCluster = activeCluster,
                filteredTraces = filteredTraces,
                stateVersion = stateVersion,
                themeMode = themeMode,
                sessions = sessions,
                currentSessionId = currentSessionId,
                loading = loading,
                loadProgress = loadProgress,
                importMsg = importMsg,
                onSwitchCluster = vm::switchCluster,
                onRemoveCluster = vm::removeCluster,
                onRenameCluster = vm::renameCluster,
                onSetOverviewFilter = vm::setOverviewFilter,
                onSetVerdict = { key, v -> vm.setVerdict(key, v) },
                onSliderChange = { ts, v -> vm.updateSlider(ts, v) },
                onGlobalSliderChange = vm::updateGlobalSlider,
                onSetSortField = vm::setSortField,
                pinnedKey = pinnedKey,
                onTogglePin = vm::togglePin,
                onSaveSession = { name -> vm.saveSession(name) },
                onImportFile = ::importFile,
                onPasteText = { text -> vm.importText(text, "Paste") },
                onLoadSession = vm::loadSession,
                onDeleteSession = vm::deleteSession,
                onDeleteAllData = vm::deleteAllData,
                onSetTheme = vm::setTheme,
                onExportTsv = vm::exportTsv,
                onExportJson = vm::exportJson,
                onExportSessionJson = { id, cb -> vm.getSessionJson(id, cb) },
                onCopyToNewTab = vm::copyFilteredToNewTab,
                onClearImportMsg = vm::clearImportMsg,
                onRefreshSessions = vm::refreshSessions,
                onSyncRemote = if (vm.remoteEnabled) vm::syncFromRemote else null,
                scores = activeCluster?.scores ?: emptyMap(),
                onStartScoring = { targetKey ->
                    vm.startScoring(targetKey)
                    navController.navigate(Route.Scoring.route)
                },
                scoringDict = vm.scoringDict,
                scoringUseDict = scoringUseDict,
                scoringNormalizeDigits = scoringNormalizeDigits,
                onToggleUseDict = vm::toggleScoringUseDict,
                onToggleNormalizeDigits = vm::toggleScoringNormalizeDigits,
                onRemoveDictEntries = vm::removeDictEntries,
                onClearDict = vm::clearDict,
                onImportDict = { json, merge -> vm.importDict(json, merge) },
                autoPinFirst = autoPinFirst,
                onToggleAutoPinFirst = vm::toggleAutoPinFirst
            )
        }

        composable(Route.Scoring.route) {
            val ss = scoringState
            if (ss != null) {
                val cl = activeCluster
                val anchorTrace = cl?.traces?.find { it.key == pinnedKey }
                val targetTrace = cl?.traces?.find { it.key == scoringTargetKey }
                anchorTrace?.ensureCache()
                targetTrace?.ensureCache()
                ScoringScreen(
                    scoringState = ss,
                    version = stateVersion,
                    anchorSeq = anchorTrace?.currentSeq ?: emptyList(),
                    anchorTotalDur = anchorTrace?.totalDur ?: 0L,
                    targetSeq = targetTrace?.currentSeq ?: emptyList(),
                    targetTotalDur = targetTrace?.totalDur ?: 0L,
                    onVerdict = vm::scoringVerdict,
                    onUndo = vm::scoringUndo,
                    onClose = {
                        vm.closeScoring()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
