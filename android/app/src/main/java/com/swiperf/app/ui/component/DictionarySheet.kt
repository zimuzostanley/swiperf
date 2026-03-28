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
import androidx.compose.ui.text.style.TextOverflow
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
    onImport: (json: String, merge: Boolean) -> Unit,
    onSaveFile: ((content: String, filename: String) -> Unit)? = null,
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
    var filterVerdict by remember { mutableStateOf<RegionVerdict?>(null) }

    val filtered = remember(search, filterNormalized, filterVerdict, dictionary.all) {
        val q = search.lowercase()
        dictionary.all
            .filter { if (filterNormalized != null) it.normalized == filterNormalized else true }
            .filter { if (filterVerdict != null) it.verdict == filterVerdict else true }
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

                // Filters
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filterVerdict == null, onClick = { filterVerdict = null }, label = { Text("all") })
                    FilterChip(selected = filterVerdict == RegionVerdict.SAME, onClick = { filterVerdict = RegionVerdict.SAME }, label = { Text("same") })
                    FilterChip(selected = filterVerdict == RegionVerdict.DIFFERENT, onClick = { filterVerdict = RegionVerdict.DIFFERENT }, label = { Text("diff") })
                    FilterChip(selected = filterNormalized == true, onClick = { filterNormalized = if (filterNormalized == true) null else true }, label = { Text("[n]") })
                }
                Spacer(Modifier.height(6.dp))

                // Entry list
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it.displayLabel }) { entry ->
                        val isSelected = entry in selected
                        val symbol = if (entry.verdict == RegionVerdict.SAME) "\u2248" else "\u2260"
                        val verdictColor = if (entry.verdict == RegionVerdict.SAME) PerfettoColors.POSITIVE_COLOR else PerfettoColors.NEGATIVE_COLOR

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (isSelected) selected.remove(entry) else selected.add(entry) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { if (it) selected.add(entry) else selected.remove(entry) },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(
                                modifier = Modifier.weight(1f).clickable {
                                    // Show full text in snackbar
                                    val full = entry.signature.joinToString("\n") { (f, a, t) ->
                                        "${f.replace("_", " ")}: ${a ?: "\u2014"} $symbol ${t ?: "\u2014"}"
                                    }
                                    scope.launch { snackbar.showSnackbar(full, duration = SnackbarDuration.Short) }
                                }
                            ) {
                                for ((field, anchor, target) in entry.signature) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(field.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
                                        Text(anchor ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(symbol, style = MaterialTheme.typography.labelSmall, color = verdictColor, modifier = Modifier.padding(horizontal = 4.dp))
                                        Text(target ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (entry.normalized) {
                                    Text("[n]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                                if (entry.hitCount > 0) {
                                    Text("\u00d7${entry.hitCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
                        Text("copy", style = MaterialTheme.typography.labelSmall)
                    }
                    if (onSaveFile != null) {
                        OutlinedButton(onClick = {
                            onSaveFile(dictionary.toJson(), "swiperf-dict.json")
                        }, enabled = dictionary.size > 0) {
                            Text("save", style = MaterialTheme.typography.labelSmall)
                        }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("Paste dictionary JSON\u2026") },
                        singleLine = false,
                        shape = RoundedCornerShape(4.dp)
                    )
                    if (dictionary.size > 0) {
                        Text(
                            "Current dictionary has ${dictionary.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dictionary.size > 0) {
                        OutlinedButton(onClick = { onImport(importText, false); showImport = false }) { Text("Replace") }
                    }
                    Button(onClick = { onImport(importText, true); showImport = false }) {
                        Text(if (dictionary.size > 0) "Merge" else "Import")
                    }
                }
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
