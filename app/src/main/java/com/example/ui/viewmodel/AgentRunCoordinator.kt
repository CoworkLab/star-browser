package com.example.ui.viewmodel

import com.example.agent.AgentDefaults
import com.example.agent.AgentSkill
import com.example.agent.ApprovalDecision
import com.example.agent.LlmBrain
import com.example.agent.LlmProviderConfig
import com.example.agent.McpConnectorConfig
import com.example.agent.PendingApprovalAction
import com.example.agent.SystemPromptPreset
import com.example.agent.ToolkitId
import com.example.agent.TrueAgentEngine
import com.example.data.local.AgentMessageEntity
import com.example.data.local.StarRepository
import com.example.data.preferences.BrowserSettingsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class AgentRunCoordinator(
    private val repo: StarRepository,
    private val brain: LlmBrain,
    private val engine: TrueAgentEngine
) {
    suspend fun executeTurn(
        convId: String,
        displayInput: String,
        effectiveUserPrompt: String,
        fallbackTitleSource: String,
        grabbedTitle: String?,
        provider: LlmProviderConfig,
        settings: BrowserSettingsSnapshot,
        attachedImageBase64: String?,
        attachedSandboxFiles: List<String>,
        requestApproval: suspend (PendingApprovalAction) -> ApprovalDecision,
        onStreamDelta: (String, String) -> Unit,
        onTurnResetStream: () -> Unit
    ) {
        val promptPreset: SystemPromptPreset =
            settings.prompts.firstOrNull { it.id == settings.activePromptId } ?: settings.prompts.first()
        val existingMsgs = repo.getMessagesOnce(convId)
        val isFirstUserTurn = existingMsgs.none { it.role == "user" }
        val quickFallbackTitle = BrowserUrlAndMediaHelper.buildFallbackIntentTitle(
            fallbackTitleSource,
            grabbedTitle
        )

        if (isFirstUserTurn) {
            repo.updateConversationMeta(convId, quickFallbackTitle)
        }

        repo.insertMessage(
            AgentMessageEntity(
                conversationId = convId,
                role = "user",
                content = displayInput
            )
        )

        val historyPairs = existingMsgs
            .filter { it.role == "user" || it.role == "assistant" }
            .map { it.role to it.content }

        val stepRowIds = ConcurrentHashMap<String, Long>()
        try {
            val result = engine.run(
                userPrompt = effectiveUserPrompt,
                history = historyPairs,
                provider = provider,
                systemPrompt = promptPreset,
                activeSkills = settings.skills,
                mode = settings.executionMode,
                enableReasoning = provider.supportsReasoning && settings.reasoningRequested,
                enabledToolkits = settings.enabledToolkits,
                mcpConnectors = settings.mcpConnectors,
                sandboxInternetEnabled = settings.sandboxInternetEnabled,
                attachedImageBase64 = attachedImageBase64,
                attachedSandboxFiles = attachedSandboxFiles,
                requestApprovalDecision = requestApproval,
                onToolStarted = { started ->
                    val rowId = repo.insertMessage(
                        AgentMessageEntity(
                            conversationId = convId,
                            role = "step",
                            content = "",
                            toolName = started.toolName,
                            toolSuccess = null,
                            toolCallId = started.callId,
                            toolArgs = started.argumentsJson,
                            toolStatus = "running"
                        )
                    )
                    stepRowIds[started.callId] = rowId
                },
                onToolAwaitingApproval = { action ->
                    val rowId = stepRowIds[action.callId]
                    if (rowId != null) {
                        repo.updateStepMessage(
                            id = rowId,
                            status = "awaiting_approval",
                            content = action.summary,
                            success = null,
                            durationMs = null
                        )
                    }
                },
                onStepExecuted = { record ->
                    val status = when {
                        record.success -> "completed"
                        record.skippedByUser -> "skipped"
                        else -> "failed"
                    }
                    val rowId = stepRowIds.remove(record.callId)
                    if (rowId != null) {
                        repo.updateStepMessage(
                            id = rowId,
                            status = status,
                            content = record.outputOrError,
                            success = record.success,
                            durationMs = record.durationMs
                        )
                    } else {
                        repo.insertMessage(
                            AgentMessageEntity(
                                conversationId = convId,
                                role = "step",
                                content = record.outputOrError,
                                toolName = record.toolName,
                                toolSuccess = record.success,
                                toolCallId = record.callId,
                                toolArgs = record.argumentsJson,
                                toolStatus = status,
                                toolDurationMs = record.durationMs
                            )
                        )
                    }
                },
                onStreamDelta = onStreamDelta,
                onTurnResetStream = onTurnResetStream
            )

            result.fold(
                onSuccess = { runRes ->
                    repo.insertMessage(
                        AgentMessageEntity(
                            conversationId = convId,
                            role = "assistant",
                            content = runRes.finalText,
                            reasoning = runRes.reasoning,
                            citationsJson = AgentDefaults.serializeCitations(runRes.citations)
                        )
                    )
                    if (isFirstUserTurn) {
                        val aiTitle = brain.generateChatTitle(provider, displayInput)
                        if (!aiTitle.isNullOrBlank()) {
                            repo.updateConversationMeta(convId, aiTitle)
                        }
                    }
                },
                onFailure = { err ->
                    repo.insertMessage(
                        AgentMessageEntity(
                            conversationId = convId,
                            role = "assistant",
                            content = "Request failed: ${err.message ?: "Unknown error"}"
                        )
                    )
                }
            )
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                stepRowIds.values.forEach { rowId ->
                    repo.updateStepMessage(
                        id = rowId,
                        status = "cancelled",
                        content = "Stopped by user.",
                        success = false,
                        durationMs = null
                    )
                }
            }
            throw c
        }
    }
}
