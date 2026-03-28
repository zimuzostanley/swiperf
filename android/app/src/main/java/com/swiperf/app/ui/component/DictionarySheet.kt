package com.swiperf.app.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
                        val verdictLabel = if (entry.verdict == RegionVerdict.SAME) "same" else "diff"
                        val verdictColor = if (entry.verdict == RegionVerdict.SAME) PerfettoColors.POSITIVE_COLOR else PerfettoColors.NEGATIVE_COLOR
                        val symbol = if (entry.verdict == RegionVerdict.SAME) "\u2248" else "\u2260"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { if (isSelected) selected.remove(entry) else selected.add(entry) }
                                .padding(10.dp)
                        ) {
                            // Header
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(verdictLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = verdictColor)
                                if (entry.normalized) { Spacer(Modifier.width(6.dp)); Text("[n]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                                if (entry.hitCount > 0) { Spacer(Modifier.width(6.dp)); Text("\u00d7${entry.hitCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Spacer(Modifier.weight(1f))
                                Checkbox(checked = isSelected, onCheckedChange = { if (it) selected.add(entry) else selected.remove(entry) }, modifier = Modifier.size(20.dp))
                            }
                            // Column headers
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.width(48.dp))
                                Text("anchor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Text("target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(4.dp))
                            // Fields
                            for ((field, anchor, target) in entry.signature) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(field.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                                    Text(anchor ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { scope.launch { snackbar.showSnackbar(anchor ?: "null", duration = SnackbarDuration.Short) } })
                                    Text(symbol, style = MaterialTheme.typography.bodySmall, color = verdictColor)
                                    Text(target ?: "\u2014", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { scope.launch { snackbar.showSnackbar(target ?: "null", duration = SnackbarDuration.Short) } })
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
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(android.content.Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Export dictionary"))
                    }, enabled = dictionary.size > 0) {
                        Text("download", style = MaterialTheme.typography.labelSmall)
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
