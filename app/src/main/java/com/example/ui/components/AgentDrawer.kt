package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.LlmProviderConfig
import com.example.data.local.AgentConversationEntity
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun AgentHistoryDrawerContent(
    conversations: List<AgentConversationEntity>,
    activeConversationId: String,
    activeProvider: LlmProviderConfig,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenStudioProviders: () -> Unit,
    onOpenStudioRoot: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.widthIn(max = 310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StarCrestMark(size = 24.dp)
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        onNewConversation()
                        onCloseDrawer()
                    },
                    modifier = Modifier.testTag("drawer_new_chat_button")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_plus),
                        contentDescription = stringResource(R.string.cd_new_chat),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val selected = conv.id == activeConversationId
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = conv.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = selected,
                        onClick = {
                            onSelectConversation(conv.id)
                            onCloseDrawer()
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_chat),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        badge = {
                            if (conversations.size > 1) {
                                IconButton(
                                    onClick = { onDeleteConversation(conv.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_trash),
                                        contentDescription = "Delete conversation",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerActionTile(
                    iconRes = R.drawable.ic_modern_cpu,
                    iconTintPrimary = true,
                    title = activeProvider.name,
                    subtitle = activeProvider.modelId,
                    monoSubtitle = true,
                    testTag = "sidebar_model_button",
                    onClick = {
                        onCloseDrawer()
                        onOpenStudioProviders()
                    }
                )
                DrawerActionTile(
                    iconRes = R.drawable.ic_modern_sliders,
                    iconTintPrimary = true,
                    title = "Agent Studio",
                    subtitle = "Prompts, Skills, MCP & Sandbox",
                    monoSubtitle = false,
                    testTag = "sidebar_studio_button",
                    onClick = {
                        onCloseDrawer()
                        onOpenStudioRoot()
                    }
                )
                DrawerActionTile(
                    iconRes = R.drawable.ic_modern_settings,
                    iconTintPrimary = false,
                    title = "Settings",
                    subtitle = "Appearance, Browser & Data",
                    monoSubtitle = false,
                    testTag = "sidebar_settings_button",
                    onClick = {
                        onCloseDrawer()
                        onOpenSettings()
                    }
                )
            }
        }
    }
}

@Composable
private fun DrawerActionTile(
    iconRes: Int,
    iconTintPrimary: Boolean,
    title: String,
    subtitle: String,
    monoSubtitle: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (iconTintPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (monoSubtitle) JetBrainsMonoFamily else MaterialTheme.typography.bodySmall.fontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
