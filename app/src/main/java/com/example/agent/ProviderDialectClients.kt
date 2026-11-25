package com.example.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

internal class StreamedToolCallAcc(
    var id: String = "",
    var name: String = "",
    val argsBuf: StringBuilder = StringBuilder(),
    var thoughtSignature: String? = null
)

object ProviderDialectClients {

    fun supportsOpenRouterReasoningParam(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "openrouter.ai" || host.endsWith(".openrouter.ai") ||
            host == "api.kilo.ai" || host.endsWith(".kilo.ai")
    }

    internal suspend fun <T> executeCancellable(
        client: OkHttpClient,
        request: Request,
        block: suspend (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        val handle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            coroutineContext.ensureActive()
            call.execute().use { resp ->
                coroutineContext.ensureActive()
                block(resp)
            }
        } finally {
            handle?.dispose()
        }
    }

    internal suspend fun executeOpenAiTitle(
        client: OkHttpClient,
        jsonMediaType: MediaType,
        provider: LlmProviderConfig,
        messages: List<BrainMessage>
    ): BrainTurnOutput {
        val msgArr = buildOpenAiMessages(messages, allowVision = false)
        val payload = JSONObject().apply {
            put("model", provider.modelId)
            put("messages", msgArr)
            put("stream", false)
            put("max_tokens", 28)
            put("temperature", 0.2)
            if (provider.supportsReasoning && supportsOpenRouterReasoningParam(provider.baseUrl)) {
                put("reasoning", JSONObject().put("effort", "none"))
            }
        }
        val reqBuilder = Request.Builder()
            .url(provider.baseUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
        if (provider.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        return executeCancellable(client, reqBuilder.build()) { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            parseOpenAiResponse(body)
        }
    }

    internal fun buildOpenAiChatPayload(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        allowVision: Boolean,
        stream: Boolean = true
    ): JSONObject {
        val msgArr = buildOpenAiMessages(messages, allowVision)
        return JSONObject().apply {
            put("model", provider.modelId)
            put("messages", msgArr)
            put("stream", stream)
            put("max_tokens", 2048)
            if (tools.isNotEmpty()) {
                put("tools", ToolRegistry.toOpenAiToolsJson(tools))
                put("tool_choice", "auto")
            }
            if (!enableReasoning && provider.supportsReasoning && supportsOpenRouterReasoningParam(provider.baseUrl)) {
                put("reasoning", JSONObject().put("effort", "none"))
            }
        }
    }

    internal suspend fun executeOpenAiTurn(
        client: OkHttpClient,
        jsonMediaType: MediaType,
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        allowVision: Boolean,
        onStreamDelta: (String, String) -> Unit
    ): BrainTurnOutput {
        val payload = buildOpenAiChatPayload(
            provider = provider,
            messages = messages,
            tools = tools,
            enableReasoning = enableReasoning,
            allowVision = allowVision,
            stream = true
        )

        val reqBuilder = Request.Builder()
            .url(provider.baseUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
        if (provider.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }

        return executeCancellable(client, reqBuilder.build()) { resp ->
            if (!resp.isSuccessful) {
                throw buildHttpError(resp.code, resp.body?.string().orEmpty())
            }
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val source = resp.body?.source() ?: return@executeCancellable BrainTurnOutput.FinalText("")

            if (!contentType.contains("text/event-stream")) {
                val raw = source.readUtf8()
                if (raw.trimStart().startsWith("data:")) {
                    return@executeCancellable parseOpenAiSseLines(raw.lineSequence(), onStreamDelta)
                }
                return@executeCancellable parseOpenAiResponse(raw)
            }
            parseOpenAiSseSource(source, onStreamDelta)
        }
    }

    internal suspend fun executeAnthropicTurn(
        client: OkHttpClient,
        jsonMediaType: MediaType,
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        allowVision: Boolean,
        stream: Boolean = true,
        maxTokens: Int = 2048,
        onStreamDelta: (String, String) -> Unit
    ): BrainTurnOutput {
        val systemTexts = messages.filterIsInstance<BrainMessage.System>().joinToString("\n\n") { it.text }
        val msgArr = buildAnthropicMessages(messages, allowVision)

        val payload = JSONObject().apply {
            put("model", provider.modelId)
            put("max_tokens", maxTokens)
            put("stream", stream)
            if (systemTexts.isNotBlank()) put("system", systemTexts)
            put("messages", msgArr)
            if (tools.isNotEmpty()) {
                put("tools", ToolRegistry.toAnthropicToolsJson(tools))
            }
            if (enableReasoning) {
                put("thinking", JSONObject().put("type", "enabled").put("budget_tokens", 1024))
            }
        }

        val req = Request.Builder()
            .url(provider.baseUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream, application/json" else "application/json")
            .header("anthropic-version", "2023-06-01")
            .header("x-api-key", provider.apiKey)
            .build()

        return executeCancellable(client, req) { resp ->
            if (!resp.isSuccessful) {
                throw buildHttpError(resp.code, resp.body?.string().orEmpty())
            }
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val source = resp.body?.source() ?: return@executeCancellable BrainTurnOutput.FinalText("")
            if (!stream || !contentType.contains("text/event-stream")) {
                return@executeCancellable parseAnthropicResponse(source.readUtf8())
            }

            val textBuf = StringBuilder()
            val thinkingBuf = StringBuilder()
            val toolAcc = linkedMapOf<Int, StreamedToolCallAcc>()
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val data = trimmed.removePrefix("data:").trim()
                val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (obj.optString("type")) {
                    "content_block_start" -> {
                        val bIdx = obj.optInt("index", 0)
                        val cb = obj.optJSONObject("content_block") ?: continue
                        if (cb.optString("type") == "tool_use") {
                            toolAcc[bIdx] = StreamedToolCallAcc(
                                id = cb.optString("id", "toolu_$bIdx"),
                                name = cb.optString("name", "")
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val bIdx = obj.optInt("index", 0)
                        val d = obj.optJSONObject("delta") ?: continue
                        when (d.optString("type")) {
                            "text_delta" -> {
                                val t = d.optString("text", "")
                                if (t.isNotEmpty()) {
                                    textBuf.append(t)
                                    onStreamDelta(t, "")
                                }
                            }
                            "thinking_delta" -> {
                                val t = d.optString("thinking", "")
                                if (t.isNotEmpty()) {
                                    thinkingBuf.append(t)
                                    onStreamDelta("", t)
                                }
                            }
                            "input_json_delta" -> {
                                toolAcc[bIdx]?.argsBuf?.append(d.optString("partial_json", ""))
                            }
                        }
                    }
                }
            }
            finalizeStream(textBuf, thinkingBuf, toolAcc)
        }
    }

    internal fun buildGeminiGeneratePayload(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        allowVision: Boolean,
        maxTokens: Int = 2048
    ): JSONObject {
        val systemTexts = messages.filterIsInstance<BrainMessage.System>().joinToString("\n\n") { it.text }
        val contents = buildGeminiContents(messages, allowVision)

        return JSONObject().apply {
            if (systemTexts.isNotBlank()) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemTexts))))
            }
            put("contents", contents)
            if (tools.isNotEmpty()) {
                put("tools", ToolRegistry.toGeminiToolsJson(tools))
            }
            val genConfig = JSONObject().apply {
                put("maxOutputTokens", maxTokens)
                if (enableReasoning) {
                    put("thinkingConfig", JSONObject().put("includeThoughts", true))
                } else if (provider.modelId.contains("2.5")) {
                    put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
                }
            }
            put("generationConfig", genConfig)
        }
    }

    internal suspend fun executeGeminiTurn(
        client: OkHttpClient,
        jsonMediaType: MediaType,
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        allowVision: Boolean,
        stream: Boolean = true,
        maxTokens: Int = 2048,
        onStreamDelta: (String, String) -> Unit
    ): BrainTurnOutput {
        val payload = buildGeminiGeneratePayload(
            provider = provider,
            messages = messages,
            tools = tools,
            enableReasoning = enableReasoning,
            allowVision = allowVision,
            maxTokens = maxTokens
        )

        val cleanBase = provider.baseUrl.trimEnd('/')
        val url = if (stream) {
            "$cleanBase/${provider.modelId}:streamGenerateContent?alt=sse"
        } else {
            "$cleanBase/${provider.modelId}:generateContent"
        }
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream, application/json" else "application/json")
        if (provider.apiKey.isNotBlank()) {
            reqBuilder.header("x-goog-api-key", provider.apiKey)
        }

        return executeCancellable(client, reqBuilder.build()) { resp ->
            if (!resp.isSuccessful) {
                throw buildHttpError(resp.code, resp.body?.string().orEmpty())
            }
            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val source = resp.body?.source() ?: return@executeCancellable BrainTurnOutput.FinalText("")
            if (!stream || !contentType.contains("text/event-stream")) {
                return@executeCancellable parseGeminiResponse(source.readUtf8())
            }

            val textBuf = StringBuilder()
            val thoughtBuf = StringBuilder()
            val calls = mutableListOf<StructuredToolCall>()
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val data = trimmed.removePrefix("data:").trim()
                val chunkOut = runCatching { parseGeminiResponse(data) }.getOrNull() ?: continue
                when (chunkOut) {
                    is BrainTurnOutput.FinalText -> {
                        if (!chunkOut.reasoning.isNullOrEmpty()) {
                            thoughtBuf.append(chunkOut.reasoning)
                            onStreamDelta("", chunkOut.reasoning)
                        }
                        if (chunkOut.text.isNotEmpty()) {
                            textBuf.append(chunkOut.text)
                            onStreamDelta(chunkOut.text, "")
                        }
                    }
                    is BrainTurnOutput.ToolCalls -> {
                        if (!chunkOut.reasoning.isNullOrEmpty()) {
                            thoughtBuf.append(chunkOut.reasoning)
                            onStreamDelta("", chunkOut.reasoning)
                        }
                        if (!chunkOut.assistantText.isNullOrEmpty()) {
                            textBuf.append(chunkOut.assistantText)
                            onStreamDelta(chunkOut.assistantText, "")
                        }
                        calls.addAll(chunkOut.calls)
                    }
                }
            }
            val reasoning = thoughtBuf.toString().trim().ifBlank { null }
            if (calls.isNotEmpty()) {
                BrainTurnOutput.ToolCalls(
                    calls = calls,
                    assistantText = textBuf.toString().ifBlank { null },
                    reasoning = reasoning
                )
            } else {
                BrainTurnOutput.FinalText(text = textBuf.toString(), reasoning = reasoning)
            }
        }
    }

    private fun buildOpenAiMessages(messages: List<BrainMessage>, allowVision: Boolean): JSONArray {
        val msgArr = JSONArray()
        messages.forEach { msg ->
            when (msg) {
                is BrainMessage.System -> msgArr.put(JSONObject().apply {
                    put("role", "system")
                    put("content", msg.text)
                })
                is BrainMessage.User -> {
                    if (allowVision && !msg.imageBase64.isNullOrBlank()) {
                        val contentArr = JSONArray().apply {
                            put(JSONObject().put("type", "text").put("text", msg.text))
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${msg.imageBase64}"))
                            })
                        }
                        msgArr.put(JSONObject().put("role", "user").put("content", contentArr))
                    } else {
                        msgArr.put(JSONObject().put("role", "user").put("content", msg.text))
                    }
                }
                is BrainMessage.AssistantText -> msgArr.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", msg.text)
                })
                is BrainMessage.AssistantToolCalls -> {
                    val toolCallsJson = JSONArray().apply {
                        msg.calls.forEach { call ->
                            put(JSONObject().apply {
                                put("id", call.id)
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", call.name)
                                    put("arguments", call.arguments.toString())
                                })
                            })
                        }
                    }
                    msgArr.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", msg.text ?: JSONObject.NULL)
                        put("tool_calls", toolCallsJson)
                    })
                }
                is BrainMessage.ToolResult -> msgArr.put(JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", msg.callId)
                    put("name", msg.toolName)
                    put("content", msg.resultJson)
                })
            }
        }
        return msgArr
    }

    private fun buildAnthropicMessages(messages: List<BrainMessage>, allowVision: Boolean): JSONArray {
        val msgArr = JSONArray()
        var idx = 0
        while (idx < messages.size) {
            when (val m = messages[idx]) {
                is BrainMessage.System -> idx++
                is BrainMessage.User -> {
                    val contentArr = JSONArray()
                    if (allowVision && !m.imageBase64.isNullOrBlank()) {
                        contentArr.put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", m.imageBase64)
                            })
                        })
                    }
                    contentArr.put(JSONObject().put("type", "text").put("text", m.text))
                    msgArr.put(JSONObject().put("role", "user").put("content", contentArr))
                    idx++
                }
                is BrainMessage.AssistantText -> {
                    msgArr.put(JSONObject().put("role", "assistant").put("content", m.text))
                    idx++
                }
                is BrainMessage.AssistantToolCalls -> {
                    val blocks = JSONArray()
                    if (!m.text.isNullOrBlank()) {
                        blocks.put(JSONObject().put("type", "text").put("text", m.text))
                    }
                    m.calls.forEach { call ->
                        blocks.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", call.id)
                            put("name", call.name)
                            put("input", call.arguments)
                        })
                    }
                    msgArr.put(JSONObject().put("role", "assistant").put("content", blocks))
                    idx++
                }
                is BrainMessage.ToolResult -> {
                    val resultBlocks = JSONArray()
                    while (idx < messages.size && messages[idx] is BrainMessage.ToolResult) {
                        val tr = messages[idx] as BrainMessage.ToolResult
                        resultBlocks.put(JSONObject().apply {
                            put("type", "tool_result")
                            put("tool_use_id", tr.callId)
                            put("content", tr.resultJson)
                        })
                        idx++
                    }
                    msgArr.put(JSONObject().put("role", "user").put("content", resultBlocks))
                }
            }
        }
        return msgArr
    }

    private fun buildGeminiContents(messages: List<BrainMessage>, allowVision: Boolean): JSONArray {
        val contents = JSONArray()
        var idx = 0
        while (idx < messages.size) {
            when (val m = messages[idx]) {
                is BrainMessage.System -> idx++
                is BrainMessage.User -> {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", m.text))
                        if (allowVision && !m.imageBase64.isNullOrBlank()) {
                            put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", m.imageBase64)
                            }))
                        }
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                    idx++
                }
                is BrainMessage.AssistantText -> {
                    contents.put(JSONObject().apply {
                        put("role", "model")
                        put("parts", JSONArray().put(JSONObject().put("text", m.text)))
                    })
                    idx++
                }
                is BrainMessage.AssistantToolCalls -> {
                    val parts = JSONArray()
                    m.calls.forEach { call ->
                        val partObj = JSONObject().put("functionCall", JSONObject().apply {
                            put("name", call.name)
                            put("args", call.arguments)
                        })
                        if (!call.thoughtSignature.isNullOrBlank()) {
                            partObj.put("thoughtSignature", call.thoughtSignature)
                        }
                        parts.put(partObj)
                    }
                    contents.put(JSONObject().put("role", "model").put("parts", parts))
                    idx++
                }
                is BrainMessage.ToolResult -> {
                    val parts = JSONArray()
                    while (idx < messages.size && messages[idx] is BrainMessage.ToolResult) {
                        val tr = messages[idx] as BrainMessage.ToolResult
                        parts.put(JSONObject().put("functionResponse", JSONObject().apply {
                            put("name", tr.toolName)
                            put("response", runCatching { JSONObject(tr.resultJson) }.getOrElse {
                                JSONObject().put("result", tr.resultJson)
                            })
                        }))
                        idx++
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", parts))
                }
            }
        }
        return contents
    }

    private suspend fun parseOpenAiSseSource(
        source: BufferedSource,
        onStreamDelta: (String, String) -> Unit
    ): BrainTurnOutput {
        val textBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        val toolAcc = linkedMapOf<Int, StreamedToolCallAcc>()
        var nonSseFallback: StringBuilder? = null

        while (!source.exhausted()) {
            currentCoroutineContext().ensureActive()
            val line = source.readUtf8Line() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
            if (!trimmed.startsWith("data:") && textBuf.isEmpty() && toolAcc.isEmpty()) {
                if (trimmed.startsWith("{")) {
                    nonSseFallback = (nonSseFallback ?: StringBuilder()).append(line)
                    continue
                }
            }
            if (nonSseFallback != null) {
                nonSseFallback.append(line)
                continue
            }
            if (!trimmed.startsWith("data:")) continue
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]") break
            consumeOpenAiSseChunk(data, textBuf, reasoningBuf, toolAcc, onStreamDelta)
        }

        if (nonSseFallback != null && textBuf.isEmpty() && toolAcc.isEmpty()) {
            return parseOpenAiResponse(nonSseFallback.toString())
        }
        return finalizeStream(textBuf, reasoningBuf, toolAcc)
    }

    private fun parseOpenAiSseLines(
        lines: Sequence<String>,
        onStreamDelta: (String, String) -> Unit
    ): BrainTurnOutput {
        val textBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        val toolAcc = linkedMapOf<Int, StreamedToolCallAcc>()
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]") break
            consumeOpenAiSseChunk(data, textBuf, reasoningBuf, toolAcc, onStreamDelta)
        }
        return finalizeStream(textBuf, reasoningBuf, toolAcc)
    }

    private fun consumeOpenAiSseChunk(
        data: String,
        textBuf: StringBuilder,
        reasoningBuf: StringBuilder,
        toolAcc: MutableMap<Int, StreamedToolCallAcc>,
        onStreamDelta: (String, String) -> Unit
    ) {
        val root = runCatching { JSONObject(data) }.getOrNull() ?: return
        val choices = root.optJSONArray("choices") ?: return
        if (choices.length() == 0) return
        val choice = choices.optJSONObject(0) ?: return
        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return

        val contentChunk = delta.optString("content", "").takeIf { it.isNotEmpty() && it != "null" }.orEmpty()
        val reasoningChunk = extractOpenAiReasoning(delta).orEmpty()

        if (contentChunk.isNotEmpty() || reasoningChunk.isNotEmpty()) {
            if (contentChunk.isNotEmpty()) textBuf.append(contentChunk)
            if (reasoningChunk.isNotEmpty()) reasoningBuf.append(reasoningChunk)
            onStreamDelta(contentChunk, reasoningChunk)
        }

        val tcArr = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until tcArr.length()) {
            val tc = tcArr.optJSONObject(i) ?: continue
            val idx = tc.optInt("index", i)
            val acc = toolAcc.getOrPut(idx) { StreamedToolCallAcc(id = "call_$idx") }
            tc.optString("id", "").takeIf { it.isNotBlank() && it != "null" }?.let { acc.id = it }
            val fn = tc.optJSONObject("function") ?: continue
            fn.optString("name", "").takeIf { it.isNotBlank() && it != "null" }?.let { acc.name += it }
            fn.optString("arguments", "").takeIf { it.isNotEmpty() && it != "null" }?.let { acc.argsBuf.append(it) }
        }
    }

    private fun finalizeStream(
        textBuf: StringBuilder,
        reasoningBuf: StringBuilder,
        toolAcc: Map<Int, StreamedToolCallAcc>
    ): BrainTurnOutput {
        val reasoning = reasoningBuf.toString().trim().ifBlank { null }
        val text = textBuf.toString()
        if (toolAcc.isNotEmpty()) {
            val calls = toolAcc.entries.sortedBy { it.key }.mapNotNull { (_, acc) ->
                if (acc.name.isBlank()) return@mapNotNull null
                val argsObj = runCatching {
                    JSONObject(acc.argsBuf.toString().ifBlank { "{}" })
                }.getOrElse { JSONObject() }
                StructuredToolCall(
                    id = acc.id,
                    name = acc.name,
                    arguments = argsObj,
                    thoughtSignature = acc.thoughtSignature
                )
            }
            if (calls.isNotEmpty()) {
                return BrainTurnOutput.ToolCalls(
                    calls = calls,
                    assistantText = text.ifBlank { null },
                    reasoning = reasoning
                )
            }
        }
        return BrainTurnOutput.FinalText(text = text, reasoning = reasoning)
    }

    private fun buildHttpError(code: Int, body: String): IllegalStateException {
        val errMsg = runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
        }.getOrNull().orEmpty()
        return IllegalStateException("LLM HTTP $code: ${errMsg.ifBlank { body.take(220) }}")
    }

    fun parseOpenAiResponse(rawJson: String): BrainTurnOutput {
        val root = JSONObject(rawJson)
        val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val reasoning = extractOpenAiReasoning(msg)
        val content = msg.optString("content", "").takeIf { it != "null" }.orEmpty()

        val toolCallsArr = msg.optJSONArray("tool_calls")
        if (toolCallsArr != null && toolCallsArr.length() > 0) {
            val parsed = buildList {
                for (i in 0 until toolCallsArr.length()) {
                    val tc = toolCallsArr.getJSONObject(i)
                    val fn = tc.getJSONObject("function")
                    val rawArgs = fn.optString("arguments", "{}")
                    val argsObj = runCatching { JSONObject(rawArgs) }.getOrElse { JSONObject() }
                    add(
                        StructuredToolCall(
                            id = tc.optString("id", "call_$i"),
                            name = fn.getString("name"),
                            arguments = argsObj
                        )
                    )
                }
            }
            return BrainTurnOutput.ToolCalls(
                calls = parsed,
                assistantText = content.ifBlank { null },
                reasoning = reasoning
            )
        }
        return BrainTurnOutput.FinalText(
            text = content,
            reasoning = reasoning
        )
    }

    fun parseAnthropicResponse(rawJson: String): BrainTurnOutput {
        val root = JSONObject(rawJson)
        val contentArr = root.optJSONArray("content") ?: JSONArray()
        val textBuf = StringBuilder()
        val thinkingBuf = StringBuilder()
        val calls = mutableListOf<StructuredToolCall>()

        for (i in 0 until contentArr.length()) {
            val block = contentArr.getJSONObject(i)
            when (block.optString("type")) {
                "thinking" -> thinkingBuf.append(block.optString("thinking")).append("\n")
                "text" -> textBuf.append(block.optString("text"))
                "tool_use" -> calls.add(
                    StructuredToolCall(
                        id = block.optString("id", "toolu_$i"),
                        name = block.getString("name"),
                        arguments = block.optJSONObject("input") ?: JSONObject()
                    )
                )
            }
        }
        val reasoning = thinkingBuf.toString().trim().ifBlank { null }
        return if (calls.isNotEmpty()) {
            BrainTurnOutput.ToolCalls(
                calls = calls,
                assistantText = textBuf.toString().ifBlank { null },
                reasoning = reasoning
            )
        } else {
            BrainTurnOutput.FinalText(text = textBuf.toString(), reasoning = reasoning)
        }
    }

    fun parseGeminiResponse(rawJson: String): BrainTurnOutput {
        val root = JSONObject(rawJson)
        val candidate = root.getJSONArray("candidates").getJSONObject(0)
        val parts = candidate.getJSONObject("content").optJSONArray("parts") ?: JSONArray()
        val textBuf = StringBuilder()
        val thoughtBuf = StringBuilder()
        val calls = mutableListOf<StructuredToolCall>()

        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.has("functionCall")) {
                val fc = p.getJSONObject("functionCall")
                val signature = p.optString("thoughtSignature", "")
                    .ifBlank { p.optString("thought_signature", "") }
                    .takeIf { it.isNotBlank() }
                calls.add(
                    StructuredToolCall(
                        id = "gemini_${UUID.randomUUID().toString().take(8)}",
                        name = fc.getString("name"),
                        arguments = fc.optJSONObject("args") ?: JSONObject(),
                        thoughtSignature = signature
                    )
                )
            } else if (p.optBoolean("thought", false)) {
                thoughtBuf.append(p.optString("text"))
            } else if (p.has("text")) {
                textBuf.append(p.optString("text"))
            }
        }

        val reasoning = thoughtBuf.toString().trim().ifBlank { null }
        return if (calls.isNotEmpty()) {
            BrainTurnOutput.ToolCalls(
                calls = calls,
                assistantText = textBuf.toString().ifBlank { null },
                reasoning = reasoning
            )
        } else {
            BrainTurnOutput.FinalText(text = textBuf.toString(), reasoning = reasoning)
        }
    }

    private fun extractOpenAiReasoning(msg: JSONObject): String? {
        val direct = msg.optString("reasoning", "").takeIf { it.isNotBlank() && it != "null" }
            ?: msg.optString("reasoning_content", "").takeIf { it.isNotBlank() && it != "null" }
        if (direct != null) return direct.trim()

        val details = msg.optJSONArray("reasoning_details") ?: return null
        val sb = StringBuilder()
        for (i in 0 until details.length()) {
            val item = details.optJSONObject(i) ?: continue
            val txt = item.optString("text", "")
            if (txt.isNotBlank()) sb.append(txt).append("\n")
        }
        return sb.toString().trim().ifBlank { null }
    }
}
