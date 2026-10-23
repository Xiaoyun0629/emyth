/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.activity

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.toComposeColor
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File
import java.io.FileOutputStream

class TaskOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContent {
            me.rerere.rikkahub.ui.theme.RikkahubTheme {
                TaskOverlayScreen(onFinished = { finish() })
            }
        }
    }
}

@Composable
private fun TaskOverlayScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { getKoin().get<SettingsStore>() }
    val task = store.settingsFlow.value.taskSetting

    var remaining by remember { mutableLongStateOf(task.deadlineMillis - System.currentTimeMillis()) }

    LaunchedEffect(task.deadlineMillis) {
        while (remaining > 0) {
            delay(1000)
            remaining = task.deadlineMillis - System.currentTimeMillis()
        }
    }

    // 任务已通过 / 无任务，直接关闭
    LaunchedEffect(task.status) {
        if (task.status == "approved" || task.status == "none") {
            onFinished()
        }
    }

    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch {
                val file = saveBitmap(context, bitmap)
                store.update { s ->
                    s.copy(taskSetting = s.taskSetting.copy(
                        submission = file.absolutePath,
                        submittedAt = System.currentTimeMillis(),
                        status = "submitted",
                    ))
                }
                onFinished()
            }
        }
    }

    val bgColor = task.backgroundColor?.toComposeColor() ?: MaterialTheme.colorScheme.background
    val btnColor = task.buttonColor?.toComposeColor() ?: MaterialTheme.colorScheme.primary
    val cdColor = task.countdownColor?.toComposeColor() ?: MaterialTheme.colorScheme.error

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = HugeIcons.CheckList,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = btnColor,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = task.content,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (task.type == "photo") "拍照提交" else "文字提交",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            val minutes = (remaining / 60_000).coerceAtLeast(0)
            val seconds = ((remaining % 60_000) / 1000).coerceAtLeast(0)
            Text(
                text = if (remaining > 0) {
                    "剩 ${minutes}分${seconds.toString().padStart(2, '0')}秒"
                } else {
                    "已超时"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cdColor,
            )
            Spacer(Modifier.height(28.dp))

            if (task.type == "photo") {
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                ) {
                    Icon(HugeIcons.Camera01, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("拍照提交")
                }
            } else {
                Button(
                    onClick = { showTextDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                ) {
                    Icon(HugeIcons.PencilEdit01, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("提交")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (task.punishment.isNotBlank()) {
                Text(
                    text = "超时惩罚：${task.punishment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("提交任务") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("写点什么，让安焮审核") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTextDialog = false
                    scope.launch {
                        store.update { s ->
                            s.copy(taskSetting = s.taskSetting.copy(
                                submission = textInput,
                                submittedAt = System.currentTimeMillis(),
                                status = "submitted",
                            ))
                        }
                        onFinished()
                    }
                }) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun saveBitmap(context: Context, bitmap: Bitmap): File {
    val file = File(context.filesDir, "task_photo_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return file
}
