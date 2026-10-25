/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.di

import dev.emyth.app.ui.pages.assistant.AssistantVM
import dev.emyth.app.ui.pages.assistant.detail.AssistantDetailVM
import dev.emyth.app.ui.pages.backup.BackupVM
import dev.emyth.app.ui.pages.chat.ChatDrawerVM
import dev.emyth.app.ui.pages.chat.ChatVM
import dev.emyth.app.ui.pages.debug.DebugVM
import dev.emyth.app.ui.pages.developer.DeveloperVM
import dev.emyth.app.ui.pages.favorite.FavoriteVM
import dev.emyth.app.ui.pages.health.HealthVM
import dev.emyth.app.ui.pages.memory.MemoryBankVM
import dev.emyth.app.ui.pages.miniapp.MiniAppViewModel
import dev.emyth.app.ui.pages.search.SearchVM
import dev.emyth.app.ui.pages.history.HistoryVM
import dev.emyth.app.ui.pages.stats.StatsVM
import dev.emyth.app.ui.pages.imggen.ImgGenVM
import dev.emyth.app.ui.pages.extensions.PromptVM
import dev.emyth.app.ui.pages.extensions.ExternalMemoriesVM
import dev.emyth.app.ui.pages.extensions.QuickMessagesVM
import dev.emyth.app.ui.pages.extensions.SkillDetailVM
import dev.emyth.app.ui.pages.extensions.SkillsVM
import dev.emyth.app.ui.pages.extensions.workspace.WorkspaceDetailVM
import dev.emyth.app.ui.pages.extensions.workspace.WorkspaceVM
import dev.emyth.app.ui.pages.security.SecurityAuditVM
import dev.emyth.app.ui.pages.setting.SettingVM
import dev.emyth.app.ui.pages.share.handler.ShareHandlerVM
import dev.emyth.app.ui.pages.translator.TranslatorVM
import dev.emyth.app.workflow.ui.WorkflowsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::DeveloperVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::ExternalMemoriesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> { WorkspaceDetailVM(id = it.get(), repository = get()) }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
    viewModelOf(::HealthVM)
    viewModelOf(::MemoryBankVM)
    viewModelOf(::MiniAppViewModel)
    viewModelOf(::WorkflowsViewModel)
    viewModelOf(::SecurityAuditVM)
}
