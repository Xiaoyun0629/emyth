/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.VoiceFavoriteItem
import dev.emyth.app.data.event.AppEvent
import dev.emyth.app.data.event.AppEventBus
import dev.emyth.app.ui.components.nav.BackButton
import dev.emyth.app.ui.theme.CustomColors
import dev.emyth.app.utils.plus
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Play
import org.koin.java.KoinJavaComponent.getKoin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceFavoritePage() {
    val settingsStore = getKoin().get<SettingsStore>()
    val eventBus = getKoin().get<AppEventBus>()
    val scope = rememberCoroutineScope()
    val setting by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val favorites = setting.voiceFavoriteSetting.favorites
    val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text("语音收藏") },
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "还没有收藏的语音",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = innerPadding + PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(favorites.reversed(), key = { it.timestamp }) { item ->
                VoiceFavoriteCard(
                    item = item,
                    dateFormat = dateFormat,
                    onPlay = { scope.launch { eventBus.emit(AppEvent.Speak(item.text)) } },
                    onDelete = {
                        scope.launch {
                            settingsStore.update { s ->
                                s.copy(
                                    voiceFavoriteSetting = s.voiceFavoriteSetting.copy(
                                        favorites = s.voiceFavoriteSetting.favorites.filter { it.timestamp != item.timestamp }
                                    )
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceFavoriteCard(
    item: VoiceFavoriteItem,
    dateFormat: SimpleDateFormat,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = dateFormat.format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }

        IconButton(onClick = onPlay) {
            Icon(
                imageVector = HugeIcons.Play,
                contentDescription = "播放",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = HugeIcons.Delete01,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}
