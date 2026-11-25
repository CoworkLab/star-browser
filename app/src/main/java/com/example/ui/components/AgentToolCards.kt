package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.AgentDefaults
import com.example.agent.PendingApprovalAction
import com.example.agent.ToolRegistry
import com.example.data.local.AgentMessageEntity
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStarSemanticColors

@Composable
fun UserMessageBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
fun ToolCallRuntimeCard(step: AgentMessageEntity) {
    var inspectDetails by remember { mutableStateOf(false) }
    val semantic = LocalStarSemanticColors.current

    val canonical = step.toolName ?: "tool"
    val status = step.toolStatus ?: when (step.toolSuccess) {
        true -> "completed"
        false -> "failed"
        null -> "running"
    }
    val friendlyTitle = remember(canonical) { ToolRegistry.friendlyTitle(canonical) }
    val targetPreview = remember(canonical, step.toolArgs) {
        ToolRegistry.conciseTarget(canonical, step.toolArgs)
    }

    val accentColor = when (status) {
        "running" -> MaterialTheme.colorScheme.primary
        "awaiting_approval" -> semantic.warning
        "completed" -> semantic.success
        "skipped", "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when (status) {
        "running" -> "Running"
        "awaiting_approval" -> "Approve?"
        "completed" -> step.toolDurationMs?.let { "${it}ms" } ?: "Done"
        "skipped" -> "Denied"
        "cancelled" -> "Cancelled"
        else -> "Failed"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (status == "running" || status == "awaiting_approval") {
                    accentColor.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { inspectDetails = !inspectDetails }
            .testTag("tool_runtime_chip")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (status) {
                    "running" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    "awaiting_approval" -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_shield),
                            contentDescription = null,
                            tint = semantic.warning,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    "completed" -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_check),
                            contentDescription = null,
                            tint = semantic.success,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    "skipped", "cancelled" -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_alert),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = friendlyTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = canonical,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (targetPreview.isNotBlank()) {
                        Text(
                            text = targetPreview,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            AnimatedVisibility(visible = inspectDetails && (step.content.isNotBlank() || !step.toolArgs.isNullOrBlank())) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (!step.toolArgs.isNullOrBlank() && step.toolArgs != "{}") {
                        Text(
                            text = "Args: ${step.toolArgs.take(220)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (step.content.isNotBlank()) {
                        Text(
                            text = step.content.take(320),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReasoningCollapseCard(reasoning: String) {
    var showThought by remember { mutableStateOf(false) }

    Surface(
        onClick = { showThought = !showThought },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .testTag("reasoning_disclosure_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_modern_reasoning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    painter = painterResource(
                        if (showThought) R.drawable.ic_modern_chevron_up else R.drawable.ic_modern_chevron_down
                    ),
                    contentDescription = if (showThought) "Collapse reasoning" else "Expand reasoning",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            AnimatedVisibility(visible = showThought) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AgentResponseBubble(
    msg: AgentMessageEntity,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val citations = remember(msg.citationsJson) {
        AgentDefaults.deserializeCitations(msg.citationsJson)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!msg.reasoning.isNullOrBlank()) {
            ReasoningCollapseCard(reasoning = msg.reasoning)
        }

        RichMarkdownText(
            markdown = msg.content,
            citations = citations,
            onOpenUrl = onOpenUrl,
            onCopyCode = onCopy
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onCopy(msg.content) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_modern_copy),
                    contentDescription = "Copy response",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StreamingTurnBubble(
    streamingReplyText: String,
    streamingReasoningText: String,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (streamingReasoningText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_reasoning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Reasoning...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = streamingReasoningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (streamingReplyText.isNotBlank()) {
            RichMarkdownText(
                markdown = streamingReplyText,
                citations = emptyList(),
                onOpenUrl = onOpenUrl,
                onCopyCode = onCopy
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StarCrestMark(size = 18.dp)
            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

@Composable
fun HumanInTheLoopCard(
    action: PendingApprovalAction,
    onApprove: () -> Unit,
    onAllowForThread: () -> Unit,
    onDeny: () -> Unit
) {
    val semantic = LocalStarSemanticColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .testTag("hitl_approval_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_shield),
                        contentDescription = null,
                        tint = semantic.warning,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Human Approval Required",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = action.riskLevel.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = action.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (action.targetUrlOrPath.isNotBlank()) {
                Text(
                    text = action.targetUrlOrPath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.testTag("hitl_deny_button")
                ) {
                    Text("Deny")
                }
                FilledTonalButton(
                    onClick = onAllowForThread,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.testTag("hitl_allow_thread_button")
                ) {
                    Text("Allow Thread")
                }
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.testTag("hitl_approve_button")
                ) {
                    Text("Approve")
                }
            }
        }
    }
}
