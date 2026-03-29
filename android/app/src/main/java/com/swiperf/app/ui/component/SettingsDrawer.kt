package com.swiperf.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.scoring.DictEntry
import com.swiperf.app.data.scoring.ScoringDictionary
import com.swiperf.app.data.session.SessionMeta
import com.swiperf.app.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    sessions: List<SessionMeta>,
    currentSessionId: String?,
    hasData: Boolean,
    clusterName: String,
    onSetTheme: (ThemeMode) -> Unit,
    onSaveSession: (String) -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllData: () -> Unit,
    onExportSession: (String) -> Unit,
    onSyncRemote: (() -> Unit)? = null,
    scoringDict: ScoringDictionary = ScoringDictionary(),
    scoringUseDict: Boolean = true,
    scoringNormalizeDigits: Boolean = false,
    onToggleUseDict: (() -> Unit)? = null,
    onToggleNormalizeDigits: (() -> Unit)? = null,
    onRemoveDictEntries: ((List<DictEntry>) -> Unit)? = null,
    onClearDict: (() -> Unit)? = null,
    onImportDict: ((json: String, merge: Boolean) -> Unit)? = null,
    autoPinFirst: Boolean = true,
    onToggleAutoPinFirst: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    var showDictionary by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))

            // Theme
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            for (mode in ThemeMode.entries) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = themeMode == mode, onClick = { onSetTheme(mode) })
                    Column {
                        Text(
                            mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (mode == ThemeMode.SYSTEM) {
                            Text(
                                "Follow device setting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            // Save session
            if (hasData) {
                var showSaveDialog by remember { mutableStateOf(false) }
                Text("Session", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Current Session")
                }

                if (showSaveDialog) {
                    var name by remember { mutableStateOf(clusterName) }
                    AlertDialog(
                        onDismissRequest = { showSaveDialog = false },
                        title = { Text("Save Session") },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Session name") },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                        },
                        confirmButton = {
                            Button(onClick = { onSaveSession(name.ifBlank { clusterName }); showSaveDialog = false }) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
            }

            // Past sessions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Past Sessions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${sessions.size} saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    "No sessions saved yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == currentSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoadSession(session.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    "${fmt.format(session.updatedAt)} \u00b7 ${session.traceCount} traces",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onExportSession(session.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, "Export", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteSession(session.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(16.dp))

            // Remote sync
            if (onSyncRemote != null) {
                Text("Sync", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSyncRemote(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fetch from server")
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
            }

            // Scoring
            run {
                Text("Scoring", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // Use dictionary switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Use dictionary", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Auto-resolve regions from learned equivalences",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scoringUseDict,
                        onCheckedChange = { onToggleUseDict?.invoke() }
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Normalize digits switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Normalize digits", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Replace numbers with [num] before comparing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scoringNormalizeDigits,
                        onCheckedChange = { onToggleNormalizeDigits?.invoke() }
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Auto-pin first trace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-pin first trace", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Pin the first trace as anchor when loading data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoPinFirst,
                        onCheckedChange = { onToggleAutoPinFirst?.invoke() }
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Dictionary button
                OutlinedButton(
                    onClick = { showDictionary = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scoring Dictionary (${scoringDict.size})")
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
            }

            // Data management
            Text("Data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sessions.isNotEmpty() || hasData) {
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Data")
                }
            } else {
                Text(
                    "No data to clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all data?") },
            text = { Text("This will permanently delete all saved sessions and loaded data.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteAllData(); confirmClear = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    if (showDictionary) {
        DictionarySheet(
            dictionary = scoringDict,
            onRemove = { entries -> onRemoveDictEntries?.invoke(entries) },
            onClear = { onClearDict?.invoke() },
            onImport = { json, merge -> onImportDict?.invoke(json, merge) },
            onDismiss = { showDictionary = false }
        )
    }
}
