package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BrowserTabEntity
import com.example.data.local.HistoryEntryEntity
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStarSemanticColors
import com.example.ui.viewmodel.WebTabRuntime

private data class SpeedDialItem(
    val label: String,
    val url: String,
    val badge: String
)

private val speedDialItems = listOf(
    SpeedDialItem("Google", "https://www.google.com", "G"),
    SpeedDialItem("YouTube", "https://www.youtube.com", "YT"),
    SpeedDialItem("GitHub", "https://github.com", "GH"),
    SpeedDialItem("Wikipedia", "https://en.wikipedia.org", "W"),
    SpeedDialItem("Reddit", "https://www.reddit.com", "R"),
    SpeedDialItem("X", "https://x.com", "X"),
    SpeedDialItem("Hacker News", "https://news.ycombinator.com", "HN"),
    SpeedDialItem("Docs", "https://developer.android.com", "AD")
)

@Composable
fun BrowserView(
    activeTab: BrowserTabEntity?,
    tabCount: Int,
    runtime: WebTabRuntime,
    history: List<HistoryEntryEntity>,
    showFindBar: Boolean,
    findQuery: String,
    onNavigateUrl: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onHome: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenMenu: () -> Unit,
    onSwitchToAgent: () -> Unit,
    onAskAgentCurrentPage: () -> Unit,
    onUpdateFindQuery: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onCloseFindBar: () -> Unit,
    onTopChromeHeightPx: (Int) -> Unit = {},
    onBottomDockHeightPx: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentUrl = activeTab?.url.orEmpty()
    val isNewTab = currentUrl.isBlank() || currentUrl == "about:blank"
    val hostLabel = remember(currentUrl) {
        if (isNewTab) "" else (Uri.parse(currentUrl).host?.removePrefix("www.") ?: currentUrl)
    }
    var omniboxFocused by remember { mutableStateOf(false) }
    var omniboxField by remember(currentUrl) {
        val initText = if (isNewTab) "" else currentUrl
        mutableStateOf(TextFieldValue(text = initText))
    }
    val focusManager = LocalFocusManager.current
    val omniboxFocusRequester = remember { FocusRequester() }
    val semantic = LocalStarSemanticColors.current

    LaunchedEffect(currentUrl) {
        if (!omniboxFocused) {
            val initText = if (isNewTab) "" else currentUrl
            omniboxField = TextFieldValue(text = initText)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal safe-area top strip + optional Find bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onTopChromeHeightPx(it.height) }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        if (!isNewTab && runtime.isLoading) {
                            LinearProgressIndicator(
                                progress = { runtime.progress.coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showFindBar) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BasicTextField(
                                value = findQuery,
                                onValueChange = onUpdateFindQuery,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                decorationBox = { inner ->
                                    if (findQuery.isEmpty()) {
                                        Text(
                                            "Find in page...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            IconButton(onClick = { onFindNext(false) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_chevron_up),
                                    contentDescription = "Previous match",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onFindNext(true) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_chevron_down),
                                    contentDescription = "Next match",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onCloseFindBar, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_close),
                                    contentDescription = "Close find",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Middle content: either New Tab Page or transparent hole revealing WebViewHost beneath
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isNewTab) {
                    NewTabPageContent(
                        history = history,
                        onNavigateUrl = onNavigateUrl,
                        onSwitchToAgent = onSwitchToAgent
                    )
                }
            }

            // Single Unified Bottom Browser Dock
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { onBottomDockHeightPx(it.height) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Single clean address/search pill
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .border(
                                        width = if (omniboxFocused) 1.5.dp else 1.dp,
                                        color = if (omniboxFocused) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(100.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 14.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when {
                                                isNewTab || omniboxFocused -> R.drawable.ic_modern_search
                                                currentUrl.startsWith("https://") -> R.drawable.ic_modern_lock
                                                else -> R.drawable.ic_modern_globe
                                            }
                                        ),
                                        contentDescription = null,
                                        tint = when {
                                            !isNewTab && !omniboxFocused && currentUrl.startsWith("https://") -> semantic.success
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(9.dp))

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { omniboxFocusRequester.requestFocus() },
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (!omniboxFocused) {
                                            Text(
                                                text = if (isNewTab) stringResource(R.string.browser_search_placeholder) else hostLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isNewTab) FontWeight.Normal else FontWeight.Medium,
                                                color = if (isNewTab) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (omniboxField.text.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.browser_search_placeholder),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        BasicTextField(
                                            value = omniboxField,
                                            onValueChange = { omniboxField = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                color = if (omniboxFocused) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    Color.Transparent
                                                }
                                            ),
                                            cursorBrush = SolidColor(
                                                if (omniboxFocused) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                            keyboardActions = KeyboardActions(
                                                onGo = {
                                                    focusManager.clearFocus()
                                                    onNavigateUrl(omniboxField.text)
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .focusRequester(omniboxFocusRequester)
                                                .onFocusChanged { state ->
                                                    omniboxFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        val full = if (isNewTab) "" else currentUrl
                                                        omniboxField = TextFieldValue(
                                                            text = full,
                                                            selection = TextRange(0, full.length)
                                                        )
                                                    } else {
                                                        val full = if (isNewTab) "" else currentUrl
                                                        omniboxField = TextFieldValue(text = full)
                                                    }
                                                }
                                                .testTag("browser_omnibox_input")
                                        )
                                    }

                                    if (omniboxFocused && omniboxField.text.isNotEmpty()) {
                                        IconButton(
                                            onClick = { omniboxField = TextFieldValue("") },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_modern_close),
                                                contentDescription = "Clear URL",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else if (!isNewTab) {
                                        IconButton(
                                            onClick = onReloadOrStop,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("browser_reload_button")
                                        ) {
                                            if (runtime.isLoading) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_modern_close),
                                                    contentDescription = "Stop loading",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_modern_refresh),
                                                    contentDescription = "Reload page",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Agent handoff pill
                            Surface(
                                onClick = {
                                    if (isNewTab) onSwitchToAgent() else onAskAgentCurrentPage()
                                },
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("browser_ask_agent_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_sparkles),
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.agent_title),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        // Clean 5-item bottom navigation controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                enabled = runtime.canGoBack,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_back),
                                    contentDescription = "Back",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = onForward,
                                enabled = runtime.canGoForward,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_forward),
                                    contentDescription = "Forward",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = onHome,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_home),
                                    contentDescription = "New Tab Home",
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenTabSwitcher() }
                                    .padding(8.dp)
                                    .testTag("browser_tab_count_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .border(1.7.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.5.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabCount.coerceAtMost(99).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = onOpenMenu,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("browser_menu_button")
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_more),
                                    contentDescription = "Browser Menu",
                                    modifier = Modifier.size(21.dp)
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
private fun NewTabPageContent(
    history: List<HistoryEntryEntity>,
    onNavigateUrl: (String) -> Unit,
    onSwitchToAgent: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StarCrestMark(size = 44.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = onSwitchToAgent,
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(100.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_sparkles),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Open Agent Chat",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Speed Dial",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    speedDialItems.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { dial ->
                                Surface(
                                    onClick = { onNavigateUrl(dial.url) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(18.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dial.badge,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = dial.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            history.take(6).forEachIndexed { idx, entry ->
                                ListItem(
                                    headlineContent = {
                                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(
                                            entry.url,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_modern_history),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { onNavigateUrl(entry.url) }
                                )
                                if (idx < history.take(6).lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
