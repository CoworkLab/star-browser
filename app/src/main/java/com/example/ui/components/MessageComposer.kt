package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.ExecutionMode
import com.example.agent.LlmProviderConfig
import com.example.sandbox.SandboxFileEntry
import com.example.ui.viewmodel.GrabbedPageContext

@Composable
fun StarCrestMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.44f
        val inner = r * 0.24f

        val star = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx + inner * 0.4f, cy - inner, cx + inner, cy - inner * 0.4f, cx + r, cy)
            cubicTo(cx + inner, cy + inner * 0.4f, cx + inner * 0.4f, cy + inner, cx, cy + r)
            cubicTo(cx - inner * 0.4f, cy + inner, cx - inner, cy + inner * 0.4f, cx - r, cy)
            cubicTo(cx - inner, cy - inner * 0.4f, cx - inner * 0.4f, cy - inner, cx, cy - r)
            close()
        }
        drawPath(
            path = star,
            brush = Brush.linearGradient(listOf(Color.White, primary, tertiary))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Image4PillComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    activeProvider: LlmProviderConfig,
    executionMode: ExecutionMode,
    onModeChange: (ExecutionMode) -> Unit,
    reasoningRequested: Boolean,
    onToggleReasoning: (Boolean) -> Unit,
    isAgentRunning: Boolean,
    grabbedPage: GrabbedPageContext?,
    hasAttachedImage: Boolean,
    attachedSandboxFiles: List<String>,
    sandboxFiles: List<SandboxFileEntry>,
    onGrabPageContext: () -> Unit,
    onClearGrabbedPage: () -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onToggleSandboxFile: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPlusSheet by remember { mutableStateOf(false) }
    var showSandboxPicker by remember { mutableStateOf(false) }

    val canSend = inputText.isNotBlank() || hasAttachedImage || grabbedPage != null || attachedSandboxFiles.isNotEmpty()
    val hasActiveChips = grabbedPage != null ||
        hasAttachedImage ||
        attachedSandboxFiles.isNotEmpty() ||
        executionMode != ExecutionMode.AUTO ||
        (activeProvider.supportsReasoning && reasoningRequested)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            AnimatedVisibility(visible = hasActiveChips) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (executionMode != ExecutionMode.AUTO) {
                        InputChip(
                            selected = true,
                            onClick = { showPlusSheet = true },
                            label = { Text(executionMode.label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_tune), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (activeProvider.supportsReasoning && reasoningRequested) {
                        InputChip(
                            selected = true,
                            onClick = { showPlusSheet = true },
                            label = { Text("Reasoning", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_reasoning), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (grabbedPage != null) {
                        InputChip(
                            selected = true,
                            onClick = onClearGrabbedPage,
                            label = {
                                Text(
                                    text = "${grabbedPage.title.take(22)} (${grabbedPage.interactiveCount} refs)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            trailingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_close), contentDescription = "Remove page context", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    if (hasAttachedImage) {
                        InputChip(
                            selected = true,
                            onClick = onClearImage,
                            label = { Text("Image attached", style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_image), contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_close), contentDescription = "Remove image", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    attachedSandboxFiles.forEach { path ->
                        InputChip(
                            selected = true,
                            onClick = { onToggleSandboxFile(path) },
                            label = { Text(path, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_paperclip), contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                Icon(painterResource(R.drawable.ic_modern_close), contentDescription = "Remove file", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // Image 4 Layout: Detached circular '+' button + single rounded pill input with inline grab icon & circular lavender-silver Mic/Send button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { showPlusSheet = true },
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("composer_plus_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_modern_plus),
                        contentDescription = stringResource(R.string.cd_composer_plus),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.agent_input_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = onInputChange,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 5,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { if (canSend && !isAgentRunning) onSend() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("agent_message_input")
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        // Right circular primary action button (Voice / Send / Stop)
                        Surface(
                            onClick = {
                                when {
                                    isAgentRunning -> onStop()
                                    canSend -> onSend()
                                    else -> onStartVoiceInput()
                                }
                            },
                            shape = CircleShape,
                            color = if (isAgentRunning) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isAgentRunning) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag(
                                    when {
                                        isAgentRunning -> "composer_stop_button"
                                        canSend -> "composer_send_button"
                                        else -> "composer_mic_button"
                                    }
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        when {
                                            isAgentRunning -> R.drawable.ic_modern_stop
                                            canSend -> R.drawable.ic_modern_arrow_up
                                            else -> R.drawable.ic_modern_mic
                                        }
                                    ),
                                    contentDescription = when {
                                        isAgentRunning -> stringResource(R.string.cd_stop_agent)
                                        canSend -> stringResource(R.string.cd_send_message)
                                        else -> stringResource(R.string.cd_voice_input)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlusSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlusSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Composer Options",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Execution Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ExecutionMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = executionMode == mode,
                                onClick = { onModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ExecutionMode.entries.size
                                ),
                                modifier = Modifier.testTag("mode_segment_${mode.name.lowercase()}")
                            ) {
                                Text(mode.shortLabel, maxLines = 1)
                            }
                        }
                    }
                    Text(
                        text = executionMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Conditional Reasoning Toggle: strictly shown only when active provider supports reasoning
                if (activeProvider.supportsReasoning) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        ListItem(
                            headlineContent = { Text("Reasoning") },
                            supportingContent = {
                                Text("Enable thinking tokens for ${activeProvider.name}")
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_reasoning),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = reasoningRequested,
                                    onCheckedChange = onToggleReasoning,
                                    modifier = Modifier.testTag("plus_sheet_reasoning_switch")
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Attach Image") },
                            supportingContent = {
                                Text(
                                    if (activeProvider.supportsVision) {
                                        "Pick a photo via Android Photo Picker"
                                    } else {
                                        "Vision disabled on ${activeProvider.name} — tap to configure"
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(painterResource(R.drawable.ic_modern_image), contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingContent = {
                                Icon(painterResource(R.drawable.ic_modern_arrow_right), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable {
                                    showPlusSheet = false
                                    if (activeProvider.supportsVision) onPickImage()
                                    else onOpenProviderSettings()
                                }
                                .testTag("plus_attach_image")
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Attach Sandbox File") },
                            supportingContent = {
                                Text("${sandboxFiles.size} file(s) in isolated workspace")
                            },
                            leadingContent = {
                                Icon(painterResource(R.drawable.ic_modern_folder), contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingContent = {
                                Icon(painterResource(R.drawable.ic_modern_arrow_right), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clickable {
                                    showPlusSheet = false
                                    showSandboxPicker = true
                                }
                                .testTag("plus_attach_sandbox")
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Grab Current Browser Page") },
                            supportingContent = {
                                Text("Attach DOM snapshot and interactive elements")
                            },
                            leadingContent = {
                                Icon(painterResource(R.drawable.ic_modern_sparkles), contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                showPlusSheet = false
                                onGrabPageContext()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSandboxPicker) {
        AlertDialog(
            onDismissRequest = { showSandboxPicker = false },
            title = { Text("Attach Sandbox File") },
            text = {
                if (sandboxFiles.isEmpty()) {
                    Text(
                        "No files exist in the mobile sandbox yet. Ask the Agent to save a note or create a file inside Agent Studio → Sandbox.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sandboxFiles.forEach { file ->
                            val selected = file.relativePath in attachedSandboxFiles
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onToggleSandboxFile(file.relativePath) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = file.relativePath,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (selected) "Attached" else "${file.sizeBytes} B",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSandboxPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}
