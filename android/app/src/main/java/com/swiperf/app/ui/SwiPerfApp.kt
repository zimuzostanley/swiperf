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
    data object Compare : Route("compare")
    data object CompareReview : Route("compare_review")
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
    val ccState by vm.crossCompareState.collectAsState()
    val anchorKey by vm.anchorKey.collectAsState()
    val stateVersion by vm.stateVersion.collectAsState()

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
                onToggleSort = vm::toggleSort,
                onStartCompare = {
                    vm.startCrossCompare()
                    navController.navigate(Route.Compare.route)
                },
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
                onRefreshSessions = vm::refreshSessions
            )
        }

        composable(Route.Compare.route) {
            val currentCcState = ccState
            val currentCluster = activeCluster
            if (currentCcState != null && currentCluster != null) {
                CompareScreen(
                    cluster = currentCluster,
                    ccState = currentCcState,
                    anchorKey = anchorKey,
                    onSetAnchor = vm::setAnchor,
                    onRecordComparison = vm::recordComparison,
                    onSkip = vm::skipComparison,
                    onUndo = vm::undoComparison,
                    onClose = {
                        vm.closeCrossCompare()
                        navController.popBackStack()
                    },
                    onNavigateToReview = {
                        navController.navigate(Route.CompareReview.route) {
                            popUpTo(Route.Compare.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Route.CompareReview.route) {
            val currentCcState = ccState
            val currentCluster = activeCluster
            if (currentCcState != null && currentCluster != null) {
                CompareReviewScreen(
                    cluster = currentCluster,
                    ccState = currentCcState,
                    anchorKey = anchorKey,
                    onApply = { posIdx, negIdx ->
                        vm.applyCrossCompareResults(posIdx, negIdx)
                        navController.navigate(Route.Main.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onReset = {
                        vm.resetCrossCompare()
                        navController.navigate(Route.Compare.route) {
                            popUpTo(Route.CompareReview.route) { inclusive = true }
                        }
                    },
                    onClose = {
                        vm.closeCrossCompare()
                        navController.navigate(Route.Main.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
