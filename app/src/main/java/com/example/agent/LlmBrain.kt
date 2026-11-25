package com.example.agent

import org.json.JSONObject

data class StructuredToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
    val thoughtSignature: String? = null
)

sealed class BrainMessage {
    data class System(val text: String) : BrainMessage()
    data class User(val text: String, val imageBase64: String? = null) : BrainMessage()
    data class AssistantText(val text: String, val reasoning: String? = null) : BrainMessage()
    data class AssistantToolCalls(
        val calls: List<StructuredToolCall>,
        val text: String? = null,
        val reasoning: String? = null
    ) : BrainMessage()
    data class ToolResult(
        val callId: String,
        val toolName: String,
        val resultJson: String
    ) : BrainMessage()
}

sealed class BrainTurnOutput {
    data class FinalText(
        val text: String,
        val reasoning: String? = null
    ) : BrainTurnOutput()

    data class ToolCalls(
        val calls: List<StructuredToolCall>,
        val assistantText: String? = null,
        val reasoning: String? = null
    ) : BrainTurnOutput()
}

interface LlmBrain {
    suspend fun runAgentTurn(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean
    ): Result<BrainTurnOutput>

    suspend fun runAgentTurnStreaming(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        onStreamDelta: (textDelta: String, reasoningDelta: String) -> Unit
    ): Result<BrainTurnOutput> = runAgentTurn(provider, messages, tools, enableReasoning)

    suspend fun generateChatTitle(
        provider: LlmProviderConfig,
        userPrompt: String
    ): String? = null

    suspend fun testConnection(provider: LlmProviderConfig): Result<String>
}
