package com.swiperf.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swiperf.app.data.model.Verdict
import com.swiperf.app.ui.component.SettingsDrawerContent
import com.swiperf.app.ui.screen.*
import com.swiperf.app.ui.viewmodel.SwiPerfViewModel
import kotlinx.coroutines.launch

sealed class Route(val route: String) {
    data object Start : Route("start")
    data object Main : Route("main")
    data object Compare : Route("compare")
    data object CompareReview : Route("compare_review")
}

@Composable
fun SwiPerfApp(vm: SwiPerfViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Collect state
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

    // Navigate to main when clusters are loaded
    LaunchedEffect(clusters) {
        if (clusters.isNotEmpty() && navController.currentDestination?.route == Route.Start.route) {
            navController.navigate(Route.Main.route) {
                popUpTo(Route.Start.route) { inclusive = true }
            }
        }
    }

    fun importFile(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        // Check if it's a session file
        if (text.trimStart().startsWith("{") && text.contains("\"version\"") && text.contains("\"clusters\"")) {
            vm.importSessionFromText(text)
        } else {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Import"
            vm.importText(text, name)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SettingsDrawerContent(
                    themeMode = themeMode,
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSetTheme = vm::setTheme,
                    onLoadSession = { id ->
                        vm.loadSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = vm::deleteSession,
                    onDeleteAllData = {
                        vm.deleteAllData()
                        navController.navigate(Route.Start.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onExportSession = { id ->
                        vm.getSessionJson(id) { json ->
                            if (json != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_TEXT, json)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Export session"))
                            }
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = if (clusters.isEmpty()) Route.Start.route else Route.Main.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Route.Start.route) {
                StartScreen(
                    sessions = sessions,
                    loading = loading,
                    loadProgress = loadProgress,
                    importMsg = importMsg,
                    themeMode = themeMode,
                    onImportFile = ::importFile,
                    onPasteText = { text -> vm.importText(text, "Paste") },
                    onLoadSession = vm::loadSession,
                    onDeleteSession = vm::deleteSession,
                    onSetTheme = vm::setTheme,
                    onClearImportMsg = vm::clearImportMsg
                )
            }

            composable(Route.Main.route) {
                MainScreen(
                    clusters = clusters,
                    activeCluster = activeCluster,
                    filteredTraces = filteredTraces,
                    onSwitchCluster = vm::switchCluster,
                    onRemoveCluster = vm::removeCluster,
                    onSetOverviewFilter = vm::setOverviewFilter,
                    onSetVerdict = { key, v -> vm.setVerdict(key, v) },
                    onSliderChange = { ts, v -> vm.updateSlider(ts, v) },
                    onGlobalSliderChange = vm::updateGlobalSlider,
                    onToggleSort = vm::toggleSort,
                    onStartCompare = {
                        vm.startCrossCompare()
                        navController.navigate(Route.Compare.route)
                    },
                    onSaveSession = {
                        val name = activeCluster?.name ?: "Session"
                        vm.saveSession(name)
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onExportTsv = vm::exportTsv,
                    onExportJson = vm::exportJson,
                    onCopyToNewTab = vm::copyFilteredToNewTab,
                    importMsg = importMsg,
                    onClearImportMsg = vm::clearImportMsg
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
}
