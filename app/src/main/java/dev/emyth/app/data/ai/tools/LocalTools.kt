/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.ai.tools
 
import android.content.Context
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import dev.emyth.app.data.event.AppEvent
import dev.emyth.app.data.ai.mcp.McpCommonOptions
import dev.emyth.app.data.ai.mcp.McpManager
import dev.emyth.app.data.ai.mcp.McpServerConfig
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.getCurrentAssistant
import dev.emyth.app.data.event.AppEventBus
import dev.emyth.app.service.VoiceCallService
import dev.emyth.app.utils.readClipboardText
import dev.emyth.app.utils.writeClipboardText
import org.koin.java.KoinJavaComponent.getKoin
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
 
@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()
 
    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()
 
    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()
 
    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()
 
    /**
     * AI 主动发起语音通话 (聊着聊着想打就打).
     * 开启后 AI 可在合适时机调用 request_voice_call, 弹出来电界面邀请用户接听.
     */
    @Serializable
    @SerialName("request_voice_call")
    data object RequestVoiceCall : LocalToolOption()
 
    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()
 
    /**
     * 已废弃: 本地短信工具与系统工具(SystemToolOption.Sms)都注册成同名 read_sms,
     * 同时启用会让某些模型因同名工具报错发不出消息。短信读取统一改由系统工具侧提供
     * (设置 → 系统工具 → 启用短信读取工具)。
     *
     * 保留此 sealed 子类仅为向后兼容: 存量助手的 localTools JSON 里可能含 {"type":"sms"},
     * 若删除会导致 Settings 反序列化失败使应用无法启动。它不再出现在 UI, 也不再注册工具。
     */
    @Serializable
    @SerialName("sms")
    data object Sms : LocalToolOption()
 
    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()
 
    @Serializable
    @SerialName("web_fetch")
    data object WebFetch : LocalToolOption()
 
    @Serializable
    @SerialName("list_zip_contents")
    data object ListZipContents : LocalToolOption()
 
    /**
     * 已废弃: check_token_usage 工具是个空壳(只返回占位字符串),
     * 已完整删除工具实现和UI卡片。保留此 sealed 子类仅为向后兼容:
     * 存量助手的 localTools JSON 里可能含 {"type":"check_token_usage"},
     * 若删除会导致 Settings 反序列化失败使应用无法启动。它不再出现在 UI, 也不再注册工具。
     */
    @Serializable
    @SerialName("check_token_usage")
    data object CheckTokenUsage : LocalToolOption()
 
    @Serializable
    @SerialName("allow_skip_reply")
    data object AllowSkipReply : LocalToolOption()

    /**
     * 工作流 (Tasker 风格自动化). 开启后向 AI 注册 7 个 workflow_* 工具,
     * AI 可创建/管理事件驱动的工作流 (触发器 + 条件 -> 动作).
     */
    @Serializable
    @SerialName("workflows")
    data object Workflows : LocalToolOption()

    /**
     * 屏幕自动化. 开启后注册 tap/long_press/swipe/scroll/read_window_tree/find_node/
     * click_node/set_text/global_action/take_screenshot 等无障碍服务工具, AI 可控制屏幕.
     * 需用户在系统设置->无障碍中启用Emyth.
     */
    @Serializable
    @SerialName("screen_automation")
    data object ScreenAutomation : LocalToolOption()

    /**
     * SSH 远程连接. 开启后注册 ssh_exec/ssh_exec_saved/save_ssh_host/list_ssh_hosts/
     * delete_ssh_host/ssh_upload/ssh_download/ssh_forget_host_key 工具, AI 可远程执行命令和传文件.
     */
    @Serializable
    @SerialName("ssh")
    data object Ssh : LocalToolOption()

    /**
     * 系统配置. 开启后注册 configure_mcp 工具, AI 可帮用户把 MCP 服务器
     * 直接配置进Emyth(写配置 + 连接), 无需用户手动添加.
     */
    @Serializable
    @SerialName("configure_mcp")
    data object Configure : LocalToolOption()
}
 
class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val workflowRepository: dev.emyth.app.workflow.repository.WorkflowRepository,
    private val workflowEngine: dev.emyth.app.workflow.execution.WorkflowEngine,
    private val sshHostRepository: dev.emyth.app.data.repository.SshHostRepository,
) {
    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            needsApproval = true,
            description = """
                Execute JavaScript code using QuickJS engine (ES2020).
                The result is the value of the last expression in the code.
                For calculations with decimals, use toFixed() to control precision.
                Console output (log/info/warn/error) is captured and returned in 'logs' field.
                No DOM or Node.js APIs available.
                Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                    required = listOf("code")
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val context = QuickJSContext.create()
                context.setConsole(object : QuickJSContext.Console {
                    override fun log(info: String?) {
                        logs.add("[LOG] $info")
                    }
 
                    override fun info(info: String?) {
                        logs.add("[INFO] $info")
                    }
 
                    override fun warn(info: String?) {
                        logs.add("[WARN] $info")
                    }
 
                    override fun error(info: String?) {
                        logs.add("[ERROR] $info")
                    }
                })
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                val result = context.evaluate(code)
                val payload = buildJsonObject {
                    if (logs.isNotEmpty()) {
                        put("logs", JsonPrimitive(logs.joinToString("\n")))
                    }
                    put(
                        key = "result",
                        element = when (result) {
                            null -> JsonNull
                            is QuickJSObject -> JsonPrimitive(result.stringify())
                            else -> JsonPrimitive(result.toString())
                        }
                    )
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }
 
    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = """
                Get the current local date and time info from the device.
                Returns year/month/day, weekday, ISO date/time strings, timezone, and timestamp.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val time = now.toLocalTime().withNano(0)
                val weekday = now.dayOfWeek
                val payload = buildJsonObject {
                    put("year", date.year)
                    put("month", date.monthValue)
                    put("day", date.dayOfMonth)
                    put("weekday", weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("weekday_index", weekday.value)
                    put("date", date.toString())
                    put("time", time.toString())
                    put("datetime", now.withNano(0).toString())
                    put("timezone", now.zone.id)
                    put("utc_offset", now.offset.id)
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }
 
    val clipboardTool by lazy {
        Tool(
            name = "clipboard_tool",
            description = """
                Read or write plain text from the device clipboard.
                Use action: read or write. For write, provide text.
                Do NOT write to the clipboard unless the user has explicitly requested it.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add("read")
                                    add("write")
                                }
                            )
                            put("description", "Operation to perform: read or write")
                        })
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Text to write to the clipboard (required for write)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
                    "read" -> {
                        val payload = buildJsonObject {
                            put("text", context.readClipboardText())
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }
 
                    "write" -> {
                        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                        context.writeClipboardText(text)
                        val payload = buildJsonObject {
                            put("success", true)
                            put("text", text)
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }
 
                    else -> error("unknown action: $action, must be one of [read, write]")
                }
            }
        )
    }
 
    val ttsTool by lazy {
        Tool(
            name = "text_to_speech",
            description = """
                Speak text aloud to the user using the device's text-to-speech engine.
                Use this when the user asks you to read something aloud, or when audio output is appropriate.
                The tool returns immediately; audio plays in the background on the device.
                Provide natural, readable text without markdown formatting.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to speak aloud")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = {
                val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: error("text is required")
                eventBus.emit(AppEvent.Speak(text))
                val payload = buildJsonObject {
                    put("success", true)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }
 
    val askUserTool by lazy {
        Tool(
            name = "ask_user",
            description = """
                Ask the user one or more questions when you need clarification, additional information, or confirmation.
                Each question can optionally provide a list of suggested options for the user to choose from.
                The user may select an option or provide their own free-text answer for each question.
                The answers will be returned as a JSON object mapping question IDs to the user's responses.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("questions", buildJsonObject {
                            put("type", "array")
                            put("description", "List of questions to ask the user")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Unique identifier for this question")
                                    })
                                    put("question", buildJsonObject {
                                        put("type", "string")
                                        put("description", "The question text to display to the user")
                                    })
                                    put("options", buildJsonObject {
                                        put("type", "array")
                                        put(
                                            "description",
                                            "Optional list of suggested options for the user to choose from"
                                        )
                                        put("items", buildJsonObject {
                                            put("type", "string")
                                        })
                                    })
                                    put("selection_type", buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            kotlinx.serialization.json.buildJsonArray {
                                                add("text")
                                                add("single")
                                                add("multi")
                                            }
                                        )
                                        put(
                                            "description",
                                            "Answer type: text (free text input, default), single (select exactly one option), multi (select one or more options)"
                                        )
                                    })
                                })
                                put("required", kotlinx.serialization.json.buildJsonArray {
                                    add("id")
                                    add("question")
                                })
                            })
                        })
                    },
                    required = listOf("questions")
                )
            },
            needsApproval = true,
            execute = {
                error("ask_user tool should be handled by HITL flow")
            }
        )
    }
 
    /**
     * AI 主动发起语音通话工具.
     * conversationId 在工具构建时闭包捕获 (execute 拿不到上下文),
     * 由 ChatService 在组装工具列表时传入当前会话 id.
     */
    private fun createRequestVoiceCallTool(conversationId: String) = Tool(
        name = "request_voice_call",
        description = """
            Proactively initiate a voice call to the user, like making a phone call.
            The user will see an incoming-call screen and can choose to answer or decline.
            Use this when a voice conversation would be more natural than text — for example
            when the topic is emotional, complex, or the user seems to want real-time back-and-forth.
            Do NOT overuse this; typically at most once per conversation unless the user asks.
            The tool returns whether the call was successfully requested (does not indicate whether the user answered).
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("reason", buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            "A short reason why a voice call is being initiated now (shown briefly to the user). Optional."
                        )
                    })
                },
                required = emptyList()
            )
        },
        execute = {
            // 单通话守卫: 已有通话进行中就不重复发起
            val active = VoiceCallService.activeConversationId.value
            if (active != null) {
                val payload = buildJsonObject {
                    put("success", false)
                    put("reason", "a voice call is already in progress")
                }
                return@Tool listOf(UIMessagePart.Text(payload.toString()))
            }
            eventBus.emit(AppEvent.RequestVoiceCall(conversationId))
            val payload = buildJsonObject {
                put("success", true)
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    )
 
    /** AI 主动挂断当前语音通话 */
    private fun createEndVoiceCallTool() = Tool(
        name = "end_voice_call",
        description = "Hang up the current voice call. Call this when you want to proactively end the ongoing voice conversation (for example, the user is done, or you need to stop). Returns success/failure.",
        parameters = { InputSchema.Obj(properties = buildJsonObject { }, required = emptyList()) },
        execute = {
            val active = VoiceCallService.activeConversationId.value
            if (active == null) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("reason", "no voice call in progress")
                }.toString()))
            }
            VoiceCallService.stop(context)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
            }.toString()))
        }
    )

    val webFetchTool by lazy {
        Tool(
            name = "web_fetch",
            description = "Fetch the content of a web page and return clean readable text. Automatically strips HTML tags, scripts, styles and extracts the main text content. Useful for reading articles, news, documentation, or any web page. Supports timeout and max length limits.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("url", buildJsonObject { put("type", "string"); put("description", "URL to fetch") })
                        put("max_length", buildJsonObject { put("type", "integer"); put("description", "Max content length in chars (default 10000)") })
                        put("raw", buildJsonObject { put("type", "boolean"); put("description", "If true, return raw HTML instead of cleaned text (default false)") })
                    },
                    required = listOf("url")
                )
            },
            execute = {
                val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
                val maxLen = it.jsonObject["max_length"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10000
                val raw = it.jsonObject["raw"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; AI Assistant)")
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,text/plain,application/json,*/*")
                    connection.instanceFollowRedirects = true
                    val code = connection.responseCode
                    val body = if (code in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    }
                    val content = if (raw || !body.contains("<")) body else HtmlToText.convert(body)
                    val truncated = if (content.length > maxLen) content.take(maxLen) + "...[truncated]" else content
                    listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", code in 200..299); put("status_code", code); put("url", url)
                        put("content", truncated); put("content_length", content.length)
                    }.toString()))
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text(buildJsonObject { put("success", false); put("error", e.message ?: "") }.toString()))
                }
            }
        )
    }
 
    val listZipContentsTool by lazy {
        Tool(
            name = "list_zip_contents",
            description = "List the contents of a ZIP file. Returns file names, sizes, and compressed sizes. Supports local file paths or content URIs.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("path", buildJsonObject { put("type", "string"); put("description", "File path or content URI of the ZIP file") })
                    },
                    required = listOf("path")
                )
            },
            execute = {
                val path = it.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path is required")
                try {
                    val inputStream = if (path.startsWith("content://")) {
                        context.contentResolver.openInputStream(android.net.Uri.parse(path))
                    } else {
                        java.io.FileInputStream(path)
                    }
                    inputStream?.use { stream ->
                        java.util.zip.ZipInputStream(stream).use { zis ->
                            val entries = mutableListOf<Map<String, Any>>()
                            var entry = zis.nextEntry
                            while (entry != null) {
                                entries.add(mapOf("name" to entry.name, "size" to entry.size, "compressed_size" to entry.compressedSize))
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                            listOf(UIMessagePart.Text(buildJsonObject {
                                put("success", true); put("count", entries.size)
                                put("entries", kotlinx.serialization.json.buildJsonArray {
                                    entries.forEach { e ->
                                        add(buildJsonObject {
                                            put("name", e["name"].toString())
                                            put("size", e["size"] as Long)
                                            put("compressed_size", e["compressed_size"] as Long)
                                        })
                                    }
                                })
                            }.toString()))
                        }
                    } ?: listOf(UIMessagePart.Text(buildJsonObject { put("success", false); put("error", "Cannot open file") }.toString()))
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text(buildJsonObject { put("success", false); put("error", e.message ?: "") }.toString()))
                }
            }
        )
    }
 
    fun getTools(
        options: List<LocalToolOption>,
        conversationId: String? = null,
    ): List<Tool> = buildTools(options, conversationId, ToolInvocationContext.EMPTY)

    /**
     * 工具调用上下文重载 - 供工作流引擎等无头调用方使用. 在基础工具之上额外注册
     * workflow_* 工具 (当助手开启了 Workflows 选项时), 以便 AI 能创建/管理工作流.
     */
    fun getTools(
        options: List<LocalToolOption>,
        invocationContext: ToolInvocationContext,
    ): List<Tool> {
        val tools = buildTools(options, invocationContext.callerConversationId, invocationContext)
        if (options.contains(LocalToolOption.Workflows)) {
            // knownToolNamesProvider 返回空集 = 跳过创建时的工具名校验, 改在触发时由
            // ToolSurfaceBuilder 构建的完整工具面 (含 system/MCP/plugin) 校验. 这样 AI
            // 能在动作里引用 post_notification/set_torch/mcp_*/plg_* 等非本地工具.
            tools.add(dev.emyth.app.workflow.tools.workflowCreateTool(
                workflowRepository,
                knownToolNamesProvider = { emptyList() },
                callerContext = invocationContext,
            ))
            tools.add(dev.emyth.app.workflow.tools.workflowListTool(workflowRepository))
            tools.add(dev.emyth.app.workflow.tools.workflowGetTool(workflowRepository))
            tools.add(dev.emyth.app.workflow.tools.workflowUpdateTool(
                workflowRepository,
                knownToolNamesProvider = { emptyList() },
                callerContext = invocationContext,
            ))
            tools.add(dev.emyth.app.workflow.tools.workflowDeleteTool(workflowRepository))
            tools.add(dev.emyth.app.workflow.tools.workflowSetEnabledTool(workflowRepository))
            tools.add(dev.emyth.app.workflow.tools.workflowRunTool(workflowEngine, workflowRepository))
        }
        return tools
    }

    /**
     * 配置Emyth里的 MCP 服务器（帮用户直接添加好，无需手动操作）。
     */
    private fun createConfigureTool() = Tool(
        name = "configure",
        needsApproval = true,
        description = "配置Emyth里的 MCP 服务器：action=add 添加(name/url/type/token)，action=remove 删除(name)，action=list 列出已配置的 MCP。url 形如 http://120.76.178.0:8877/mcp。type 默认 streamable_http，可选 sse。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("description", "add / remove / list")
                    })
                    put("name", buildJsonObject {
                        put("type", "string")
                        put("description", "MCP 名称")
                    })
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "MCP 地址")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("description", "streamable_http 或 sse，默认 streamable_http")
                    })
                    put("token", buildJsonObject {
                        put("type", "string")
                        put("description", "可选，鉴权 token（自动加到 Authorization: Bearer）")
                    })
                },
                required = listOf("action")
            )
        },
        execute = { args ->
            val params = args.jsonObject
            val action = params["action"]?.jsonPrimitive?.contentOrNull ?: "list"
            val store = getKoin().get<SettingsStore>()
            val mcpManager = getKoin().get<McpManager>()
            when (action) {
                "add" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val url = params["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (url.isBlank()) {
                        return@Tool listOf(UIMessagePart.Text("""{"success":false,"error":"url 不能为空"}"""))
                    }
                    val type = params["type"]?.jsonPrimitive?.contentOrNull ?: "streamable_http"
                    val token = params["token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val headers = if (token.isNotBlank()) listOf("Authorization" to "Bearer $token") else emptyList()
                    val common = McpCommonOptions(name = name.ifBlank { url }, headers = headers)
                    val config: McpServerConfig = if (type == "sse") {
                        McpServerConfig.SseTransportServer(commonOptions = common, url = url)
                    } else {
                        McpServerConfig.StreamableHTTPServer(commonOptions = common, url = url)
                    }
                    store.update { st ->
                        st.copy(mcpServers = st.mcpServers.filterNot { it.commonOptions.name == common.name } + config)
                    }
                    mcpManager.addClient(config)
                    listOf(UIMessagePart.Text("""{"success":true,"name":"${common.name}","url":"$url","type":"$type"}"""))
                }
                "remove" -> {
                    val name = params["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val targets = store.settingsFlow.value.mcpServers.filter { it.commonOptions.name == name }
                    store.update { st -> st.copy(mcpServers = st.mcpServers.filterNot { it.commonOptions.name == name }) }
                    targets.forEach { mcpManager.removeClient(it) }
                    listOf(UIMessagePart.Text("""{"success":true,"removed":${targets.size}}"""))
                }
                else -> {
                    val servers = store.settingsFlow.value.mcpServers
                    val info = if (servers.isEmpty()) "未配置任何 MCP" else servers.joinToString("\n") { c ->
                        val u = when (c) {
                            is McpServerConfig.SseTransportServer -> c.url
                            is McpServerConfig.StreamableHTTPServer -> c.url
                        }
                        "- ${c.commonOptions.name} → $u"
                    }
                    listOf(UIMessagePart.Text(info))
                }
            }
        }
    )


    private fun createDeleteAssistantTool() = Tool(
        name = "delete_assistant",
        needsApproval = true,
        description = "删除一个 AI 助手。需要先开启「允许助手删除其他助手」的信任开关。不能删除当前正在使用的助手（自己）。",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("assistant_id", buildJsonObject {
                        put("type", "string")
                        put("description", "要删除的助手 ID 或名字")
                    })
                },
                required = listOf("assistant_id")
            )
        },
        execute = { args ->
            val params = args.jsonObject
            val query = params["assistant_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val store = getKoin().get<SettingsStore>()
            val settings = store.settingsFlow.value
            if (!settings.allowAssistantDelete) {
                return@Tool listOf(UIMessagePart.Text("""{"success":false,"error":"未开启「允许助手删除其他助手」的信任开关"}"""))
            }
            val current = settings.getCurrentAssistant()
            val target = settings.assistants.find {
                it.id.toString() == query || it.name == query
            }
            if (target == null) {
                return@Tool listOf(UIMessagePart.Text("""{"success":false,"error":"未找到该助手"}"""))
            }
            if (target.id == current.id) {
                return@Tool listOf(UIMessagePart.Text("""{"success":false,"error":"不能删除当前正在使用的助手（自己）"}"""))
            }
            store.update { st ->
                st.copy(assistants = st.assistants.filter { it.id != target.id })
            }
            listOf(UIMessagePart.Text("""{"success":true,"deleted":"${target.name}"}"""))
        }
    )

    private fun buildTools(
        options: List<LocalToolOption>,
        conversationId: String? = null,
        invocationContext: ToolInvocationContext = ToolInvocationContext.EMPTY,
    ): MutableList<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.RequestVoiceCall) && conversationId != null) {
            tools.add(createRequestVoiceCallTool(conversationId))
            tools.add(createEndVoiceCallTool())
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        // 注: 本地短信工具已废弃, 与系统工具同名冲突。改由系统工具侧提供。
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(createCalendarTool(context))
        }
        if (options.contains(LocalToolOption.WebFetch)) {
            tools.add(webFetchTool)
        }
        if (options.contains(LocalToolOption.ListZipContents)) {
            tools.add(listZipContentsTool)
        }
        if (options.contains(LocalToolOption.ScreenAutomation)) {
            // 屏幕自动化工具 - 需无障碍服务. wake_screen 已由 SystemTools 提供, 不重复注册.
            tools.add(dev.emyth.app.data.ai.tools.local.tapTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.longPressTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.swipeTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.readWindowTreeTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.findNodeTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.clickNodeTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.setTextTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.scrollTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.globalActionTool(invocationContext))
            tools.add(dev.emyth.app.data.ai.tools.local.takeScreenshotTool(context))
        }
        if (options.contains(LocalToolOption.Configure)) {
            tools.add(createConfigureTool())
            tools.add(createDeleteAssistantTool())
        }
        if (options.contains(LocalToolOption.Ssh)) {
            tools.add(dev.emyth.app.data.ai.tools.local.sshExecTool(context))
            tools.add(dev.emyth.app.data.ai.tools.local.saveSshHostTool(sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.listSshHostsTool(sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.deleteSshHostTool(sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.sshExecSavedTool(context, sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.sshUploadTool(context, sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.sshDownloadTool(context, sshHostRepository))
            tools.add(dev.emyth.app.data.ai.tools.local.forgetSshHostKeyTool(context))
        }
        return tools
    }
}