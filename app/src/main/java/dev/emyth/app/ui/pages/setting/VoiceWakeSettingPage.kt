/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.emyth.app.data.service.VoiceWakeService
import dev.emyth.app.ui.components.ui.ColorPickerDialog
import dev.emyth.app.ui.components.ui.toComposeColor
import dev.emyth.app.data.wake.VoiceWakeStore
import dev.emyth.app.ui.activity.VoiceWakeEnrollActivity
import java.io.File

/** 语音唤醒设置页（独立入口，不在系统工具里）。 */
@Composable
fun VoiceWakeSettingPage() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(VoiceWakeStore.isEnabled(context)) }
    var responsePaths by remember { mutableStateOf(VoiceWakeStore.getResponsePaths(context)) }
    var hasTemplate by remember { mutableStateOf(VoiceWakeStore.hasTemplate(context)) }
    var logs by remember { mutableStateOf(VoiceWakeStore.getLogs(context)) }
    var subtitleColor by remember { mutableStateOf(VoiceWakeStore.getSubtitleColor(context)) }
    var nameColor by remember { mutableStateOf(VoiceWakeStore.getNameColor(context)) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showNamePicker by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> importResponseAudio(context, uri) }
        responsePaths = VoiceWakeStore.getResponsePaths(context)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用语音唤醒", fontSize = 16.sp)
                        Text(
                            "开启后手机持续监听唤醒词，喊中会弹出助手并播放回应音频",
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            enabled = on
                            VoiceWakeStore.setEnabled(context, on)
                            if (on) VoiceWakeService.start(context) else VoiceWakeService.stop(context)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("唤醒词", fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasTemplate) "已录入唤醒词 ✓" else "尚未录入唤醒词",
                    fontSize = 13.sp,
                    color = if (hasTemplate) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    else androidx.compose.ui.graphics.Color.Gray
                )
                TextButton(
                    onClick = {
                        context.startActivity(Intent(context, VoiceWakeEnrollActivity::class.java))
                        // 返回后刷新状态
                    }
                ) {
                    Text("录制唤醒词")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("回应音频", fontSize = 16.sp)
                    TextButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                        Text("导入音频")
                    }
                }
                Text("唤醒后随机播放一段", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                if (responsePaths.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("暂无回应音频", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
                } else {
                    Spacer(Modifier.height(6.dp))
                    responsePaths.forEach { path ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                VoiceWakeStore.removeResponsePath(context, path)
                                responsePaths = VoiceWakeStore.getResponsePaths(context)
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                File(path).name,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text("删除", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("外观", fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showSubtitlePicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("字幕颜色", fontSize = 14.sp)
                    Box(Modifier.size(26.dp).background(subtitleColor.toComposeColor(), CircleShape))
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showNamePicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("名称颜色", fontSize = 14.sp)
                    Box(Modifier.size(26.dp).background(nameColor.toComposeColor(), CircleShape))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("运行日志", fontSize = 16.sp)
                    Row {
                        TextButton(onClick = { logs = VoiceWakeStore.getLogs(context) }) {
                            Text("刷新")
                        }
                        TextButton(onClick = {
                            VoiceWakeStore.clearLogs(context)
                            logs = VoiceWakeStore.getLogs(context)
                        }) {
                            Text("清空")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (logs.isBlank()) {
                    Text("暂无日志", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                } else {
                    Text(logs, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }

    if (showSubtitlePicker) {
        ColorPickerDialog(
            initialColor = subtitleColor,
            defaultColor = Color.White,
            onConfirm = { c ->
                if (c != null) {
                    subtitleColor = c
                    VoiceWakeStore.setSubtitleColor(context, c)
                }
                showSubtitlePicker = false
            },
            onDismiss = { showSubtitlePicker = false }
        )
    }
    if (showNamePicker) {
        ColorPickerDialog(
            initialColor = nameColor,
            defaultColor = Color.White,
            onConfirm = { c ->
                if (c != null) {
                    nameColor = c
                    VoiceWakeStore.setNameColor(context, c)
                }
                showNamePicker = false
            },
            onDismiss = { showNamePicker = false }
        )
    }
}

/** 把选中的音频复制到 app 内部目录，避免 SAF 权限过期。 */
private fun importResponseAudio(context: Context, uri: Uri) {
    try {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_${System.currentTimeMillis()}.mp3"
        val dir = File(context.filesDir, "wake_responses").apply { mkdirs() }
        val dest = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        VoiceWakeStore.addResponsePath(context, dest.absolutePath)
    } catch (e: Exception) {
        // 忽略单个失败
    }
}
