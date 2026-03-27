package com.swiperf.app.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    hasClusters: Boolean,
    clusterCount: Int,
    onExportTsv: (scope: String) -> String,
    onExportJson: (scope: String) -> String,
    onSaveFile: (content: String, filename: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SwiPerf Export", text))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text("This tab", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    copyToClipboard(onExportTsv("tab"))
                    onDismiss()
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy TSV")
                }
                OutlinedButton(onClick = {
                    val tsv = onExportTsv("tab")
                    onSaveFile(tsv, "swiperf-tab.tsv")
                    onDismiss()
                }) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("TSV")
                }
                OutlinedButton(onClick = {
                    val json = onExportJson("tab")
                    onSaveFile(json, "swiperf-tab.json")
                    onDismiss()
                }) {
                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("JSON")
                }
            }

            if (clusterCount > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Text("All tabs", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        copyToClipboard(onExportTsv("all"))
                        onDismiss()
                    }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy TSV")
                    }
                    OutlinedButton(onClick = {
                        val tsv = onExportTsv("all")
                        onSaveFile(tsv, "swiperf-all.tsv")
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("TSV")
                    }
                    OutlinedButton(onClick = {
                        val json = onExportJson("all")
                        onSaveFile(json, "swiperf-all.json")
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("JSON")
                    }
                }
            }
        }
    }
}
