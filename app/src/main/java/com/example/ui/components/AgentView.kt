package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.ApprovalDecision
import com.example.agent.ExecutionMode
import com.example.agent.LlmProviderConfig
import com.example.agent.PendingApprovalAction
import com.example.data.local.AgentConversationEntity
import com.example.data.local.AgentMessageEntity
import com.example.sandbox.SandboxFileEntry
import com.example.ui.theme.LocalStarSemanticColors
import com.example.ui.viewmodel.GrabbedPageContext
import kotlinx.coroutines.launch

@Composable
fun AgentView(
    conversations: List<AgentConversationEntity>,
    activeConversationId: String,
    messages: List<AgentMessageEntity>,
    activeProvider: LlmProviderConfig,
    executionMode: ExecutionMode,
    reasoningRequested: Boolean,
    isAgentRunning: Boolean,
    streamingReplyText: String = "",
    streamingReasoningText: String = "",
    pendingApproval: PendingApprovalAction?,
    grabbedPage: GrabbedPageContext?,
    hasAttachedImage: Boolean,
    attachedSandboxFiles: List<String>,
    sandboxFiles: List<SandboxFileEntry>,
    activeTabHost: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onModeChange: (ExecutionMode) -> Unit,
    onToggleReasoning: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopRun: () -> Unit,
    onResolveApproval: (ApprovalDecision) -> Unit,
    onGrabPageContext: () -> Unit,
    onClearGrabbedPage: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onToggleSandboxFile: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onOpenStudioProviders: () -> Unit,
    onOpenStudioRoot: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onSwitchToBrowser: () -> Unit,
    onOpenUrlInBrowser: (String) -> Unit,
    onCopyText: (String) -> Unit,
    voiceDraftText: String?,
    onConsumeVoiceDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val semantic = LocalStarSemanticColors.current

    val activeConversation = remember(conversations, activeConversationId) {
        conversations.firstOrNull { it.id == activeConversationId }
    }
    val headerTitle = activeConversation?.title?.takeIf {
        it.isNotBlank() && it != "New Conversation"
    } ?: "New Chat"

    LaunchedEffect(voiceDraftText) {
        if (!voiceDraftText.isNullOrBlank()) {
            inputText = if (inputText.isBlank()) voiceDraftText else "$inputText $voiceDraftText"
            onConsumeVoiceDraft()
        }
    }

    LaunchedEffect(messages.size, streamingReplyText.length, pendingApproval != null) {
        if (messages.isNotEmpty() || streamingReplyText.isNotEmpty()) {
            val targetIdx = (messages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIdx)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AgentHistoryDrawerContent(
                conversations = conversations,
                activeConversationId = activeConversationId,
                activeProvider = activeProvider,
                onSelectConversation = onSelectConversation,
                onNewConversation = onNewConversation,
                onDeleteConversation = onDeleteConversation,
                onOpenStudioProviders = onOpenStudioProviders,
                onOpenStudioRoot = onOpenStudioRoot,
                onOpenSettings = onOpenSettings,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("agent_drawer_button"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_menu),
                            contentDescription = stringResource(R.string.cd_open_threads),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("agent_chat_title")
                        )
                        if (executionMode != ExecutionMode.AUTO) {
                            Spacer(Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = executionMode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (messages.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = onNewConversation,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("header_new_chat_button"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_plus),
                                contentDescription = stringResource(R.string.cd_new_chat),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onSwitchToBrowser,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("switch_to_browser_button"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_globe),
                            contentDescription = stringResource(R.string.cd_switch_to_browser),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    AnimatedVisibility(visible = pendingApproval != null) {
                        pendingApproval?.let { approval ->
                            HumanInTheLoopCard(
                                action = approval,
                                onApprove = { onResolveApproval(ApprovalDecision.APPROVE_ONCE) },
                                onAllowForThread = { onResolveApproval(ApprovalDecision.ALLOW_FOR_THREAD) },
                                onDeny = { onResolveApproval(ApprovalDecision.DENY) }
                            )
                        }
                    }
                    Image4PillComposer(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        activeProvider = activeProvider,
                        executionMode = executionMode,
                        onModeChange = onModeChange,
                        reasoningRequested = reasoningRequested,
                        onToggleReasoning = onToggleReasoning,
                        isAgentRunning = isAgentRunning,
                        grabbedPage = grabbedPage,
                        hasAttachedImage = hasAttachedImage,
                        attachedSandboxFiles = attachedSandboxFiles,
                        sandboxFiles = sandboxFiles,
                        onGrabPageContext = onGrabPageContext,
                        onClearGrabbedPage = onClearGrabbedPage,
                        onPickImage = onPickImage,
                        onClearImage = onClearImage,
                        onToggleSandboxFile = onToggleSandboxFile,
                        onOpenProviderSettings = onOpenStudioProviders,
                        onStartVoiceInput = onStartVoiceInput,
                        onSend = {
                            val prompt = inputText
                            inputText = ""
                            onSendMessage(prompt)
                        },
                        onStop = onStopRun
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                semantic.subtleGlow,
                                MaterialTheme.colorScheme.background
                            ),
                            radius = 950f
                        )
                    )
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                if (messages.isEmpty() && !isAgentRunning) {
                    EmptyAgentState(
                        activeTabHost = activeTabHost,
                        onQuickPrompt = { prompt -> onSendMessage(prompt) },
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                            .testTag("agent_chat_feed"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { msg -> "msg_${msg.id}" }) { msg ->
                            when (msg.role) {
                                "user" -> UserMessageBubble(content = msg.content)
                                "step" -> ToolCallRuntimeCard(step = msg)
                                else -> AgentResponseBubble(
                                    msg = msg,
                                    onOpenUrl = onOpenUrlInBrowser,
                                    onCopy = onCopyText
                                )
                            }
                        }

                        if (isAgentRunning) {
                            item(key = "streaming_live_turn") {
                                StreamingTurnBubble(
                                    streamingReplyText = streamingReplyText,
                                    streamingReasoningText = streamingReasoningText,
                                    onOpenUrl = onOpenUrlInBrowser,
                                    onCopy = onCopyText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAgentState(
    activeTabHost: String,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StarCrestMark(size = 52.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.agent_empty_headline),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.agent_empty_subhead),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 360.dp)
        )
        Spacer(Modifier.height(24.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val starters = listOfNotNull(
                if (activeTabHost.isNotBlank()) "Summarize $activeTabHost" else null,
                "Open Wikipedia and search Kotlin Coroutines",
                "Open YouTube and find Android Material 3 design"
            )
            starters.forEach { prompt ->
                SuggestionChip(
                    onClick = { onQuickPrompt(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(100.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}
