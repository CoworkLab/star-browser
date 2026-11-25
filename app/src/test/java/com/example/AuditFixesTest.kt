package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent.AgentDefaults
import com.example.agent.AgentHostBridge
import com.example.agent.BrainMessage
import com.example.agent.BrainTurnOutput
import com.example.agent.ExecutionMode
import com.example.agent.FakeToolCallDetector
import com.example.agent.LlmBrain
import com.example.agent.LlmProviderConfig
import com.example.agent.ProviderDialect
import com.example.agent.ProviderDialectClients
import com.example.agent.ToolDefinition
import com.example.agent.ToolExecutionRecord
import com.example.agent.ToolRegistry
import com.example.agent.ToolValidationResult
import com.example.agent.TrueAgentEngine
import com.example.data.local.AgentConversationEntity
import com.example.data.local.AgentMessageEntity
import com.example.data.local.BrowserTabEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.StarDatabase
import com.example.data.local.StarRepository
import com.example.data.preferences.SearchEngine
import com.example.sandbox.MobileSandboxManager
import com.example.ui.components.AgentView
import com.example.ui.components.DownloadsSheet
import com.example.ui.theme.StarBrowserTheme
import com.example.ui.viewmodel.BrowserUrlAndMediaHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditFixesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `openai dialect omits reasoning parameter for OpenAI endpoints but sends it for OpenRouter and Kilo`() {
        assertFalse(ProviderDialectClients.supportsOpenRouterReasoningParam("https://api.openai.com/v1/chat/completions"))
        assertTrue(ProviderDialectClients.supportsOpenRouterReasoningParam("https://openrouter.ai/api/v1/chat/completions"))
        assertTrue(ProviderDialectClients.supportsOpenRouterReasoningParam("https://api.kilo.ai/api/gateway/chat/completions"))

        val openAiConfig = LlmProviderConfig(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            modelId = "gpt-4.1-mini",
            dialect = ProviderDialect.OPENAI_COMPAT,
            supportsReasoning = true
        )
        val openAiPayload = ProviderDialectClients.buildOpenAiChatPayload(
            provider = openAiConfig,
            messages = listOf(BrainMessage.User("Hello")),
            tools = emptyList(),
            enableReasoning = false,
            allowVision = true
        )
        assertFalse(openAiPayload.has("reasoning"))

        val openRouterConfig = openAiConfig.copy(
            id = "openrouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions"
        )
        val openRouterPayload = ProviderDialectClients.buildOpenAiChatPayload(
            provider = openRouterConfig,
            messages = listOf(BrainMessage.User("Hello")),
            tools = emptyList(),
            enableReasoning = false,
            allowVision = true
        )
        assertTrue(openRouterPayload.has("reasoning"))
        assertEquals("none", openRouterPayload.getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun `gemini parser generates unique call ids and preserves thoughtSignature across turns`() {
        val rawGemini = """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    {
                      "functionCall": {"name": "browser_open", "args": {"url": "https://example.com"}},
                      "thoughtSignature": "sig_alpha_123"
                    },
                    {
                      "functionCall": {"name": "browser_snapshot", "args": {}},
                      "thoughtSignature": "sig_beta_456"
                    }
                  ]
                }
              }]
            }
        """.trimIndent()

        val parsed = ProviderDialectClients.parseGeminiResponse(rawGemini) as BrainTurnOutput.ToolCalls
        assertEquals(2, parsed.calls.size)
        assertNotEquals(parsed.calls[0].id, parsed.calls[1].id)
        assertTrue(parsed.calls[0].id.startsWith("gemini_"))
        assertEquals("sig_alpha_123", parsed.calls[0].thoughtSignature)
        assertEquals("sig_beta_456", parsed.calls[1].thoughtSignature)

        val geminiProvider = AgentDefaults.defaultProviders.first { it.dialect == ProviderDialect.GEMINI_REST }
        val payload = ProviderDialectClients.buildGeminiGeneratePayload(
            provider = geminiProvider,
            messages = listOf(
                BrainMessage.User("Open and inspect example.com"),
                BrainMessage.AssistantToolCalls(calls = parsed.calls, text = null),
                BrainMessage.ToolResult(parsed.calls[0].id, "browser_open", """{"ok":true}"""),
                BrainMessage.ToolResult(parsed.calls[1].id, "browser_snapshot", """{"ok":true}""")
            ),
            tools = emptyList(),
            enableReasoning = true,
            allowVision = false
        )
        val contents = payload.getJSONArray("contents")
        val modelTurn = contents.getJSONObject(1)
        assertEquals("model", modelTurn.getString("role"))
        val modelParts = modelTurn.getJSONArray("parts")
        assertEquals("sig_alpha_123", modelParts.getJSONObject(0).getString("thoughtSignature"))
        assertEquals("sig_beta_456", modelParts.getJSONObject(1).getString("thoughtSignature"))
    }

    @Test
    fun `CancellationException from LlmBrain propagates instead of returning Result failure`() = runTest {
        val cancellingBrain = object : LlmBrain {
            override suspend fun runAgentTurn(
                provider: LlmProviderConfig,
                messages: List<BrainMessage>,
                tools: List<ToolDefinition>,
                enableReasoning: Boolean
            ): Result<BrainTurnOutput> {
                throw CancellationException("Stopped by user")
            }

            override suspend fun testConnection(provider: LlmProviderConfig): Result<String> = Result.success("OK")
        }
        val dummyHost = object : AgentHostBridge {
            override fun currentUrl(): String = "about:blank"
            override fun currentTitle(): String = "New Tab"
            override suspend fun navigateAndWait(url: String, newTab: Boolean): Result<String> = Result.success("OK")
            override suspend fun goBackAndWait(): Result<String> = Result.success("OK")
            override suspend fun goForwardAndWait(): Result<String> = Result.success("OK")
            override suspend fun reloadAndWait(): Result<String> = Result.success("OK")
            override suspend fun evaluateDomJs(script: String): Result<String> = Result.success("{}")
            override suspend fun captureViewportBase64(): String? = null
            override fun copyToClipboard(text: String) {}
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TrueAgentEngine(cancellingBrain, dummyHost, MobileSandboxManager(context))
        var threwCancellation = false
        try {
            engine.run(
                userPrompt = "Hello",
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
            )
        } catch (_: CancellationException) {
            threwCancellation = true
        }
        assertTrue(threwCancellation)
    }

    @Test
    fun `tool routing omits tools for general chat even with active webpage, and allows localhost`() {
        val generalOnPage = ToolRegistry.selectToolsForTurn(
            userPrompt = "What is the capital of France?",
            hasActiveWebpage = true,
            hasGrabbedPage = false,
            hasAttachments = false,
            mode = ExecutionMode.AUTO,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true
        )
        assertTrue(generalOnPage.isEmpty())

        val pageIntentTools = ToolRegistry.selectToolsForTurn(
            userPrompt = "Summarize this article for me",
            hasActiveWebpage = true,
            hasGrabbedPage = false,
            hasAttachments = false,
            mode = ExecutionMode.AUTO,
            enabledToolkits = AgentDefaults.defaultEnabledToolkits,
            mcpConnectors = emptyList(),
            sandboxInternetEnabled = true
        )
        assertTrue(pageIntentTools.any { it.canonicalName == "browser.snapshot" })

        val localhostValidation = ToolRegistry.validateToolCall(
            callId = "c_local",
            rawName = "browser_open",
            arguments = JSONObject().put("url", "http://localhost:3000/dashboard"),
            availableTools = ToolRegistry.builtInTools
        )
        assertTrue(localhostValidation is ToolValidationResult.Valid)

        val loopbackValidation = ToolRegistry.validateToolCall(
            callId = "c_loopback",
            rawName = "browser_open",
            arguments = JSONObject().put("url", "http://127.0.0.1:8080"),
            availableTools = ToolRegistry.builtInTools
        )
        assertTrue(loopbackValidation is ToolValidationResult.Valid)

        val unsafeValidation = ToolRegistry.validateToolCall(
            callId = "c_unsafe",
            rawName = "browser_open",
            arguments = JSONObject().put("url", "javascript:alert(1)"),
            availableTools = ToolRegistry.builtInTools
        )
        assertTrue(unsafeValidation is ToolValidationResult.Invalid)
    }

    @Test
    fun `FakeToolCallDetector distinguishes honest failure replies from false success claims`() {
        val failedRecord = listOf(
            ToolExecutionRecord(
                callId = "c1",
                toolName = "browser.open",
                argumentsJson = """{"url":"https://www.youtube.com"}""",
                success = false,
                timedOut = false,
                outputOrError = "DNS error",
                durationMs = 50L
            )
        )
        assertFalse(FakeToolCallDetector.claimsSuccessDespiteFailures("I couldn't open that page because DNS failed.", failedRecord))
        assertFalse(FakeToolCallDetector.claimsSuccessDespiteFailures("Sorry, it is not possible to access that site.", failedRecord))
        assertTrue(FakeToolCallDetector.claimsSuccessDespiteFailures("I have opened YouTube for you!", failedRecord))
    }

    @Test
    fun `Room database orders tabs by createdAt, persists downloads, and recovers interrupted steps`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = StarRepository(db)
            repo.upsertTab(
                BrowserTabEntity(
                    id = "tab_1",
                    url = "https://first.example",
                    title = "First",
                    isActive = false,
                    updatedAt = 9000L,
                    createdAt = 1000L
                )
            )
            repo.upsertTab(
                BrowserTabEntity(
                    id = "tab_2",
                    url = "https://second.example",
                    title = "Second",
                    isActive = true,
                    updatedAt = 5000L,
                    createdAt = 2000L
                )
            )
            val tabs = repo.tabs.first()
            assertEquals(listOf("tab_1", "tab_2"), tabs.map { it.id })

            repo.upsertConversation(AgentConversationEntity(id = "conv_1", title = "Audit"))
            repo.insertMessage(
                AgentMessageEntity(
                    conversationId = "conv_1",
                    role = "step",
                    content = "",
                    toolName = "browser.open",
                    toolStatus = "running"
                )
            )
            repo.insertMessage(
                AgentMessageEntity(
                    conversationId = "conv_1",
                    role = "step",
                    content = "Awaiting user decision",
                    toolName = "sandbox.delete",
                    toolStatus = "awaiting_approval"
                )
            )
            repo.recoverInterruptedSteps()
            val msgs = repo.getMessagesOnce("conv_1")
            assertEquals(2, msgs.size)
            assertTrue(msgs.all { it.toolStatus == "cancelled" && it.toolSuccess == false })
            assertEquals("Interrupted before completion.", msgs[0].content)

            val dlId = repo.recordDownload(
                systemDownloadId = 77L,
                fileName = "guide.pdf",
                url = "https://example.com/guide.pdf",
                mimeType = "application/pdf"
            )
            assertEquals(1, repo.downloads.first().size)
            repo.deleteDownload(dlId)
            assertTrue(repo.downloads.first().isEmpty())
            repo.recordDownload(78L, "a.zip", "https://example.com/a.zip", "application/zip")
            repo.clearDownloads()
            assertTrue(repo.downloads.first().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `BrowserUrlAndMediaHelper handles localhost URLs, fallback titles, and downscales large bitmaps`() {
        assertEquals(
            "http://localhost:8080/api",
            BrowserUrlAndMediaHelper.resolveInputToUrl("localhost:8080/api", SearchEngine.GOOGLE)
        )
        assertEquals(
            "http://127.0.0.1:3000",
            BrowserUrlAndMediaHelper.resolveInputToUrl("127.0.0.1:3000", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://kotlinlang.org",
            BrowserUrlAndMediaHelper.resolveInputToUrl("kotlinlang.org", SearchEngine.GOOGLE)
        )
        assertTrue(
            BrowserUrlAndMediaHelper.resolveInputToUrl("compose material 3", SearchEngine.DUCKDUCKGO)
                .startsWith("https://duckduckgo.com/?q=")
        )
        assertEquals(
            "Open YouTube music",
            BrowserUrlAndMediaHelper.buildFallbackIntentTitle("please open YouTube music", null)
        )

        val largeBmp = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        val rawBytes = ByteArrayOutputStream().use { out ->
            largeBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            largeBmp.recycle()
            out.toByteArray()
        }
        val encoded = BrowserUrlAndMediaHelper.compressImageForVision(rawBytes, maxEdgePx = 1280)
        assertNotNull(encoded)
        val decodedBytes = Base64.decode(encoded!!, Base64.NO_WRAP)
        val outBmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        assertNotNull(outBmp)
        assertTrue("Expected max edge <= 1280, was ${outBmp.width}x${outBmp.height}", max(outBmp.width, outBmp.height) <= 1280)
        outBmp.recycle()
    }

    @Test
    fun `UI renders cancelled step pill and populated DownloadsSheet`() {
        composeRule.setContent {
            StarBrowserTheme {
                AgentView(
                    conversations = listOf(AgentConversationEntity("c1", "Cancelled Thread")),
                    activeConversationId = "c1",
                    messages = listOf(
                        AgentMessageEntity(
                            id = 1,
                            conversationId = "c1",
                            role = "step",
                            content = "Interrupted before completion.",
                            toolName = "browser.open",
                            toolStatus = "cancelled",
                            toolSuccess = false
                        )
                    ),
                    activeProvider = AgentDefaults.defaultProviders.first(),
                    executionMode = ExecutionMode.AUTO,
                    reasoningRequested = false,
                    isAgentRunning = false,
                    pendingApproval = null,
                    grabbedPage = null,
                    hasAttachedImage = false,
                    attachedSandboxFiles = emptyList(),
                    sandboxFiles = emptyList(),
                    activeTabHost = "example.com",
                    onSelectConversation = {},
                    onNewConversation = {},
                    onDeleteConversation = {},
                    onModeChange = {},
                    onToggleReasoning = {},
                    onSendMessage = {},
                    onStopRun = {},
                    onResolveApproval = {},
                    onGrabPageContext = {},
                    onClearGrabbedPage = {},
                    onPickImage = {},
                    onClearImage = {},
                    onToggleSandboxFile = {},
                    onStartVoiceInput = {},
                    onOpenStudioProviders = {},
                    onOpenStudioRoot = {},
                    onSwitchToBrowser = {},
                    onOpenUrlInBrowser = {},
                    onCopyText = {},
                    voiceDraftText = null,
                    onConsumeVoiceDraft = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    @Test
    fun `DownloadsSheet displays downloaded items and Clear list action`() {
        composeRule.setContent {
            StarBrowserTheme {
                DownloadsSheet(
                    downloads = listOf(
                        DownloadEntity(
                            id = 1L,
                            systemDownloadId = 11L,
                            fileName = "release-notes.pdf",
                            url = "https://example.com/release-notes.pdf",
                            mimeType = "application/pdf"
                        )
                    ),
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("release-notes.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("Clear list").assertIsDisplayed()
    }
}
