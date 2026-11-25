package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.agent.AgentDefaults
import com.example.agent.AgentHostBridge
import com.example.agent.BrainMessage
import com.example.agent.BrainTurnOutput
import com.example.agent.ExecutionMode
import com.example.agent.LlmBrain
import com.example.agent.LlmProviderConfig
import com.example.agent.MultiProviderBrain
import com.example.agent.StructuredToolCall
import com.example.agent.ToolDefinition
import com.example.agent.ToolkitId
import com.example.agent.TrueAgentEngine
import com.example.sandbox.MobileSandboxManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrueAgentEngineTest {

    private class ScriptedBrain(
        private val turns: List<BrainTurnOutput>
    ) : LlmBrain {
        var callCount = 0
        val recordedToolResults = mutableListOf<BrainMessage.ToolResult>()

        override suspend fun runAgentTurn(
            provider: LlmProviderConfig,
            messages: List<BrainMessage>,
            tools: List<ToolDefinition>,
            enableReasoning: Boolean
        ): Result<BrainTurnOutput> {
            recordedToolResults.clear()
            recordedToolResults.addAll(messages.filterIsInstance<BrainMessage.ToolResult>())
            val out = turns.getOrElse(callCount) { turns.last() }
            callCount++
            return Result.success(out)
        }

        override suspend fun testConnection(provider: LlmProviderConfig): Result<String> =
            Result.success("OK")
    }

    private class FakeHostBridge(
        private val onNavigate: suspend (String, Boolean) -> Result<String> = { url, _ ->
            Result.success("Loaded '$url'")
        }
    ) : AgentHostBridge {
        val navigatedUrls = mutableListOf<String>()
        var domJsCalls = 0

        override fun currentUrl(): String = navigatedUrls.lastOrNull() ?: "about:blank"
        override fun currentTitle(): String = "Test Page"

        override suspend fun navigateAndWait(url: String, newTab: Boolean): Result<String> {
            navigatedUrls.add(url)
            return onNavigate(url, newTab)
        }

        override suspend fun goBackAndWait(): Result<String> = Result.success("Back OK")
        override suspend fun goForwardAndWait(): Result<String> = Result.success("Forward OK")
        override suspend fun reloadAndWait(): Result<String> = Result.success("Reload OK")
        override suspend fun evaluateDomJs(script: String): Result<String> {
            domJsCalls++
            return Result.success("{\"title\":\"YouTube\",\"url\":\"https://www.youtube.com\",\"elements\":[],\"text\":\"Video home\"}")
        }
        override suspend fun captureViewportBase64(): String? = null
        override fun copyToClipboard(text: String) {}
    }

    private fun runEngine(brain: LlmBrain, host: AgentHostBridge) = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sandbox = MobileSandboxManager(context)
        val engine = TrueAgentEngine(brain = brain, host = host, sandbox = sandbox)
        engine.run(
            userPrompt = "Open YouTube",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = setOf(ToolkitId.BROWSER, ToolkitId.SANDBOX, ToolkitId.WEB_FETCH, ToolkitId.DEVICE),
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()
    }

    @Test
    fun `scenario 1 and 4 - user requests opening YouTube executes browser open and confirms success`() = runTest {
        val host = FakeHostBridge()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall(
                            id = "call_1",
                            name = "browser_open",
                            arguments = JSONObject().put("url", "https://www.youtube.com").put("new_tab", false)
                        )
                    )
                ),
                BrainTurnOutput.FinalText("I opened YouTube for you.")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Open YouTube",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertEquals(listOf("https://www.youtube.com"), host.navigatedUrls)
        assertTrue(res.executedTools.single().success)
        assertTrue(res.finalText.contains("opened YouTube", ignoreCase = true))
    }

    @Test
    fun `scenario 2 - fake tool JSON in assistant text does not execute browser tool`() = runTest {
        val host = FakeHostBridge()
        val fakeJsonText = """
            I'll open YouTube for you right away.
            {"name": "browser.open", "parameters": {"url": "https://www.youtube.com", "new_tab": false}}
        """.trimIndent()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.FinalText(fakeJsonText),
                BrainTurnOutput.FinalText(fakeJsonText)
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Open YouTube",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertTrue(host.navigatedUrls.isEmpty())
        assertTrue(res.executedTools.isEmpty())
        assertTrue(res.finalText.contains("No action was executed", ignoreCase = true))
    }

    @Test
    fun `scenario 3 - browser tool failure prevents false completion claim`() = runTest {
        val host = FakeHostBridge(
            onNavigate = { _, _ -> Result.failure(IllegalStateException("ERR_NAME_NOT_RESOLVED")) }
        )
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall(
                            id = "c1",
                            name = "browser_open",
                            arguments = JSONObject().put("url", "https://www.youtube.com")
                        )
                    )
                ),
                BrainTurnOutput.FinalText("I have opened YouTube for you!")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Open YouTube",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertFalse(res.executedTools.single().success)
        assertTrue(res.finalText.contains("did not complete", ignoreCase = true))
        assertTrue(res.finalText.contains("ERR_NAME_NOT_RESOLVED"))
    }

    @Test
    fun `scenario 5 - unknown tool request is rejected safely and fed back to model`() = runTest {
        val host = FakeHostBridge()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c_unknown", "shell.exec", JSONObject().put("cmd", "rm -rf"))
                    )
                ),
                BrainTurnOutput.FinalText("That tool is unavailable.")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Run shell",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertFalse(res.executedTools.single().success)
        assertTrue(res.executedTools.single().outputOrError.contains("Unknown tool"))
        assertTrue(brain.recordedToolResults.single().resultJson.contains("\"ok\":false"))
    }

    @Test
    fun `scenario 6 - invalid tool arguments are rejected without invoking host`() = runTest {
        val host = FakeHostBridge()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c_bad", "browser_open", JSONObject().put("url", "javascript:alert(1)"))
                    )
                ),
                BrainTurnOutput.FinalText("Invalid URL was rejected.")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Run JS",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertTrue(host.navigatedUrls.isEmpty())
        assertFalse(res.executedTools.single().success)
        assertTrue(res.executedTools.single().outputOrError.contains("unsafe", ignoreCase = true))
    }

    @Test
    fun `scenario 7 - hanging tool triggers timeout and prevents completion claim`() = runTest {
        val host = FakeHostBridge(
            onNavigate = { _, _ ->
                delay(25_000L)
                Result.success("Too late")
            }
        )
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c_hang", "browser_open", JSONObject().put("url", "https://example.com"))
                    )
                ),
                BrainTurnOutput.FinalText("Opened example.com!")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(brain, host, MobileSandboxManager(context))
        val res = engine.run(
            userPrompt = "Open example.com",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertTrue(res.executedTools.single().timedOut)
        assertFalse(res.executedTools.single().success)
        assertTrue(res.finalText.contains("timed out", ignoreCase = true))
    }

    @Test
    fun `scenario 8 - multiple batched and sequential tool calls execute and feed results back`() = runTest {
        val host = FakeHostBridge()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c1", "browser_open", JSONObject().put("url", "https://www.youtube.com")),
                        StructuredToolCall("c2", "browser_snapshot", JSONObject())
                    )
                ),
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c3", "sandbox_write", JSONObject().put("path", "yt.txt").put("content", "YouTube home"))
                    )
                ),
                BrainTurnOutput.FinalText("Opened YouTube, captured snapshot, and saved yt.txt.")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sandbox = MobileSandboxManager(context, StandardTestDispatcher(testScheduler))
        val engine = TrueAgentEngine(brain, host, sandbox)
        val res = engine.run(
            userPrompt = "Open YouTube, inspect it, and save note",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.AUTO,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApproval = { true },
            onStepExecuted = {}
        ).getOrThrow()

        assertEquals(3, res.executedTools.size)
        assertTrue(
            "Expected all tools to succeed, but failed: " +
                res.executedTools.filterNot { it.success }.joinToString { "${it.toolName}: ${it.outputOrError} (timedOut=${it.timedOut})" },
            res.executedTools.all { it.success }
        )
        assertEquals(1, host.domJsCalls)
        assertEquals("YouTube home", sandbox.readFile("yt.txt").getOrNull())
        sandbox.deleteFile("yt.txt")
    }

    @Test
    fun `structured parsers separate text from native tool_use and functionCall across dialects`() {
        val openAiJson = """
            {
              "choices": [{
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call_abc",
                    "type": "function",
                    "function": { "name": "browser_open", "arguments": "{\"url\":\"https://youtube.com\"}" }
                  }]
                }
              }]
            }
        """.trimIndent()
        val parsedOpenAi = MultiProviderBrain.parseOpenAiResponse(openAiJson) as BrainTurnOutput.ToolCalls
        assertEquals("browser_open", parsedOpenAi.calls.single().name)

        val anthropicJson = """
            {
              "content": [
                {"type": "thinking", "thinking": "Need to open URL"},
                {"type": "tool_use", "id": "toolu_1", "name": "browser_open", "input": {"url": "https://youtube.com"}}
              ]
            }
        """.trimIndent()
        val parsedAnthropic = MultiProviderBrain.parseAnthropicResponse(anthropicJson) as BrainTurnOutput.ToolCalls
        assertEquals("Need to open URL", parsedAnthropic.reasoning)
        assertEquals("browser_open", parsedAnthropic.calls.single().name)

        val geminiJson = """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    {"text": "Checking page", "thought": true},
                    {"functionCall": {"name": "browser_snapshot", "args": {}}}
                  ]
                }
              }]
            }
        """.trimIndent()
        val parsedGemini = MultiProviderBrain.parseGeminiResponse(geminiJson) as BrainTurnOutput.ToolCalls
        assertEquals("Checking page", parsedGemini.reasoning)
        assertEquals("browser_snapshot", parsedGemini.calls.single().name)
    }

    @Test
    fun `conversational prompts omit heavy tool schemas while browser prompts include them`() {
        val chatTools = com.example.agent.ToolRegistry.selectToolsForTurn(
            userPrompt = "Explain Kotlin coroutines simply",
            hasActiveWebpage = false,
            hasGrabbedPage = false,
            hasAttachments = false,
            mode = ExecutionMode.AUTO,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true
        )
        assertTrue(chatTools.isEmpty())

        val browserTools = com.example.agent.ToolRegistry.selectToolsForTurn(
            userPrompt = "Open YouTube and search Material 3",
            hasActiveWebpage = false,
            hasGrabbedPage = false,
            hasAttachments = false,
            mode = ExecutionMode.AUTO,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true
        )
        assertTrue(browserTools.any { it.canonicalName == "browser.open" })
        assertFalse(browserTools.any { it.canonicalName == "sandbox.write" })
    }

    @Test
    fun `HITL lifecycle emits started, awaiting approval, supports Allow Thread, and respects Deny`() = runTest {
        val host = FakeHostBridge()
        val brain = ScriptedBrain(
            listOf(
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c1", "sandbox_write", JSONObject().put("path", "a.txt").put("content", "hi")),
                        StructuredToolCall("c2", "sandbox_write", JSONObject().put("path", "b.txt").put("content", "yo"))
                    )
                ),
                BrainTurnOutput.ToolCalls(
                    listOf(
                        StructuredToolCall("c3", "sandbox_delete", JSONObject().put("path", "a.txt"))
                    )
                ),
                BrainTurnOutput.FinalText("Wrote files and respected delete denial.")
            )
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sandbox = MobileSandboxManager(context, StandardTestDispatcher(testScheduler))
        val engine = TrueAgentEngine(brain, host, sandbox)

        val startedCalls = mutableListOf<String>()
        val awaitedCalls = mutableListOf<String>()

        val res = engine.run(
            userPrompt = "Write a.txt and b.txt then delete a.txt",
            history = emptyList(),
            provider = AgentDefaults.defaultProviders.first(),
            systemPrompt = AgentDefaults.defaultPrompts.first(),
            activeSkills = emptyList(),
            mode = ExecutionMode.APPROVE,
            enableReasoning = false,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true,
            requestApprovalDecision = { action ->
                when (action.toolName) {
                    "sandbox.write" -> com.example.agent.ApprovalDecision.ALLOW_FOR_THREAD
                    "sandbox.delete" -> com.example.agent.ApprovalDecision.DENY
                    else -> com.example.agent.ApprovalDecision.APPROVE_ONCE
                }
            },
            onToolStarted = { startedCalls.add(it.callId) },
            onToolAwaitingApproval = { awaitedCalls.add(it.callId) },
            onStepExecuted = {}
        ).getOrThrow()

        assertEquals(listOf("c1", "c2", "c3"), startedCalls)
        // c2 was auto-approved after c1 returned ALLOW_FOR_THREAD; c3 prompted and was denied
        assertEquals(listOf("c1", "c3"), awaitedCalls)
        assertTrue(res.executedTools[0].success)
        assertTrue(res.executedTools[1].success)
        assertFalse(res.executedTools[2].success)
        assertTrue(res.executedTools[2].skippedByUser)
        assertEquals("hi", sandbox.readFile("a.txt").getOrNull())
        sandbox.deleteFile("a.txt")
        sandbox.deleteFile("b.txt")
    }
}
