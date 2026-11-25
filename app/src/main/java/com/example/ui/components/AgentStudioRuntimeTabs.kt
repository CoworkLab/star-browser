package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.McpConnectorConfig
import com.example.agent.McpTransport
import com.example.agent.ToolkitId
import com.example.sandbox.MobileSandboxManager
import com.example.sandbox.SafEntry
import com.example.sandbox.SandboxFileEntry
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ToolsAndMcpTabContent(
    enabledToolkits: Set<ToolkitId>,
    connectors: List<McpConnectorConfig>,
    onToggleToolkit: (ToolkitId) -> Unit,
    onSaveConnector: (McpConnectorConfig) -> Unit,
    onToggleConnector: (String) -> Unit,
    onDeleteConnector: (String) -> Unit
) {
    var showAddConnector by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Built-in Agent Toolkits", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column {
                    ToolkitId.entries.forEachIndexed { idx, toolkit ->
                        val enabled = toolkit in enabledToolkits
                        ListItem(
                            headlineContent = { Text(toolkit.label, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(toolkit.description) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_build),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingContent = {
                                Switch(checked = enabled, onCheckedChange = { onToggleToolkit(toolkit) })
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onToggleToolkit(toolkit) }
                        )
                        if (idx < ToolkitId.entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
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
                Text("Custom MCP & HTTP Connectors", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddConnector = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Add Connector")
                }
            }
        }

        if (connectors.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Connect remote MCP JSON-RPC servers or REST webhooks to expose live external tools to the Agent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(connectors, key = { it.id }) { conn ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    ListItem(
                        headlineContent = { Text(conn.name, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                "${conn.transport.label} · ${conn.discoveredTools.size} tool(s)\n${conn.endpointUrl}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = JetBrainsMonoFamily
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_external),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = conn.enabled, onCheckedChange = { onToggleConnector(conn.id) })
                                IconButton(onClick = { onDeleteConnector(conn.id) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_trash),
                                        contentDescription = "Delete connector",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }

    if (showAddConnector) {
        var cName by remember { mutableStateOf("") }
        var cUrl by remember { mutableStateOf("https://") }
        var cAuth by remember { mutableStateOf("") }
        var cTransport by remember { mutableStateOf(McpTransport.JSON_RPC_HTTP) }

        AlertDialog(
            onDismissRequest = { showAddConnector = false },
            title = { Text("Add MCP / HTTP Connector") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = cName, onValueChange = { cName = it }, label = { Text("Connector Name") }, singleLine = true)
                    OutlinedTextField(value = cUrl, onValueChange = { cUrl = it }, label = { Text("Endpoint URL") }, singleLine = true)
                    OutlinedTextField(value = cAuth, onValueChange = { cAuth = it }, label = { Text("Authorization Header (Optional)") }, singleLine = true)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McpTransport.entries.forEach { t ->
                            FilterChip(selected = cTransport == t, onClick = { cTransport = t }, label = { Text(t.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (cName.isNotBlank() && cUrl.startsWith("http")) {
                        onSaveConnector(
                            McpConnectorConfig(
                                id = "mcp_${UUID.randomUUID().toString().take(6)}",
                                name = cName.trim(),
                                endpointUrl = cUrl.trim(),
                                transport = cTransport,
                                authHeaderValue = cAuth.trim(),
                                enabled = true
                            )
                        )
                        showAddConnector = false
                    }
                }) { Text("Save & Discover") }
            },
            dismissButton = { TextButton(onClick = { showAddConnector = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SandboxTabContent(
    sandboxFiles: List<SandboxFileEntry>,
    sandboxUsedBytes: Long,
    sandboxInternetEnabled: Boolean,
    linkedSafLabel: String?,
    safFiles: List<SafEntry> = emptyList(),
    onSetSandboxInternet: (Boolean) -> Unit,
    onWriteFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onReadFile: suspend (String) -> Result<String>,
    onRequestLinkSaf: () -> Unit,
    onUnlinkSaf: () -> Unit,
    onImportSafFile: (SafEntry) -> Unit = {},
    onExportSandboxFile: (String) -> Unit = {},
    onRequestMicCheck: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showCreateFile by remember { mutableStateOf(false) }
    var showImportSafDialog by remember { mutableStateOf(false) }
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewContent by remember { mutableStateOf<String?>(null) }

    val quotaProgress = (sandboxUsedBytes.toFloat() / MobileSandboxManager.MAX_TOTAL_BYTES.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Isolated Mobile Sandbox", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${sandboxUsedBytes / 1024} KB / 50 MB",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { quotaProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                    )
                    Text(
                        "App-private workspace (512 KB per-file cap) keeps mobile storage lightweight and safe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    ListItem(
                        headlineContent = { Text("Sandbox Internet Access") },
                        supportingContent = { Text("Allow web_fetch and public HTTP tool calls") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_globe),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            Switch(checked = sandboxInternetEnabled, onCheckedChange = onSetSandboxInternet)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("External Folder (SAF)") },
                        supportingContent = {
                            Text(linkedSafLabel ?: "Not linked — requested only on demand")
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_folder),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            if (linkedSafLabel != null) {
                                Row {
                                    TextButton(onClick = { showImportSafDialog = true }) { Text("Import") }
                                    TextButton(onClick = onUnlinkSaf) { Text("Unlink") }
                                }
                            } else {
                                TextButton(onClick = onRequestLinkSaf) { Text("Link") }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Microphone Dictation") },
                        supportingContent = { Text("On-demand runtime permission") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_mic),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = onRequestMicCheck) { Text("Check") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Android App Permissions") },
                        supportingContent = { Text("Open system permissions settings") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_settings),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onOpenAppSettings)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sandbox Files (${sandboxFiles.size})", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = { showCreateFile = true },
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_modern_plus),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("New File")
                }
            }
        }

        items(sandboxFiles, key = { it.relativePath }) { entry ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                ListItem(
                    headlineContent = { Text(entry.relativePath, fontFamily = JetBrainsMonoFamily) },
                    supportingContent = { Text("${entry.sizeBytes} bytes") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (linkedSafLabel != null) {
                                IconButton(onClick = { onExportSandboxFile(entry.relativePath) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_upload),
                                        contentDescription = "Export to linked folder",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteFile(entry.relativePath) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_modern_trash),
                                    contentDescription = "Delete file",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch {
                            val res = onReadFile(entry.relativePath)
                            previewTitle = entry.relativePath
                            previewContent = res.getOrElse { "Error: ${it.message}" }
                        }
                    }
                )
            }
        }
    }

    if (showImportSafDialog) {
        AlertDialog(
            onDismissRequest = { showImportSafDialog = false },
            title = { Text("Import from ${linkedSafLabel ?: "Linked Folder"}") },
            text = {
                if (safFiles.isEmpty()) {
                    Text(
                        "No files found in the linked external folder.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(safFiles, key = { it.documentId }) { item ->
                            Surface(
                                onClick = {
                                    onImportSafFile(item)
                                    showImportSafDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_download),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.sizeBytes} bytes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportSafDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCreateFile) {
        var fPath by remember { mutableStateOf("notes.md") }
        var fBody by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFile = false },
            title = { Text("Create Sandbox File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fPath, onValueChange = { fPath = it }, label = { Text("Filename") }, singleLine = true)
                    OutlinedTextField(value = fBody, onValueChange = { fBody = it }, label = { Text("Content") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (fPath.isNotBlank()) {
                        onWriteFile(fPath.trim(), fBody)
                        showCreateFile = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreateFile = false }) { Text("Cancel") } }
        )
    }

    if (previewTitle != null && previewContent != null) {
        AlertDialog(
            onDismissRequest = { previewTitle = null; previewContent = null },
            title = { Text(previewTitle.orEmpty(), fontFamily = JetBrainsMonoFamily) },
            text = { Text(previewContent.orEmpty(), style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFamily) },
            confirmButton = {
                TextButton(onClick = { previewTitle = null; previewContent = null }) { Text("Close") }
            }
        )
    }
}
