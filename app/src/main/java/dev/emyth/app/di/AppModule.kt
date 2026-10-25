/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.di

import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import dev.emyth.app.AppScope
import dev.emyth.app.data.ai.AILoggingManager
import dev.emyth.app.data.ai.tools.LocalTools
import dev.emyth.app.data.event.AppEventBus
import dev.emyth.app.data.service.MemoryBankService
import dev.emyth.app.service.ChatService
import dev.emyth.app.utils.EmojiData
import dev.emyth.app.utils.EmojiUtils
import dev.emyth.app.utils.JsonInstant
import dev.emyth.app.utils.SoundEffectPlayer
import dev.emyth.app.utils.UpdateChecker
import dev.emyth.app.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter(get())
    }

    single {
        AppEventBus()
    }

    // Workflows: AgentRun ledger (no-op stub), repository, engine, trigger registry.
    single { dev.emyth.app.data.agentrun.AgentRunRepository() }
    single {
        dev.emyth.app.data.repository.SshHostRepository(
            get<dev.emyth.app.data.db.AppDatabase>().sshHostDao()
        )
    }
    single {
        dev.emyth.app.workflow.repository.WorkflowRepository(
            workflowDao = get<dev.emyth.app.data.db.AppDatabase>().workflowDao(),
            workflowRunDao = get<dev.emyth.app.data.db.AppDatabase>().workflowRunDao(),
        )
    }
    single { dev.emyth.app.workflow.condition.ContextProvider(get(), get(), get()) }
    single { dev.emyth.app.workflow.execution.WorkflowActionRunner() }
    single {
        dev.emyth.app.workflow.execution.WorkflowEngine(
            repository = get(),
            settingsStore = get(),
            contextProvider = get(),
            actionRunner = get(),
            auditRepo = get(),
        ).also { engine ->
            get<dev.emyth.app.workflow.repository.WorkflowRepository>().bindEngine(engine)
        }
    }
    single {
        dev.emyth.app.workflow.trigger.TriggerRegistry(
            context = get(),
            appScope = get(),
            workflowRepository = get(),
        )
    }

    single {
        LocalTools(get(), get(), get(), get(), get())
    }

    // 微信 Bot (iLink 协议) HTTP 客户端
    single { dev.emyth.app.data.weixin.WeixinBotClient(get()) }

    // QQ Bot (API v2) HTTP 客户端 + WebSocket 共用同一个 OkHttpClient
    single { dev.emyth.app.data.qq.QqBotClient(get()) }

    single {
        dev.emyth.app.data.ai.tools.ToolSurfaceBuilder(
            context = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            pluginToolProvider = get(),
            workspaceRepository = get(),
            json = get(),
            memoryRepository = get(),
        )
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        AILoggingManager()
    }

    single {
        MemoryBankService(
            memoryBankDAO = get(),
            okHttpClient = get(),
            context = get()
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            pluginToolProvider = get(),
            pluginLoader = get(),
            workspaceRepository = get(),
            memoryBankService = get(),
            folderRepository = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }

}
