/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.emyth.app.data.db.entity.WorkspaceEntity
import dev.emyth.app.data.repository.WorkspaceRepository

class WorkspaceVM(
    private val repository: WorkspaceRepository,
) : ViewModel() {
    val workspaces: StateFlow<List<WorkspaceEntity>> = repository.listFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            runCatching { repository.checkIntegrity() }
        }
    }

    fun createWorkspace(name: String, onResult: (Result<WorkspaceEntity>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.create(name) }
            onResult(result)
        }
    }

    fun deleteWorkspace(id: String) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
        }
    }
}