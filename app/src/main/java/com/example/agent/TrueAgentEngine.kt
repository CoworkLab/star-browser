package com.example.agent

import com.example.browser.DomBridge
import com.example.sandbox.MobileSandboxManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

interface AgentHostBridge {
    fun currentUrl(): String
    fun currentTitle(): String
    suspend fun navigateAndWait(url: String, newTab: Boolean = false): Result<String>
    suspend fun goBackAndWait(): Result<String>
    suspend fun goForwardAndWait(): Result<String>
    suspend fun reloadAndWait(): Result<String>
    suspend fun evaluateDomJs(script: String): Result<String>
    suspend fun captureViewportBase64(): String?
    fun copyToClipboard(text: String)
}

data class ToolCallStarted(
    val callId: String,
    val toolName: String,
    val argumentsJson: String
)

data class AgentRunResult(
    val finalText: String,
    val reasoning: String?,
    val citations: List<CitationItem>,
    val executedTools: List<ToolExecutionRecord>
)

class TrueAgentEngine(
    private val brain: LlmBrain,
    private val host: AgentHostBridge,
    private val sandbox: MobileSandboxManager,
    private val mcpClient: McpConnectorClient = McpConnectorClient(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val MAX_TOOL_STEPS = 10
    }

    suspend fun run(
        userPrompt: String,
        history: List<Pair<String, String>>,
        provider: LlmProviderConfig,
        systemPrompt: SystemPromptPreset,
        activeSkills: List<AgentSkill>,
        mode: ExecutionMode,
        enableReasoning: Boolean,
        enabledToolkits: Set<ToolkitId>,
        mcpConnectors: List<McpConnectorConfig>,
        sandboxInternetEnabled: Boolean,
        attachedImageBase64: String? = null,
        attachedSandboxFiles: List<String> = emptyList(),
        requestApproval: suspend (PendingApprovalAction) -> Boolean = { true },
        requestApprovalDecision: (suspend (PendingApprovalAction) -> ApprovalDecision)? = null,
        onToolStarted: suspend (ToolCallStarted) -> Unit = {},
        onToolAwaitingApproval: suspend (PendingApprovalAction) -> Unit = {},
        onStepExecuted: suspend (ToolExecutionRecord) -> Unit = {},
        onStreamDelta: (textDelta: String, reasoningDelta: String) -> Unit = { _, _ -> },
        onTurnResetStream: () -> Unit = {}
    ): Result<AgentRunResult> = try {
        val curUrl = host.currentUrl()
        val hasActiveWebpage = curUrl.isNotBlank() && !curUrl.startsWith("about:")
        val availableTools = ToolRegistry.selectToolsForTurn(
            userPrompt = userPrompt,
            hasActiveWebpage = hasActiveWebpage,
            hasGrabbedPage = userPrompt.contains("[Attached Page Snapshot:"),
            hasAttachments = attachedImageBase64 != null || attachedSandboxFiles.isNotEmpty(),
            mode = mode,
            enabledToolkits = enabledToolkits,
            mcpConnectors = mcpConnectors,
            sandboxInternetEnabled = sandboxInternetEnabled
        )

        val sysPrompt = buildSystemPrompt(
            basePrompt = systemPrompt.prompt,
            userPrompt = userPrompt,
            activeSkills = activeSkills,
            mode = mode,
            availableTools = availableTools,
            curUrl = if (hasActiveWebpage) curUrl else null,
            curTitle = if (hasActiveWebpage) host.currentTitle() else null
        )

        val messages = mutableListOf<BrainMessage>(BrainMessage.System(sysPrompt))
        history.takeLast(8).forEach { (role, text) ->
            when (role) {
                "user" -> messages.add(BrainMessage.User(text))
                "assistant" -> messages.add(BrainMessage.AssistantText(text))
            }
        }

        val attachedContext = buildAttachedContext(attachedSandboxFiles)
        val initialUserText = buildString {
            append(userPrompt.trim())
            if (attachedContext.isNotBlank()) {
                append("\n\n").append(attachedContext)
            }
        }
        val visionImage = if (provider.supportsVision) attachedImageBase64 else null
        messages.add(BrainMessage.User(initialUserText, visionImage))

        val records = mutableListOf<ToolExecutionRecord>()
        val aggregatedReasoning = StringBuilder()
        val sessionApprovedTools = mutableSetOf<String>()
        var protocolCorrectionUsed = false

        val effectiveApproval: suspend (PendingApprovalAction) -> ApprovalDecision = { action ->
            if (requestApprovalDecision != null) {
                requestApprovalDecision(action)
            } else if (requestApproval(action)) {
                ApprovalDecision.APPROVE_ONCE
            } else {
                ApprovalDecision.DENY
            }
        }

        for (step in 0 until MAX_TOOL_STEPS) {
            onTurnResetStream()
            val turnOutput = brain.runAgentTurnStreaming(
                provider = provider,
                messages = messages,
                tools = availableTools,
                enableReasoning = enableReasoning,
                onStreamDelta = onStreamDelta
            ).getOrThrow()

            when (turnOutput) {
                is BrainTurnOutput.ToolCalls -> {
                    onTurnResetStream()
                    if (!turnOutput.reasoning.isNullOrBlank()) {
                        aggregatedReasoning.append(turnOutput.reasoning).append("\n")
                    }
                    messages.add(
                        BrainMessage.AssistantToolCalls(
                            calls = turnOutput.calls,
                            text = turnOutput.assistantText,
                            reasoning = turnOutput.reasoning
                        )
                    )

                    for (call in turnOutput.calls) {
                        val validation = ToolRegistry.validateToolCall(
                            callId = call.id,
                            rawName = call.name,
                            arguments = call.arguments,
                            availableTools = availableTools
                        )
                        val record = when (validation) {
                            is ToolValidationResult.Invalid -> {
                                onToolStarted(
                                    ToolCallStarted(
                                        callId = call.id,
                                        toolName = validation.canonicalName,
                                        argumentsJson = call.arguments.toString()
                                    )
                                )
                                ToolExecutionRecord(
                                    callId = call.id,
                                    toolName = validation.canonicalName,
                                    argumentsJson = call.arguments.toString(),
                                    success = false,
                                    timedOut = false,
                                    outputOrError = validation.reason,
                                    durationMs = 0L
                                )
                            }
                            is ToolValidationResult.Valid -> {
                                onToolStarted(
                                    ToolCallStarted(
                                        callId = call.id,
                                        toolName = validation.call.canonicalName,
                                        argumentsJson = validation.call.arguments.toString()
                                    )
                                )
                                executeValidatedCall(
                                    validated = validation.call,
                                    mode = mode,
                                    sessionApprovedTools = sessionApprovedTools,
                                    mcpConnectors = mcpConnectors,
                                    sandboxInternetEnabled = sandboxInternetEnabled,
                                    onAwaitingApproval = onToolAwaitingApproval,
                                    requestApproval = effectiveApproval
                                )
                            }
                        }
                        records.add(record)
                        onStepExecuted(record)

                        val toolPayload = JSONObject().apply {
                            put("ok", record.success)
                            put("tool", record.toolName)
                            put("timed_out", record.timedOut)
                            if (record.skippedByUser) {
                                put("denied_by_user", true)
                            }
                            if (record.success) {
                                put("result", record.outputOrError)
                            } else {
                                put("error", record.outputOrError)
                            }
                        }.toString()

                        messages.add(
                            BrainMessage.ToolResult(
                                callId = call.id,
                                toolName = record.toolNameWireOrCanonical(availableTools),
                                resultJson = toolPayload
                            )
                        )
                    }
                }

                is BrainTurnOutput.FinalText -> {
                    if (!turnOutput.reasoning.isNullOrBlank()) {
                        aggregatedReasoning.append(turnOutput.reasoning).append("\n")
                    }
                    val reply = turnOutput.text.trim()

                    if (FakeToolCallDetector.containsFakeToolCallJson(reply)) {
                        if (!protocolCorrectionUsed && availableTools.isNotEmpty()) {
                            protocolCorrectionUsed = true
                            messages.add(BrainMessage.AssistantText(reply))
                            messages.add(
                                BrainMessage.User(
                                    "Protocol violation: You wrote a tool call as plain text JSON instead of invoking a structured tool call. Do not print tool JSON as text. Either invoke the real structured tool now, or explain honestly that the action was not executed."
                                )
                            )
                            continue
                        }
                        val safeFailure = "The model emitted an unexecuted tool-call block as plain text instead of running a verified tool. No action was executed."
                        return Result.success(
                            AgentRunResult(
                                finalText = safeFailure,
                                reasoning = aggregatedReasoning.toString().trim().ifBlank { null },
                                citations = emptyList(),
                                executedTools = records
                            )
                        )
                    }

                    if (FakeToolCallDetector.claimsSuccessDespiteFailures(reply, records)) {
                        val lastErr = records.lastOrNull { !it.success }?.outputOrError ?: "Tool execution failed."
                        val truthful = "Action did not complete: $lastErr"
                        return Result.success(
                            AgentRunResult(
                                finalText = truthful,
                                reasoning = aggregatedReasoning.toString().trim().ifBlank { null },
                                citations = extractCitations(truthful),
                                executedTools = records
                            )
                        )
                    }

                    return Result.success(
                        AgentRunResult(
                            finalText = reply.ifBlank { "Completed." },
                            reasoning = aggregatedReasoning.toString().trim().ifBlank { null },
                            citations = extractCitations(reply),
                            executedTools = records
                        )
                    )
                }
            }
        }

        val fallbackMsg = if (records.any { !it.success }) {
            val lastErr = records.last { !it.success }.outputOrError
            "Stopped after reaching maximum tool steps. Last error: $lastErr"
        } else {
            "Stopped after reaching the maximum of $MAX_TOOL_STEPS tool steps."
        }
        Result.success(
            AgentRunResult(
                finalText = fallbackMsg,
                reasoning = aggregatedReasoning.toString().trim().ifBlank { null },
                citations = emptyList(),
                executedTools = records
            )
        )
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun requiresHumanApproval(
        def: ToolDefinition,
        mode: ExecutionMode,
        sessionApprovedTools: Set<String>
    ): Boolean {
        if (def.canonicalName in sessionApprovedTools) return false
        return when (mode) {
            ExecutionMode.ASK -> false
            ExecutionMode.APPROVE -> def.isMutating || def.riskLevel == ToolRiskLevel.DESTRUCTIVE_OR_EXTERNAL
            ExecutionMode.AUTO -> def.riskLevel == ToolRiskLevel.DESTRUCTIVE_OR_EXTERNAL
        }
    }

    private suspend fun executeValidatedCall(
        validated: ValidatedToolCall,
        mode: ExecutionMode,
        sessionApprovedTools: MutableSet<String>,
        mcpConnectors: List<McpConnectorConfig>,
        sandboxInternetEnabled: Boolean,
        onAwaitingApproval: suspend (PendingApprovalAction) -> Unit,
        requestApproval: suspend (PendingApprovalAction) -> ApprovalDecision
    ): ToolExecutionRecord {
        val def = validated.definition
        if (requiresHumanApproval(def, mode, sessionApprovedTools)) {
            val action = PendingApprovalAction(
                id = UUID.randomUUID().toString(),
                callId = validated.callId,
                toolName = def.canonicalName,
                riskLevel = def.riskLevel,
                summary = describeAction(def.canonicalName, validated.arguments),
                targetUrlOrPath = validated.arguments.optString("url")
                    .ifBlank { validated.arguments.optString("path") }
                    .ifBlank { host.currentUrl() },
                argumentsPreview = validated.arguments.toString()
            )
            onAwaitingApproval(action)
            when (requestApproval(action)) {
                ApprovalDecision.DENY -> {
                    return ToolExecutionRecord(
                        callId = validated.callId,
                        toolName = def.canonicalName,
                        argumentsJson = validated.arguments.toString(),
                        success = false,
                        timedOut = false,
                        outputOrError = "Denied by user in Human-in-the-Loop review.",
                        durationMs = 0L,
                        skippedByUser = true
                    )
                }
                ApprovalDecision.ALLOW_FOR_THREAD -> {
                    sessionApprovedTools.add(def.canonicalName)
                }
                ApprovalDecision.APPROVE_ONCE -> Unit
            }
        }

        val start = System.currentTimeMillis()
        return try {
            val result = withTimeout(def.timeoutMs) {
                dispatchTool(validated, mcpConnectors, sandboxInternetEnabled)
            }
            val elapsed = System.currentTimeMillis() - start
            result.fold(
                onSuccess = { out ->
                    ToolExecutionRecord(
                        callId = validated.callId,
                        toolName = def.canonicalName,
                        argumentsJson = validated.arguments.toString(),
                        success = true,
                        timedOut = false,
                        outputOrError = out,
                        durationMs = elapsed
                    )
                },
                onFailure = { err ->
                    ToolExecutionRecord(
                        callId = validated.callId,
                        toolName = def.canonicalName,
                        argumentsJson = validated.arguments.toString(),
                        success = false,
                        timedOut = false,
                        outputOrError = err.message ?: "Tool execution failed.",
                        durationMs = elapsed
                    )
                }
            )
        } catch (_: TimeoutCancellationException) {
            ToolExecutionRecord(
                callId = validated.callId,
                toolName = def.canonicalName,
                argumentsJson = validated.arguments.toString(),
                success = false,
                timedOut = true,
                outputOrError = "Tool '${def.canonicalName}' timed out after ${def.timeoutMs}ms.",
                durationMs = System.currentTimeMillis() - start
            )
        } catch (c: CancellationException) {
            throw c
        }
    }

    private suspend fun dispatchTool(
        validated: ValidatedToolCall,
        mcpConnectors: List<McpConnectorConfig>,
        sandboxInternetEnabled: Boolean
    ): Result<String> {
        val args = validated.arguments
        return when (validated.canonicalName) {
            "browser.open" -> {
                val url = normalizeUrl(args.getString("url"))
                val newTab = args.optBoolean("new_tab", false)
                host.navigateAndWait(url, newTab)
            }
            "browser.snapshot" -> host.evaluateDomJs(DomBridge.SNAPSHOT_INTERACTIVE_JS)
            "browser.get_text" -> host.evaluateDomJs(DomBridge.SNAPSHOT_INTERACTIVE_JS).map { raw ->
                runCatching {
                    val obj = JSONObject(raw)
                    "Title: ${obj.optString("title")}\nURL: ${obj.optString("url")}\n\n${obj.optString("text")}"
                }.getOrDefault(raw)
            }
            "browser.click" -> evalDomAction(DomBridge.clickRefJs(args.getString("ref")))
            "browser.fill" -> evalDomAction(DomBridge.fillRefJs(args.getString("ref"), args.getString("value")))
            "browser.type" -> evalDomAction(DomBridge.typeRefJs(args.getString("ref"), args.getString("value")))
            "browser.press" -> evalDomAction(DomBridge.pressKeyJs(args.getString("key")))
            "browser.select" -> evalDomAction(DomBridge.selectRefJs(args.getString("ref"), args.getString("value")))
            "browser.check" -> evalDomAction(DomBridge.checkRefJs(args.getString("ref"), args.getBoolean("checked")))
            "browser.scroll" -> {
                val ref = args.optString("ref", "")
                val script = if (ref.isNotBlank()) {
                    DomBridge.scrollIntoViewJs(ref)
                } else {
                    DomBridge.scrollJs(args.getString("direction"), args.optInt("pixels", 650))
                }
                evalDomAction(script)
            }
            "browser.back" -> host.goBackAndWait()
            "browser.forward" -> host.goForwardAndWait()
            "browser.reload" -> host.reloadAndWait()
            "browser.wait" -> {
                val ms = args.getInt("ms").toLong()
                delay(ms)
                Result.success("Waited ${ms}ms")
            }
            "sandbox.list" -> {
                val files = sandbox.listFiles()
                val arr = JSONArray()
                files.forEach { f ->
                    arr.put(JSONObject().put("path", f.relativePath).put("bytes", f.sizeBytes))
                }
                Result.success(arr.toString())
            }
            "sandbox.read" -> sandbox.readFile(args.getString("path"))
            "sandbox.write" -> sandbox.writeFile(
                relativePath = args.getString("path"),
                content = args.getString("content"),
                append = args.optBoolean("append", false)
            ).map { entry -> "Wrote ${entry.relativePath} (${entry.sizeBytes} bytes)" }
            "sandbox.delete" -> sandbox.deleteFile(args.getString("path")).map { "Deleted ${args.getString("path")}" }
            "web.fetch" -> {
                if (!sandboxInternetEnabled) {
                    Result.failure(IllegalStateException("Sandbox Internet access is disabled in Agent Studio."))
                } else {
                    fetchPublicUrl(args.getString("url"))
                }
            }
            "device.clipboard_copy" -> {
                val txt = args.getString("text")
                host.copyToClipboard(txt)
                Result.success("Copied ${txt.length} characters to clipboard.")
            }
            else -> {
                if (validated.canonicalName.startsWith("mcp.")) {
                    val parts = validated.canonicalName.removePrefix("mcp.").split(".", limit = 2)
                    val connName = parts.getOrNull(0).orEmpty()
                    val toolName = parts.getOrNull(1).orEmpty()
                    val connector = mcpConnectors.firstOrNull { it.enabled && it.name == connName }
                        ?: return Result.failure(IllegalStateException("MCP connector '$connName' is unavailable."))
                    mcpClient.callTool(connector, toolName, args.optJSONObject("arguments") ?: JSONObject())
                } else {
                    Result.failure(IllegalArgumentException("Unsupported tool ${validated.canonicalName}"))
                }
            }
        }
    }

    private suspend fun evalDomAction(script: String): Result<String> {
        val res = host.evaluateDomJs(script)
        return res.fold(
            onSuccess = { jsonStr ->
                val parsed = runCatching { JSONObject(jsonStr) }.getOrNull()
                if (parsed != null && parsed.has("ok") && !parsed.optBoolean("ok", true)) {
                    Result.failure(IllegalStateException(parsed.optString("error", "DOM action failed.")))
                } else {
                    Result.success(jsonStr)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun fetchPublicUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            val call = httpClient.newCall(req)
            val handle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                coroutineContext.ensureActive()
                call.execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $url")
                    Result.success(body.take(12_000))
                }
            } finally {
                handle?.dispose()
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun buildAttachedContext(files: List<String>): String {
        if (files.isEmpty()) return ""
        val sb = StringBuilder("[Attached Sandbox Files]\n")
        for (path in files) {
            val content = sandbox.readFile(path).getOrElse { "Error reading $path: ${it.message}" }
            sb.append("--- $path ---\n").append(content.take(4000)).append("\n")
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(
        basePrompt: String,
        userPrompt: String,
        activeSkills: List<AgentSkill>,
        mode: ExecutionMode,
        availableTools: List<ToolDefinition>,
        curUrl: String?,
        curTitle: String?
    ): String {
        val lowerPrompt = userPrompt.lowercase()
        val matchedSkills = if (availableTools.isEmpty()) {
            emptyList()
        } else {
            activeSkills.filter { skill ->
                skill.enabled && (skill.triggerKeywords.isEmpty() || skill.triggerKeywords.any { lowerPrompt.contains(it.lowercase()) })
            }
        }
        return buildString {
            append(basePrompt.trim())
            if (!curUrl.isNullOrBlank()) {
                append("\nActive browser tab: ${curTitle?.ifBlank { curUrl } ?: curUrl} ($curUrl)")
            }
            if (availableTools.isNotEmpty()) {
                append("\nMode: ${mode.label}. Invoke tools via native structured tool calls only; never print tool JSON as text.")
            }
            if (matchedSkills.isNotEmpty()) {
                append("\nSkills:")
                matchedSkills.forEach { skill ->
                    append("\n- ${skill.name}: ${skill.instructions}")
                }
            }
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun describeAction(toolName: String, args: JSONObject): String = when (toolName) {
        "browser.open" -> "Open ${args.optString("url")}"
        "browser.click" -> "Click element ${args.optString("ref")}"
        "browser.fill" -> "Fill ${args.optString("ref")} with \"${args.optString("value").take(30)}\""
        "browser.type" -> "Type into ${args.optString("ref")}"
        "browser.press" -> "Press ${args.optString("key")}"
        "sandbox.write" -> "Write sandbox file ${args.optString("path")}"
        "sandbox.delete" -> "Delete sandbox file ${args.optString("path")}"
        else -> "Execute $toolName"
    }

    private fun extractCitations(text: String): List<CitationItem> {
        val linkRegex = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
        return linkRegex.findAll(text)
            .map { CitationItem(it.groupValues[1].trim(), it.groupValues[2].trim()) }
            .distinctBy { it.url }
            .take(8)
            .toList()
    }

    private fun ToolExecutionRecord.toolNameWireOrCanonical(tools: List<ToolDefinition>): String =
        tools.firstOrNull { it.canonicalName == toolName }?.wireName ?: toolName
}
