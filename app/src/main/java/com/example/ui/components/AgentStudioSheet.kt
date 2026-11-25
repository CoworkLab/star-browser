package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.AgentSkill
import com.example.agent.LlmProviderConfig
import com.example.agent.McpConnectorConfig
import com.example.agent.ProviderDialect
import com.example.agent.SystemPromptPreset
import com.example.agent.ToolkitId
import com.example.data.preferences.BrowserSettingsSnapshot
import com.example.sandbox.SafEntry
import com.example.sandbox.SandboxFileEntry
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStarSemanticColors
import com.example.ui.viewmodel.StudioTab
import java.util.UUID

@Composable
fun AgentStudioSheet(
    initialTab: StudioTab,
    settings: BrowserSettingsSnapshot,
    providerTestStatus: String?,
    sandboxFiles: List<SandboxFileEntry>,
    sandboxUsedBytes: Long,
    linkedSafLabel: String?,
    safFiles: List<SafEntry> = emptyList(),
    onSelectProvider: (String) -> Unit,
    onSaveProvider: (LlmProviderConfig) -> Unit,
    onTestProvider: (LlmProviderConfig) -> Unit,
    onSelectPrompt: (String) -> Unit,
    onSavePrompt: (SystemPromptPreset) -> Unit,
    onToggleSkill: (String) -> Unit,
    onAddSkill: (AgentSkill) -> Unit,
    onToggleToolkit: (ToolkitId) -> Unit,
    onSaveConnector: (McpConnectorConfig) -> Unit,
    onToggleConnector: (String) -> Unit,
    onDeleteConnector: (String) -> Unit,
    onSetSandboxInternet: (Boolean) -> Unit,
    onWriteSandboxFile: (String, String) -> Unit,
    onDeleteSandboxFile: (String) -> Unit,
    onReadSandboxFile: suspend (String) -> Result<String>,
    onRequestLinkSafFolder: () -> Unit,
    onUnlinkSafFolder: () -> Unit,
    onImportSafFile: (SafEntry) -> Unit = {},
    onExportSandboxFile: (String) -> Unit = {},
    onRequestMicCheck: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTryInChat: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Agent Studio",
                        style = MaterialTheme.typography.titleLarge
                    )

                    FilledTonalIconButton(
                        onClick = onOpenAppSettings,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_settings),
                            contentDescription = "Android App Settings",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = { Text(tab.label) },
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.testTag("studio_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                when (activeTab) {
                    StudioTab.PROVIDERS -> ProvidersTabContent(
                        providers = settings.providers,
                        activeProviderId = settings.activeProviderId,
                        testStatus = providerTestStatus,
                        onSelectProvider = onSelectProvider,
                        onSaveProvider = onSaveProvider,
                        onTestProvider = onTestProvider,
                        onTryInChat = onTryInChat
                    )

                    StudioTab.PROMPTS_SKILLS -> PromptsAndSkillsTabContent(
                        prompts = settings.prompts,
                        activePromptId = settings.activePromptId,
                        skills = settings.skills,
                        onSelectPrompt = onSelectPrompt,
                        onSavePrompt = onSavePrompt,
                        onToggleSkill = onToggleSkill,
                        onAddSkill = onAddSkill
                    )

                    StudioTab.TOOLS_MCP -> ToolsAndMcpTabContent(
                        enabledToolkits = settings.enabledToolkits,
                        connectors = settings.mcpConnectors,
                        onToggleToolkit = onToggleToolkit,
                        onSaveConnector = onSaveConnector,
                        onToggleConnector = onToggleConnector,
                        onDeleteConnector = onDeleteConnector
                    )

                    StudioTab.SANDBOX -> SandboxTabContent(
                        sandboxFiles = sandboxFiles,
                        sandboxUsedBytes = sandboxUsedBytes,
                        sandboxInternetEnabled = settings.sandboxInternetEnabled,
                        linkedSafLabel = linkedSafLabel,
                        safFiles = safFiles,
                        onSetSandboxInternet = onSetSandboxInternet,
                        onWriteFile = onWriteSandboxFile,
                        onDeleteFile = onDeleteSandboxFile,
                        onReadFile = onReadSandboxFile,
                        onRequestLinkSaf = onRequestLinkSafFolder,
                        onUnlinkSaf = onUnlinkSafFolder,
                        onImportSafFile = onImportSafFile,
                        onExportSandboxFile = onExportSandboxFile,
                        onRequestMicCheck = onRequestMicCheck,
                        onOpenAppSettings = onOpenAppSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvidersTabContent(
    providers: List<LlmProviderConfig>,
    activeProviderId: String,
    testStatus: String?,
    onSelectProvider: (String) -> Unit,
    onSaveProvider: (LlmProviderConfig) -> Unit,
    onTestProvider: (LlmProviderConfig) -> Unit,
    onTryInChat: () -> Unit
) {
    val active = providers.firstOrNull { it.id == activeProviderId } ?: providers.first()
    val semantic = LocalStarSemanticColors.current
    var editingProvider by remember { mutableStateOf<LlmProviderConfig?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_cpu),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active.name,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${active.dialect.label} · ${if (active.isKeyless) "Keyless Ready" else "API Key"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(semantic.studioAccentCardStart, semantic.studioAccentCardEnd)
                            )
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioNestedActionCard(
                            title = "@${active.modelId}",
                            body = "Tap to edit endpoint URL, model ID, or optional API key.",
                            onClick = { editingProvider = active }
                        )
                        StudioNestedActionCard(
                            title = "Reasoning Support: ${if (active.supportsReasoning) "Enabled" else "Off"}",
                            body = if (active.supportsReasoning) {
                                "Quick Reasoning toggle is available in the chat composer + menu."
                            } else {
                                "Reasoning toggle stays hidden in chat while disabled here."
                            },
                            onClick = {
                                onSaveProvider(active.copy(supportsReasoning = !active.supportsReasoning))
                            }
                        )
                        StudioNestedActionCard(
                            title = "Vision Support: ${if (active.supportsVision) "Enabled" else "Off"}",
                            body = "Controls whether images and viewport captures are sent to ${active.name}.",
                            onClick = {
                                onSaveProvider(active.copy(supportsVision = !active.supportsVision))
                            }
                        )
                    }
                }

                if (!testStatus.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = testStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTestProvider(active) },
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Test Connection")
                    }
                    Button(
                        onClick = onTryInChat,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Try in chat", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Providers",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = {
                    editingProvider = LlmProviderConfig(
                        id = "custom_${UUID.randomUUID().toString().take(6)}",
                        name = "Custom Provider",
                        baseUrl = "https://",
                        modelId = "",
                        isKeyless = true,
                        supportsReasoning = false,
                        supportsVision = false,
                        isBuiltIn = false
                    )
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add Custom")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column {
                    providers.forEachIndexed { index, provider ->
                        val selected = provider.id == activeProviderId
                        ListItem(
                            headlineContent = {
                                Text(provider.name, fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(
                                    "${provider.modelId} · ${if (provider.supportsReasoning) "Reasoning" else "Standard"}${if (provider.supportsVision) " · Vision" else ""}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(
                                        if (selected) R.drawable.ic_modern_check else R.drawable.ic_modern_key
                                    ),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { editingProvider = provider }) {
                                    Text("Edit")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable { onSelectProvider(provider.id) }
                                .testTag("provider_item_${provider.id}")
                        )
                        if (index < providers.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    editingProvider?.let { draft ->
        ProviderEditorDialog(
            initial = draft,
            onSave = {
                onSaveProvider(it)
                editingProvider = null
            },
            onDismiss = { editingProvider = null }
        )
    }
}

@Composable
private fun StudioNestedActionCard(
    title: String,
    body: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_modern_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: LlmProviderConfig,
    onSave: (LlmProviderConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var modelId by remember { mutableStateOf(initial.modelId) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var dialect by remember { mutableStateOf(initial.dialect) }
    var isKeyless by remember { mutableStateOf(initial.isKeyless) }
    var supportsReasoning by remember { mutableStateOf(initial.supportsReasoning) }
    var supportsVision by remember { mutableStateOf(initial.supportsVision) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isBuiltIn) "Configure ${initial.name}" else "Custom LLM Provider") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (isKeyless) "API Key (Optional for keyless)" else "API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Protocol Dialect", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderDialect.entries.forEach { d ->
                        FilterChip(
                            selected = dialect == d,
                            onClick = { dialect = d },
                            label = { Text(d.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keyless Endpoint", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isKeyless, onCheckedChange = { isKeyless = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_reasoning),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Supports Reasoning", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = supportsReasoning,
                        onCheckedChange = { supportsReasoning = it },
                        modifier = Modifier.testTag("provider_reasoning_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_eye),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Supports Vision", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = supportsVision,
                        onCheckedChange = { supportsVision = it },
                        modifier = Modifier.testTag("provider_vision_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()) {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                baseUrl = baseUrl.trim(),
                                modelId = modelId.trim(),
                                apiKey = apiKey.trim(),
                                dialect = dialect,
                                isKeyless = isKeyless,
                                supportsReasoning = supportsReasoning,
                                supportsVision = supportsVision
                            )
                        )
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PromptsAndSkillsTabContent(
    prompts: List<SystemPromptPreset>,
    activePromptId: String,
    skills: List<AgentSkill>,
    onSelectPrompt: (String) -> Unit,
    onSavePrompt: (SystemPromptPreset) -> Unit,
    onToggleSkill: (String) -> Unit,
    onAddSkill: (AgentSkill) -> Unit
) {
    val active = prompts.firstOrNull { it.id == activePromptId } ?: prompts.first()
    var promptDraft by remember(active.id, active.prompt) { mutableStateOf(active.prompt) }
    var showAddSkill by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("System Prompt Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                prompts.forEach { preset ->
                    FilterChip(
                        selected = preset.id == activePromptId,
                        onClick = { onSelectPrompt(preset.id) },
                        label = { Text(preset.name) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = promptDraft,
                onValueChange = { promptDraft = it },
                label = { Text("Active System Instructions") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSavePrompt(active.copy(prompt = promptDraft)) },
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Save Prompt")
                }
                OutlinedButton(
                    onClick = {
                        onSavePrompt(
                            SystemPromptPreset(
                                id = "prompt_${UUID.randomUUID().toString().take(6)}",
                                name = "Custom Prompt",
                                prompt = promptDraft,
                                isBuiltIn = false
                            )
                        )
                    },
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Save as Copy")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modular Skills", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddSkill = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("New Skill")
                }
            }
        }

        items(skills, key = { it.id }) { skill ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                ListItem(
                    headlineContent = { Text(skill.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(skill.description) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_sparkles),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingContent = {
                        Switch(checked = skill.enabled, onCheckedChange = { onToggleSkill(skill.id) })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showAddSkill) {
        var sName by remember { mutableStateOf("") }
        var sDesc by remember { mutableStateOf("") }
        var sInst by remember { mutableStateOf("") }
        var sKeywords by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSkill = false },
            title = { Text("Create Agent Skill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("Skill Name") }, singleLine = true)
                    OutlinedTextField(value = sDesc, onValueChange = { sDesc = it }, label = { Text("Description") }, singleLine = true)
                    OutlinedTextField(value = sInst, onValueChange = { sInst = it }, label = { Text("Instructions") }, minLines = 3)
                    OutlinedTextField(value = sKeywords, onValueChange = { sKeywords = it }, label = { Text("Trigger keywords (comma separated)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (sName.isNotBlank() && sInst.isNotBlank()) {
                        onAddSkill(
                            AgentSkill(
                                id = "skill_${UUID.randomUUID().toString().take(6)}",
                                name = sName.trim(),
                                description = sDesc.trim().ifBlank { "Custom user skill" },
                                instructions = sInst.trim(),
                                triggerKeywords = sKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                enabled = true,
                                isBuiltIn = false
                            )
                        )
                        showAddSkill = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddSkill = false }) { Text("Cancel") } }
        )
    }
}
