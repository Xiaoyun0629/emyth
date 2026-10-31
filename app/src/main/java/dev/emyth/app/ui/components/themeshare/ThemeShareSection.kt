package dev.emyth.app.ui.components.themeshare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.emyth.app.data.themeshare.ThemeShareCodec
import dev.emyth.app.data.themeshare.ThemeShareRepository
import kotlinx.coroutines.launch

/** 主题分享：导出分享码 + 导入分享码。 */
@Composable
fun ThemeShareSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var shareCode by remember { mutableStateOf("") }
    var importCode by remember { mutableStateOf("") }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch {
                runCatching {
                    val data = ThemeShareRepository.export(context)
                    shareCode = ThemeShareCodec.encode(data)
                    showExport = true
                }
            }
        }) {
            Icon(Icons.Filled.Upload, contentDescription = null)
            Text("导出主题")
        }
        OutlinedButton(onClick = { showImport = true }) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("导入主题")
        }
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("主题分享码") },
            text = {
                Column {
                    Text("复制下面的分享码发给别人：")
                    Text(
                        shareCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(shareCode))
                    showExport = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text("关闭") }
            },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入主题") },
            text = {
                OutlinedTextField(
                    value = importCode,
                    onValueChange = { importCode = it },
                    label = { Text("粘贴分享码") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            val data = ThemeShareCodec.decode(importCode)
                            ThemeShareRepository.import(context, data)
                        }
                        showImport = false
                    }
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            },
        )
    }
}
