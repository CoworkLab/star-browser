package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.agent.AgentDefaults
import com.example.agent.ExecutionMode
import com.example.agent.McpConnectorConfig
import com.example.agent.McpToolInfo
import com.example.agent.McpTransport
import com.example.agent.PendingApprovalAction
import com.example.agent.ToolRiskLevel
import com.example.data.local.AgentConversationEntity
import com.example.data.local.AgentMessageEntity
import com.example.data.local.BrowserTabEntity
import com.example.data.preferences.BrowserSettingsSnapshot
import com.example.sandbox.MobileSandboxManager
import com.example.ui.components.AgentView
import com.example.ui.components.BrowserView
import com.example.ui.components.SettingsOverlay
import com.example.ui.theme.StarBrowserTheme
import com.example.ui.viewmodel.WebTabRuntime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches Star Browser and agent title is Agent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Star Browser", context.getString(R.string.app_name))
        assertEquals("Agent", context.getString(R.string.agent_title))
    }

    @Test
    fun `default provider is keyless Kilo Code Gateway with reasoning`() {
        val kilo = AgentDefaults.defaultProviders.first()
        assertEquals("kilo-gateway", kilo.id)
        assertTrue(kilo.isKeyless)
        assertTrue(kilo.supportsReasoning)
        assertEquals("kilo-auto/free", kilo.modelId)
    }

    @Test
    fun `mobile sandbox isolates files and blocks path traversal`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sandbox = MobileSandboxManager(context)
        val writeRes = sandbox.writeFile("notes/summary.md", "# Hello Agent")
        assertTrue(writeRes.isSuccess)
        val readRes = sandbox.readFile("notes/summary.md")
        assertEquals("# Hello Agent", readRes.getOrNull())

        val traversalRes = sandbox.writeFile("../../escape.txt", "bad")
        assertTrue(traversalRes.isFailure)
        sandbox.deleteFile("notes/summary.md")
    }

    @Test
    fun `mcp connector serialization roundtrip preserves discovered tools`() {
        val connector = McpConnectorConfig(
            id = "conn_1",
            name = "DocsServer",
            endpointUrl = "https://example.com/mcp",
            transport = McpTransport.JSON_RPC_HTTP,
            discoveredTools = listOf(McpToolInfo("search_docs", "Search internal docs", "{}"))
        )
        val json = AgentDefaults.serializeConnectors(listOf(connector))
        val restored = AgentDefaults.deserializeConnectors(json)
        assertEquals(1, restored.size)
        assertEquals("search_docs", restored.first().discoveredTools.first().name)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun `composer omits lens icon, tools render live inline chips, and HITL card shows 3 actions`() {
        val noReasoningProvider = AgentDefaults.defaultProviders.first { !it.supportsReasoning }
        composeRule.setContent {
            StarBrowserTheme {
                AgentView(
                    conversations = listOf(AgentConversationEntity("c1", "Test Thread")),
                    activeConversationId = "c1",
                    messages = listOf(
                        AgentMessageEntity(
                            id = 1,
                            conversationId = "c1",
                            role = "step",
                            content = "Loaded YouTube",
                            toolName = "browser.open",
                            toolArgs = """{"url":"https://www.youtube.com"}""",
                            toolStatus = "completed",
                            toolSuccess = true,
                            toolDurationMs = 142L
                        ),
                        AgentMessageEntity(
                            id = 2,
                            conversationId = "c1",
                            role = "step",
                            content = "Captured refs",
                            toolName = "browser.snapshot",
                            toolStatus = "running",
                            toolSuccess = null
                        ),
                        AgentMessageEntity(id = 3, conversationId = "c1", role = "assistant", content = "Ready on page.")
                    ),
                    activeProvider = noReasoningProvider,
                    executionMode = ExecutionMode.APPROVE,
                    reasoningRequested = true,
                    isAgentRunning = false,
                    pendingApproval = PendingApprovalAction(
                        id = "p1",
                        callId = "call_9",
                        toolName = "sandbox.delete",
                        riskLevel = ToolRiskLevel.DESTRUCTIVE_OR_EXTERNAL,
                        summary = "Delete sandbox file notes.md",
                        targetUrlOrPath = "notes.md",
                        argumentsPreview = """{"path":"notes.md"}"""
                    ),
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
        composeRule.onNodeWithTag("agent_chat_title").assertIsDisplayed().assertTextEquals("Test Thread")
        composeRule.onNodeWithTag("composer_grab_page_button").assertDoesNotExist()
        composeRule.onNodeWithTag("composer_mic_button").assertIsDisplayed()

        // Both tool calls appear directly inline in conversation (not collapsed into one summary accordion)
        composeRule.onAllNodesWithTag("tool_runtime_chip").assertCountEquals(2)
        composeRule.onNodeWithText("Open Page").assertIsDisplayed()
        composeRule.onNodeWithText("youtube.com").assertIsDisplayed()
        composeRule.onNodeWithText("Inspect DOM").assertIsDisplayed()

        // Tap first chip to inspect raw output
        composeRule.onAllNodesWithTag("tool_runtime_chip").onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Loaded YouTube").assertIsDisplayed()

        // HITL approval card shows Deny / Allow Thread / Approve
        composeRule.onNodeWithTag("hitl_approval_card").assertIsDisplayed()
        composeRule.onNodeWithTag("hitl_deny_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hitl_allow_thread_button").assertIsDisplayed()
        composeRule.onNodeWithTag("hitl_approve_button").assertIsDisplayed()

        // Open '+' control sheet and confirm Reasoning row is hidden when provider doesn't support it
        composeRule.onNodeWithTag("composer_plus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mode_segment_auto").assertIsDisplayed()
        composeRule.onNodeWithTag("plus_sheet_reasoning_switch").assertDoesNotExist()
    }

    @Test
    fun `browser view shows single omnibox, no duplicate top strip or reader button, and Agent pill`() {
        composeRule.setContent {
            StarBrowserTheme {
                BrowserView(
                    activeTab = BrowserTabEntity(
                        id = "t1",
                        url = "https://developer.android.com/jetpack",
                        title = "Jetpack",
                        isActive = true
                    ),
                    tabCount = 2,
                    runtime = WebTabRuntime(canGoBack = true, canGoForward = false, isLoading = false),
                    history = emptyList(),
                    showFindBar = false,
                    findQuery = "",
                    onNavigateUrl = {},
                    onBack = {},
                    onForward = {},
                    onReloadOrStop = {},
                    onHome = {},
                    onOpenTabSwitcher = {},
                    onOpenMenu = {},
                    onSwitchToAgent = {},
                    onAskAgentCurrentPage = {},
                    onUpdateFindQuery = {},
                    onFindNext = {},
                    onCloseFindBar = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("developer.android.com").assertIsDisplayed()
        composeRule.onNodeWithTag("browser_reload_button").assertIsDisplayed()
        composeRule.onNodeWithTag("browser_ask_agent_pill").assertIsDisplayed()
        composeRule.onNodeWithText("Agent").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reader Mode").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp")
    fun `settings live search filters rows cleanly and opens full appearance setup`() {
        composeRule.setContent {
            StarBrowserTheme {
                SettingsOverlay(
                    settings = BrowserSettingsSnapshot(),
                    sandboxUsedBytes = 4096L,
                    onSetThemeMode = {},
                    onSetAdBlock = {},
                    onSetJavaScript = {},
                    onSetDesktopDefault = {},
                    onSetSearchEngine = {},
                    onOpenAgentStudio = {},
                    onOpenDownloads = {},
                    onClearBrowsingData = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_row_theme").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_row_theme").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance_mode_dark").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_mode_light").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_accent_lavender").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_search_input").performTextInput("shield")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_row_ad_block").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_row_theme").assertDoesNotExist()
    }
}
