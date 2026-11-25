package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntryEntity
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.ReaderArticle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenReader: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column {
                    MenuSheetRow("New Tab", R.drawable.ic_modern_plus) { onDismiss(); onNewTab() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow(
                        if (isBookmarked) "Remove Bookmark" else "Bookmark This Page",
                        if (isBookmarked) R.drawable.ic_modern_bookmark_filled else R.drawable.ic_modern_bookmark
                    ) { onDismiss(); onToggleBookmark() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("Bookmarks", R.drawable.ic_modern_bookmark) { onDismiss(); onOpenBookmarks() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("History", R.drawable.ic_modern_history) { onDismiss(); onOpenHistory() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("Downloads", R.drawable.ic_modern_download) { onDismiss(); onOpenDownloads() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("Reader Mode", R.drawable.ic_modern_book) { onDismiss(); onOpenReader() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("Find in Page", R.drawable.ic_modern_search) { onDismiss(); onFindInPage() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow(
                        if (isDesktopMode) "Mobile Site" else "Desktop Site",
                        R.drawable.ic_modern_desktop
                    ) { onDismiss(); onToggleDesktopMode() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSheetRow("Settings", R.drawable.ic_modern_settings) { onDismiss(); onOpenSettings() }
                }
            }
        }
    }
}

@Composable
private fun MenuSheetRow(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_modern_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
            if (bookmarks.isEmpty()) {
                Text(
                    "No bookmarks saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(bookmarks, key = { it.url }) { bm ->
                            ListItem(
                                headlineContent = { Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(bm.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = JetBrainsMonoFamily)
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteBookmark(bm) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_modern_trash),
                                            contentDescription = "Remove bookmark",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onDismiss(); onOpenUrl(bm.url) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<HistoryEntryEntity>,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("History", style = MaterialTheme.typography.titleLarge)
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text("Clear all") }
                }
            }
            if (history.isEmpty()) {
                Text(
                    "No browsing history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = JetBrainsMonoFamily)
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_history),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onDismiss(); onOpenUrl(item.url) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: List<DownloadEntity>,
    onOpenDownload: (DownloadEntity) -> Unit = {},
    onDeleteDownload: (Long) -> Unit = {},
    onClearDownloads: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge)
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = onClearDownloads) { Text("Clear list") }
                }
            }
            if (downloads.isEmpty()) {
                Text(
                    "Files downloaded through Star Browser appear here and in your Android Downloads folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { d ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(d.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(d.url, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = JetBrainsMonoFamily)
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_download),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { onDeleteDownload(d.id) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_modern_trash),
                                            contentDescription = "Remove download entry",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    onDismiss()
                                    onOpenDownload(d)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderModeOverlay(
    article: ReaderArticle,
    onAskAgent: (ReaderArticle) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    var fontScaleSp by remember { mutableFloatStateOf(16.5f) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(onClick = onDismiss, shape = CircleShape) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_back),
                        contentDescription = "Exit Reader Mode",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { fontScaleSp = (fontScaleSp - 1.5f).coerceAtLeast(13f) }) {
                        Text("A-")
                    }
                    TextButton(onClick = { fontScaleSp = (fontScaleSp + 1.5f).coerceAtMost(24f) }) {
                        Text("A+")
                    }
                    Button(
                        onClick = { onAskAgent(article) },
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("Ask Agent")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = article.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${article.byline} · ${article.readMinutes} min read",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontScaleSp.sp,
                        lineHeight = (fontScaleSp * 1.55f).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
