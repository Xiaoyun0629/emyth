/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.Json
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import kotlinx.serialization.json.jsonObject
import dev.emyth.app.data.ai.mcp.McpManager
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.data.files.FilesManager
import dev.emyth.app.data.files.SkillManager
import dev.emyth.app.data.repository.MemoryRepository
import dev.emyth.app.data.repository.WorkspaceRepository
import dev.emyth.app.plugin.provider.PluginToolProvider

/**
 * Builds the full tool surface for an assistant: search + local + system + workspace + skill
 * + MCP + plugin tools. This is the single source of truth shared by [dev.emyth.app.service.ChatService]
 * (interactive) and [dev.emyth.app.workflow.execution.WorkflowEngine] (headless fire), so a
 * workflow action can reference any tool the assistant actually has registered - not just the
 * local-tool subset. Without this, workflow_create would reject system/MCP/plugin tool names as
 * "unknown_tool" and the engine couldn't execute them at fire time.
 *
 * Headless callers (workflow fire) pass an empty [recentMessages] list and a null/assistant-default
 * [workspaceCwd]; the read-only context tools that consult recent messages simply see no history.
 */
class ToolSurfaceBuilder(
    private val context: Context,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val pluginToolProvider: PluginToolProvider,
    private val workspaceRepository: WorkspaceRepository,
    private val json: Json,
    private val memoryRepository: MemoryRepository,
) {
    suspend fun build(
        assistant: dev.emyth.app.data.model.Assistant,
        settings: Settings,
        invocationContext: ToolInvocationContext,
        recentMessages: List<UIMessage> = emptyList(),
        workspaceCwd: String? = null,
    ): List<Tool> = buildList {
        // Memory tools - mirror GenerationHandler: only when the assistant has memory enabled.
        if (assistant.enableMemory) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            addAll(buildMemoryTools(
                json = json,
                onCreation = { content -> memoryRepository.addMemory(memoryAssistantId, content) },
                onUpdate = { id, content -> memoryRepository.updateContent(id, content) },
                onDelete = { id -> memoryRepository.deleteMemory(id) },
            ))
        }
        if (settings.enableWebSearch) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools, invocationContext))
        val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions()
        if (systemToolsOptions.isNotEmpty()) {
            addAll(SystemTools(context, settings).getTools(systemToolsOptions, recentMessages, filesManager))
        }
        addAll(createWorkspaceTools(assistant.workspaceId?.toString(), workspaceRepository, workspaceCwd))
        if (assistant.enabledSkills.isNotEmpty()) {
            addAll(createSkillTools(assistant.enabledSkills, skillManager.listSkills(), skillManager))
        }
        mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
            add(
                Tool(
                    name = ToolNaming.buildMcpToolName(serverId, tool.name),
                    description = tool.description ?: "",
                    parameters = { tool.inputSchema },
                    needsApproval = tool.needsApproval,
                    execute = {
                        mcpManager.callTool(serverId, tool.name, it.jsonObject)
                    },
                )
            )
        }
        addAll(pluginToolProvider.getTools())
    }
}
