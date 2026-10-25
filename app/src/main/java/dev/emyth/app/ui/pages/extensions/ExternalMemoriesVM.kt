/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.model.ExternalMemory
import kotlin.uuid.Uuid

class ExternalMemoriesVM(
    private val settingsStore: SettingsStore
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun addExternalMemory(
        name: String,
        supabaseUrl: String,
        supabaseKey: String,
        tableName: String,
        summariesTableName: String,
        autoSaveMessages: Boolean,
        autoSaveDiarySummary: Boolean,
        recallCount: Int,
        embeddingModelId: Uuid?,
    ) {
        updateExternalMemories(
            settings.value.externalMemories + ExternalMemory(
                name = name,
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey,
                tableName = tableName.ifBlank { "chat_messages" },
                summariesTableName = summariesTableName.ifBlank { "memory_summaries" },
                autoSaveMessages = autoSaveMessages,
                autoSaveDiarySummary = autoSaveDiarySummary,
                recallCount = recallCount,
                embeddingModelId = embeddingModelId,
            )
        )
    }

    fun updateExternalMemory(updated: ExternalMemory) {
        updateExternalMemories(
            settings.value.externalMemories.map { memory ->
                if (memory.id == updated.id) updated else memory
            }
        )
    }

    fun deleteExternalMemory(id: Uuid) {
        updateExternalMemories(
            settings.value.externalMemories.filterNot { memory ->
                memory.id == id
            }
        )
    }

    private fun updateExternalMemories(externalMemories: List<ExternalMemory>) {
        val validIds = externalMemories.map { it.id }.toSet()
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    externalMemories = externalMemories,
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            externalMemoryIds = assistant.externalMemoryIds.filter { it in validIds }.toSet()
                        )
                    }
                )
            }
        }
    }
}
