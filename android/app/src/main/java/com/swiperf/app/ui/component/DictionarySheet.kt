package com.swiperf.app.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swiperf.app.data.scoring.DictEntry
import com.swiperf.app.data.scoring.RegionVerdict
import com.swiperf.app.data.scoring.ScoringDictionary
import com.swiperf.app.ui.theme.PerfettoColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    dictionary: ScoringDictionary,
    onRemove: (List<DictEntry>) -> Unit,
    onClear: () -> Unit,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var filterNormalized by remember { mutableStateOf<Boolean?>(null) } // null = show all
    val selected = remember { mutableStateListOf<DictEntry>() }
    var showImport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(search, filterNormalized, dictionary.all) {
        val q = search.lowercase()
        dictionary.all
            .filter { if (filterNormalized != null) it.normalized == filterNormalized else true }
            .filter { if (q.isBlank()) true else q in it.searchText }
            .sortedByDescending { it.hitCount }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dictionary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${dictionary.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))

                // Search
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search\u2026", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))

                // Filter: all / raw / normalized
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filterNormalized == null, onClick = { filterNormalized = null }, label = { Text("all") })
                    FilterChip(selected = filterNormalized == false, onClick = { filterNormalized = false }, label = { Text("raw") })
                    FilterChip(selected = filterNormalized == true, onClick = { filterNormalized = true }, label = { Text("normalized") })
                }
                Spacer(Modifier.height(6.dp))

                // Entry list
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it.displayLabel }) { entry ->
                        val isSelected = entry in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selected.remove(entry) else selected.add(entry)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { if (it) selected.add(entry) else selected.remove(entry) },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                entry.displayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.verdict == RegionVerdict.SAME) PerfettoColors.POSITIVE_COLOR
                                else PerfettoColors.NEGATIVE_COLOR,
                                modifier = Modifier.weight(1f)
                            )
                            if (entry.normalized) {
                                Text(
                                    "[n]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (entry.hitCount > 0) {
                                Text(
                                    "\u00d7${entry.hitCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        if (search.isNotEmpty()) "No matches" else "Dictionary is empty",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Select all / deselect
                    TextButton(onClick = {
                        if (selected.size == filtered.size) selected.clear()
                        else { selected.clear(); selected.addAll(filtered) }
                    }) {
                        Text(if (selected.size == filtered.size) "deselect" else "select all", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))

                    if (selected.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            onRemove(selected.toList())
                            selected.clear()
                        }) {
                            Text("remove ${selected.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val json = dictionary.toJson()
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("SwiPerf Dict", json))
                        scope.launch { snackbar.showSnackbar("Copied ${dictionary.size} entries", duration = SnackbarDuration.Short) }
                    }, enabled = dictionary.size > 0) {
                        Text("export", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { showImport = true }) {
                        Text("import", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    if (dictionary.size > 0) {
                        OutlinedButton(onClick = { confirmClear = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("clear all", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    // Import dialog
    if (showImport) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import Dictionary") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Paste dictionary JSON\u2026") },
                    singleLine = false,
                    shape = RoundedCornerShape(4.dp)
                )
            },
            confirmButton = {
                Button(onClick = { onImport(importText); showImport = false }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }

    // Confirm clear
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear dictionary?") },
            text = { Text("Remove all ${dictionary.size} learned equivalences?") },
            confirmButton = {
                Button(onClick = { onClear(); confirmClear = false; selected.clear() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
