package com.example.agent

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MultiProviderBrain(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) : LlmBrain {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun runAgentTurn(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean
    ): Result<BrainTurnOutput> = runAgentTurnStreaming(
        provider = provider,
        messages = messages,
        tools = tools,
        enableReasoning = enableReasoning,
        onStreamDelta = { _, _ -> }
    )

    override suspend fun runAgentTurnStreaming(
        provider: LlmProviderConfig,
        messages: List<BrainMessage>,
        tools: List<ToolDefinition>,
        enableReasoning: Boolean,
        onStreamDelta: (textDelta: String, reasoningDelta: String) -> Unit
    ): Result<BrainTurnOutput> {
        return try {
            if (!provider.isKeyless && provider.apiKey.isBlank()) {
                return Result.failure(
                    IllegalStateException("${provider.name} requires an API key in Agent Studio → Providers.")
                )
            }
            val effectiveReasoning = provider.supportsReasoning && enableReasoning
            val effectiveVision = provider.supportsVision

            val output = when (provider.dialect) {
                ProviderDialect.OPENAI_COMPAT -> ProviderDialectClients.executeOpenAiTurn(
                    client, jsonMediaType, provider, messages, tools, effectiveReasoning, effectiveVision, onStreamDelta
                )
                ProviderDialect.ANTHROPIC -> ProviderDialectClients.executeAnthropicTurn(
                    client, jsonMediaType, provider, messages, tools, effectiveReasoning, effectiveVision,
                    stream = true, maxTokens = 2048, onStreamDelta = onStreamDelta
                )
                ProviderDialect.GEMINI_REST -> ProviderDialectClients.executeGeminiTurn(
                    client, jsonMediaType, provider, messages, tools, effectiveReasoning, effectiveVision,
                    stream = true, maxTokens = 2048, onStreamDelta = onStreamDelta
                )
            }
            Result.success(output)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun generateChatTitle(
        provider: LlmProviderConfig,
        userPrompt: String
    ): String? {
        if (!provider.isKeyless && provider.apiKey.isBlank()) return null
        return try {
            val cleanInput = userPrompt.trim().take(360)
            val titleMessages = listOf(
                BrainMessage.System(
                    "Generate a short 2 to 5 word Title Case title capturing the user's intent. Output ONLY the title, no quotes, no trailing punctuation."
                ),
                BrainMessage.User(cleanInput)
            )
            val result = when (provider.dialect) {
                ProviderDialect.OPENAI_COMPAT -> ProviderDialectClients.executeOpenAiTitle(
                    client, jsonMediaType, provider, titleMessages
                )
                ProviderDialect.ANTHROPIC -> ProviderDialectClients.executeAnthropicTurn(
                    client, jsonMediaType, provider, titleMessages, emptyList(),
                    enableReasoning = false, allowVision = false, stream = false, maxTokens = 28
                ) { _, _ -> }
                ProviderDialect.GEMINI_REST -> ProviderDialectClients.executeGeminiTurn(
                    client, jsonMediaType, provider, titleMessages, emptyList(),
                    enableReasoning = false, allowVision = false, stream = false, maxTokens = 28
                ) { _, _ -> }
            }
            val raw = (result as? BrainTurnOutput.FinalText)?.text.orEmpty()
            raw.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.replace(Regex("""^["'`*#\-\s]+|["'`*#.\-!?\s]+$"""), "")
                ?.take(42)
                ?.takeIf { it.isNotBlank() }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun testConnection(provider: LlmProviderConfig): Result<String> {
        return try {
            if (!provider.isKeyless && provider.apiKey.isBlank()) {
                return Result.failure(IllegalStateException("Missing API key for ${provider.name}."))
            }
            val res = runAgentTurn(
                provider = provider,
                messages = listOf(BrainMessage.User("Reply with OK in one word.")),
                tools = emptyList(),
                enableReasoning = false
            ).getOrThrow()
            val summary = when (res) {
                is BrainTurnOutput.FinalText -> "Connected (${provider.modelId}): ${res.text.take(40)}"
                is BrainTurnOutput.ToolCalls -> "Connected (${provider.modelId})"
            }
            Result.success(summary)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    companion object {
        fun parseOpenAiResponse(rawJson: String): BrainTurnOutput =
            ProviderDialectClients.parseOpenAiResponse(rawJson)

        fun parseAnthropicResponse(rawJson: String): BrainTurnOutput =
            ProviderDialectClients.parseAnthropicResponse(rawJson)

        fun parseGeminiResponse(rawJson: String): BrainTurnOutput =
            ProviderDialectClients.parseGeminiResponse(rawJson)
    }
}
