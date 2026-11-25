package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.BrowserSettingsSnapshot
import com.example.data.preferences.SearchEngine
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.TextScalePreset
import com.example.ui.theme.ThemeMode

private data class SettingRowSpec(
    val id: String,
    val section: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val keywords: String,
    val isToggle: Boolean = false,
    val checked: Boolean = false,
    val onToggle: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun SettingsOverlay(
    settings: BrowserSettingsSnapshot,
    sandboxUsedBytes: Long,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentPreset) -> Unit = {},
    onSetTextScalePreset: (TextScalePreset) -> Unit = {},
    onSetAdBlock: (Boolean) -> Unit,
    onSetJavaScript: (Boolean) -> Unit,
    onSetDesktopDefault: (Boolean) -> Unit,
    onSetSearchEngine: (SearchEngine) -> Unit,
    onOpenAgentStudio: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit
) {
    var showAppearanceSetup by remember { mutableStateOf(false) }
    BackHandler {
        if (showAppearanceSetup) showAppearanceSetup = false else onDismiss()
    }

    if (showAppearanceSetup) {
        AppearanceSetupScreen(
            settings = settings,
            onSetThemeMode = onSetThemeMode,
            onSetAccentPreset = onSetAccentPreset,
            onSetTextScalePreset = onSetTextScalePreset,
            onBack = { showAppearanceSetup = false }
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val usedKb = (sandboxUsedBytes / 1024L).coerceAtLeast(0L)

    val allRows = listOf(
        SettingRowSpec(
            id = "theme",
            section = "General & Agent",
            title = "Appearance",
            subtitle = "${settings.themeMode.label} · ${settings.accentPreset.label} · ${settings.textScalePreset.label}",
            iconRes = R.drawable.ic_modern_palette,
            keywords = "appearance theme dark charcoal light grey white system accent color font size",
            onClick = { showAppearanceSetup = true }
        ),
        SettingRowSpec(
            id = "agent_studio",
            section = "General & Agent",
            title = "Agent Studio",
            subtitle = "Providers, Prompts, Skills, MCP & Sandbox",
            iconRes = R.drawable.ic_modern_sliders,
            keywords = "agent studio providers llm prompts skills mcp connectors",
            onClick = {
                onDismiss()
                onOpenAgentStudio()
            }
        ),
        SettingRowSpec(
            id = "ad_block",
            section = "Browser & Privacy",
            title = "Ad & Tracker Shield",
            subtitle = "Blocked ${settings.totalBlockedCount} trackers across tabs",
            iconRes = R.drawable.ic_modern_shield,
            keywords = "ad block tracker shield privacy",
            isToggle = true,
            checked = settings.adBlockEnabled,
            onToggle = onSetAdBlock
        ),
        SettingRowSpec(
            id = "search_engine",
            section = "Browser & Privacy",
            title = "Search Engine",
            subtitle = settings.searchEngine.label,
            iconRes = R.drawable.ic_modern_search,
            keywords = "search engine google duckduckgo bing kagi",
            onClick = { showSearchEngineDialog = true }
        ),
        SettingRowSpec(
            id = "javascript",
            section = "Browser & Privacy",
            title = "JavaScript",
            subtitle = if (settings.javaScriptEnabled) "Enabled for interactive sites" else "Disabled",
            iconRes = R.drawable.ic_modern_code,
            keywords = "javascript webview scripts",
            isToggle = true,
            checked = settings.javaScriptEnabled,
            onToggle = onSetJavaScript
        ),
        SettingRowSpec(
            id = "desktop_default",
            section = "Browser & Privacy",
            title = "Desktop Site by Default",
            subtitle = if (settings.desktopByDefault) "Request desktop layouts" else "Mobile viewport",
            iconRes = R.drawable.ic_modern_desktop,
            keywords = "desktop user agent viewport",
            isToggle = true,
            checked = settings.desktopByDefault,
            onToggle = onSetDesktopDefault
        ),
        SettingRowSpec(
            id = "storage",
            section = "Storage & Data",
            title = "Sandbox Storage",
            subtitle = "$usedKb KB used of 50 MB workspace quota",
            iconRes = R.drawable.ic_modern_folder,
            keywords = "storage sandbox files quota",
            onClick = {
                onDismiss()
                onOpenAgentStudio()
            }
        ),
        SettingRowSpec(
            id = "downloads",
            section = "Storage & Data",
            title = "Downloads",
            subtitle = "View active and completed downloads",
            iconRes = R.drawable.ic_modern_download,
            keywords = "downloads manager files",
            onClick = {
                onDismiss()
                onOpenDownloads()
            }
        ),
        SettingRowSpec(
            id = "clear_data",
            section = "Storage & Data",
            title = "History & Data",
            subtitle = "Clear browsing history, cookies, and cache",
            iconRes = R.drawable.ic_modern_lock,
            keywords = "history data privacy cookies cache clear",
            onClick = { showClearConfirm = true }
        )
    )

    val filteredRows = remember(searchQuery, allRows) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allRows
        else allRows.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.section.lowercase().contains(q) ||
                it.keywords.contains(q)
        }
    }

    val groupedSections = remember(filteredRows) {
        filteredRows.groupBy { it.section }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
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
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )

                    FilledTonalIconButton(
                        onClick = { showAboutDialog = true },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_info),
                            contentDescription = "About Star Browser",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (filteredRows.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_search),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = "No settings match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        groupedSections.forEach { (sectionTitle, rows) ->
                            item(key = "section_$sectionTitle") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = sectionTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(22.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(22.dp)
                                            )
                                    ) {
                                        Column {
                                            rows.forEachIndexed { index, row ->
                                                ListItem(
                                                    headlineContent = {
                                                        Text(
                                                            text = row.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    },
                                                    supportingContent = {
                                                        Text(
                                                            text = row.subtitle,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    leadingContent = {
                                                        Icon(
                                                            painter = painterResource(row.iconRes),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(21.dp)
                                                        )
                                                    },
                                                    trailingContent = {
                                                        if (row.isToggle) {
                                                            Switch(
                                                                checked = row.checked,
                                                                onCheckedChange = { row.onToggle?.invoke(it) }
                                                            )
                                                        } else {
                                                            Icon(
                                                                painter = painterResource(R.drawable.ic_modern_chevron_right),
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                    modifier = Modifier
                                                        .clickable {
                                                            if (row.isToggle) {
                                                                row.onToggle?.invoke(!row.checked)
                                                            } else {
                                                                row.onClick?.invoke()
                                                            }
                                                        }
                                                        .testTag("setting_row_${row.id}")
                                                )
                                                if (index < rows.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Floating Search Settings Pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(100.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_modern_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_search_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_close),
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Default Search Engine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchEngine.entries.forEach { engine ->
                        Surface(
                            onClick = {
                                onSetSearchEngine(engine)
                                showSearchEngineDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (settings.searchEngine == engine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = engine.label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) { Text("Done") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Browsing Data?") },
            text = { Text("This removes your saved browsing history, cookies, and WebView cache.") },
            confirmButton = {
                Button(onClick = {
                    onClearBrowsingData()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { StarCrestMark(size = 32.dp) },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Text("Chromium-powered mobile browser with Material Design 3 interface, autonomous page Agent, multi-provider LLM studio, and isolated mobile sandbox.")
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}
