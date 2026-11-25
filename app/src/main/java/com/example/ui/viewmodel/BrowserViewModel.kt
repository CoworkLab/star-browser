package com.example.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentHostBridge
import com.example.agent.AgentSkill
import com.example.agent.ApprovalDecision
import com.example.agent.ExecutionMode
import com.example.agent.LlmProviderConfig
import com.example.agent.McpConnectorClient
import com.example.agent.McpConnectorConfig
import com.example.agent.MultiProviderBrain
import com.example.agent.PendingApprovalAction
import com.example.agent.SystemPromptPreset
import com.example.agent.ToolkitId
import com.example.agent.TrueAgentEngine
import com.example.browser.DomBridge
import com.example.data.local.AgentConversationEntity
import com.example.data.local.AgentMessageEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserTabEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntryEntity
import com.example.data.local.StarDatabase
import com.example.data.local.StarRepository
import com.example.data.preferences.BrowserPreferences
import com.example.data.preferences.BrowserSettingsSnapshot
import com.example.data.preferences.SearchEngine
import com.example.sandbox.MobileSandboxManager
import com.example.sandbox.SafEntry
import com.example.sandbox.SandboxFileEntry
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.TextScalePreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume

enum class RootScreen {
    AGENT,
    BROWSER
}

enum class StudioTab(val label: String) {
    PROVIDERS("Providers"),
    PROMPTS_SKILLS("Prompts & Skills"),
    TOOLS_MCP("Tools & MCP"),
    SANDBOX("Sandbox")
}

data class WebTabRuntime(
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Float = 0f
)

data class ReaderArticle(
    val title: String,
    val byline: String,
    val readMinutes: Int,
    val content: String,
    val sourceUrl: String
)

data class GrabbedPageContext(
    val title: String,
    val url: String,
    val summarySnippet: String,
    val interactiveCount: Int,
    val rawSnapshotJson: String
)

enum class PermissionRequestKind {
    MICROPHONE,
    EXTERNAL_FOLDER
}

class BrowserViewModel(application: Application) : AndroidViewModel(application), AgentHostBridge {

    private val repo = StarRepository(StarDatabase.getInstance(application))
    private val prefs = BrowserPreferences(application)
    val sandboxManager = MobileSandboxManager(application)
    private val brain = MultiProviderBrain()
    private val mcpClient = McpConnectorClient()
    private val engine = TrueAgentEngine(
        brain = brain,
        host = this,
        sandbox = sandboxManager,
        mcpClient = mcpClient
    )
    private val agentCoordinator = AgentRunCoordinator(repo, brain, engine)

    private val webViews = mutableMapOf<String, WebView>()
    private val webViewReadyWaiters = mutableMapOf<String, CompletableDeferred<WebView>>()
    private val pendingPageLoads = mutableMapOf<String, CompletableDeferred<Result<String>>>()
    private var activeAgentJob: Job? = null
    private var approvalDeferred: CompletableDeferred<ApprovalDecision>? = null

    val settings: StateFlow<BrowserSettingsSnapshot> = prefs.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BrowserSettingsSnapshot()
    )

    val tabs: StateFlow<List<BrowserTabEntity>> = repo.tabs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repo.bookmarks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val history: StateFlow<List<HistoryEntryEntity>> = repo.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloads: StateFlow<List<DownloadEntity>> = repo.downloads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val conversations: StateFlow<List<AgentConversationEntity>> = repo.conversations.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _activeConversationId = MutableStateFlow("default-thread")
    val activeConversationId: StateFlow<String> = _activeConversationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<AgentMessageEntity>> = _activeConversationId
        .flatMapLatest { id -> repo.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rootScreen = MutableStateFlow(RootScreen.AGENT)
    val rootScreen: StateFlow<RootScreen> = _rootScreen.asStateFlow()

    private val _tabRuntimes = MutableStateFlow<Map<String, WebTabRuntime>>(emptyMap())
    val tabRuntimes: StateFlow<Map<String, WebTabRuntime>> = _tabRuntimes.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _streamingReplyText = MutableStateFlow("")
    val streamingReplyText: StateFlow<String> = _streamingReplyText.asStateFlow()

    private val _streamingReasoningText = MutableStateFlow("")
    val streamingReasoningText: StateFlow<String> = _streamingReasoningText.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingApprovalAction?>(null)
    val pendingApproval: StateFlow<PendingApprovalAction?> = _pendingApproval.asStateFlow()

    private val _attachedImageBase64 = MutableStateFlow<String?>(null)
    val attachedImageBase64: StateFlow<String?> = _attachedImageBase64.asStateFlow()

    private val _attachedSandboxFiles = MutableStateFlow<List<String>>(emptyList())
    val attachedSandboxFiles: StateFlow<List<String>> = _attachedSandboxFiles.asStateFlow()

    private val _grabbedPage = MutableStateFlow<GrabbedPageContext?>(null)
    val grabbedPage: StateFlow<GrabbedPageContext?> = _grabbedPage.asStateFlow()

    private val _sandboxFiles = MutableStateFlow<List<SandboxFileEntry>>(emptyList())
    val sandboxFiles: StateFlow<List<SandboxFileEntry>> = _sandboxFiles.asStateFlow()

    private val _sandboxUsedBytes = MutableStateFlow(0L)
    val sandboxUsedBytes: StateFlow<Long> = _sandboxUsedBytes.asStateFlow()

    private val _linkedSafLabel = MutableStateFlow<String?>(null)
    val linkedSafLabel: StateFlow<String?> = _linkedSafLabel.asStateFlow()

    private val _safFiles = MutableStateFlow<List<SafEntry>>(emptyList())
    val safFiles: StateFlow<List<SafEntry>> = _safFiles.asStateFlow()

    private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
    val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _providerTestStatus = MutableStateFlow<String?>(null)
    val providerTestStatus: StateFlow<String?> = _providerTestStatus.asStateFlow()

    private val _findInPageQuery = MutableStateFlow("")
    val findInPageQuery: StateFlow<String> = _findInPageQuery.asStateFlow()

    private val _showFindBar = MutableStateFlow(false)
    val showFindBar: StateFlow<Boolean> = _showFindBar.asStateFlow()

    private val _pendingPermissionDialog = MutableStateFlow<PermissionRequestKind?>(null)
    val pendingPermissionDialog: StateFlow<PermissionRequestKind?> = _pendingPermissionDialog.asStateFlow()

    init {
        viewModelScope.launch {
            repo.recoverInterruptedSteps()
            val initialTabs = repo.tabs.first()
            if (initialTabs.isEmpty()) {
                repo.upsertTab(
                    BrowserTabEntity(
                        id = "tab_home",
                        url = "about:blank",
                        title = "New Tab",
                        isActive = true
                    )
                )
            }
            val initialThreads = repo.conversations.first()
            if (initialThreads.isEmpty()) {
                repo.upsertConversation(
                    AgentConversationEntity(
                        id = "default-thread",
                        title = "New Conversation"
                    )
                )
            } else {
                _activeConversationId.value = initialThreads.first().id
            }
            refreshSandbox()
        }
        viewModelScope.launch {
            settings.collect { snap ->
                _linkedSafLabel.value = sandboxManager.describeLinkedSafFolder(snap.sandboxFolderUri)
                _safFiles.value = sandboxManager.listSafFiles(snap.sandboxFolderUri)
            }
        }
    }

    fun activeTab(): BrowserTabEntity? = tabs.value.firstOrNull { it.isActive } ?: tabs.value.firstOrNull()

    fun activeProvider(): LlmProviderConfig {
        val snap = settings.value
        return snap.providers.firstOrNull { it.id == snap.activeProviderId } ?: snap.providers.first()
    }

    fun switchRootScreen(screen: RootScreen) {
        _rootScreen.value = screen
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    // --- Tab & WebView Management ---

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
        webViewReadyWaiters.remove(tabId)?.complete(webView)
    }

    fun unregisterWebView(tabId: String) {
        webViews.remove(tabId)
        webViewReadyWaiters.remove(tabId)?.cancel()
    }

    fun newTab(url: String = "about:blank", switchToBrowser: Boolean = false) {
        viewModelScope.launch {
            val id = "tab_${UUID.randomUUID().toString().take(8)}"
            val desktop = settings.value.desktopByDefault
            repo.upsertTab(
                BrowserTabEntity(
                    id = id,
                    url = url,
                    title = if (url == "about:blank") "New Tab" else url,
                    isDesktopMode = desktop,
                    isActive = true
                )
            )
            repo.setActiveTab(id)
            if (switchToBrowser) _rootScreen.value = RootScreen.BROWSER
        }
    }

    fun selectTab(tabId: String) {
        viewModelScope.launch { repo.setActiveTab(tabId) }
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            val current = tabs.value
            val remaining = current.filterNot { it.id == tabId }
            withContext(Dispatchers.Main) {
                webViews.remove(tabId)?.destroy()
            }
            pendingPageLoads.remove(tabId)?.cancel()
            repo.deleteTab(tabId)
            if (remaining.isEmpty()) {
                newTab("about:blank")
            } else if (current.firstOrNull { it.id == tabId }?.isActive == true) {
                repo.setActiveTab(remaining.last().id)
            }
        }
    }

    fun loadInputUrlOrSearch(rawInput: String) {
        val target = BrowserUrlAndMediaHelper.resolveInputToUrl(rawInput, settings.value.searchEngine)
        if (target == "about:blank" && rawInput.isBlank()) return
        val tab = activeTab() ?: return
        viewModelScope.launch {
            repo.upsertTab(tab.copy(url = target, title = target, updatedAt = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                webViews[tab.id]?.loadUrl(target)
            }
        }
    }

    fun onWebProgressChanged(tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        val currentMap = _tabRuntimes.value.toMutableMap()
        currentMap[tabId] = WebTabRuntime(
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            isLoading = progress in 1..99,
            progress = progress / 100f
        )
        _tabRuntimes.value = currentMap
    }

    fun onPageStarted(tabId: String, url: String) {
        val wv = webViews[tabId]
        onWebProgressChanged(tabId, 15, wv?.canGoBack() ?: false, wv?.canGoForward() ?: false)
        viewModelScope.launch {
            val tab = repo.getTabById(tabId) ?: return@launch
            repo.upsertTab(tab.copy(url = url, updatedAt = System.currentTimeMillis()))
        }
    }

    fun onPageFinished(tabId: String, url: String, title: String) {
        val wv = webViews[tabId]
        onWebProgressChanged(tabId, 100, wv?.canGoBack() ?: false, wv?.canGoForward() ?: false)
        viewModelScope.launch {
            val tab = repo.getTabById(tabId) ?: return@launch
            val cleanTitle = title.ifBlank { Uri.parse(url).host ?: url }
            repo.upsertTab(tab.copy(url = url, title = cleanTitle, updatedAt = System.currentTimeMillis()))
            repo.recordHistory(url, cleanTitle)
        }
        pendingPageLoads.remove(tabId)?.complete(Result.success("Loaded '$title' ($url)"))
    }

    fun onPageLoadError(tabId: String, url: String, description: String) {
        val wv = webViews[tabId]
        onWebProgressChanged(tabId, 100, wv?.canGoBack() ?: false, wv?.canGoForward() ?: false)
        pendingPageLoads.remove(tabId)?.complete(
            Result.failure(IllegalStateException("Failed to load $url: $description"))
        )
    }

    fun onTrackerBlocked(tabId: String) {
        viewModelScope.launch {
            prefs.incrementBlockedCount()
            val tab = repo.getTabById(tabId) ?: return@launch
            repo.upsertTab(tab.copy(blockedCount = tab.blockedCount + 1))
        }
    }

    fun goBackUi() {
        val tab = activeTab() ?: return
        webViews[tab.id]?.let { if (it.canGoBack()) it.goBack() }
    }

    fun goForwardUi() {
        val tab = activeTab() ?: return
        webViews[tab.id]?.let { if (it.canGoForward()) it.goForward() }
    }

    fun reloadOrStopUi() {
        val tab = activeTab() ?: return
        val wv = webViews[tab.id] ?: return
        val loading = _tabRuntimes.value[tab.id]?.isLoading == true
        if (loading) wv.stopLoading() else wv.reload()
    }

    fun goHomeUi() {
        val tab = activeTab() ?: return
        viewModelScope.launch {
            repo.upsertTab(tab.copy(url = "about:blank", title = "New Tab", updatedAt = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                webViews[tab.id]?.loadUrl("about:blank")
            }
        }
    }

    fun toggleDesktopMode() {
        val tab = activeTab() ?: return
        val next = !tab.isDesktopMode
        viewModelScope.launch {
            repo.upsertTab(tab.copy(isDesktopMode = next, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleCurrentBookmark() {
        val tab = activeTab() ?: return
        if (tab.url.startsWith("about:")) return
        val exists = bookmarks.value.any { it.url == tab.url }
        viewModelScope.launch {
            repo.toggleBookmark(tab.url, tab.title, exists)
            _statusBanner.value = if (exists) "Removed bookmark" else "Saved bookmark"
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch {
            repo.toggleBookmark(url, url, currentlyBookmarked = true)
            _statusBanner.value = "Removed bookmark"
        }
    }

    fun toggleFindBar(show: Boolean) {
        _showFindBar.value = show
        if (!show) {
            _findInPageQuery.value = ""
            activeTab()?.id?.let { webViews[it]?.clearMatches() }
        }
    }

    fun updateFindQuery(query: String) {
        _findInPageQuery.value = query
        val tabId = activeTab()?.id ?: return
        val wv = webViews[tabId] ?: return
        if (query.isBlank()) wv.clearMatches() else wv.findAllAsync(query)
    }

    fun findNext(forward: Boolean) {
        val tabId = activeTab()?.id ?: return
        webViews[tabId]?.findNext(forward)
    }

    fun openReaderMode() {
        val tab = activeTab() ?: return
        if (tab.url.startsWith("about:")) {
            _statusBanner.value = "Open an article webpage first to use Reader Mode."
            return
        }
        viewModelScope.launch {
            val res = evaluateDomJs(DomBridge.READER_MODE_JS)
            res.onSuccess { raw ->
                runCatching {
                    val obj = JSONObject(raw)
                    if (obj.has("error")) error(obj.getString("error"))
                    _readerArticle.value = ReaderArticle(
                        title = obj.optString("title", tab.title),
                        byline = obj.optString("byline", ""),
                        readMinutes = obj.optInt("readMinutes", 2),
                        content = obj.optString("content", ""),
                        sourceUrl = tab.url
                    )
                }.onFailure {
                    _statusBanner.value = "Reader Mode unavailable on this page."
                }
            }.onFailure {
                _statusBanner.value = it.message ?: "Failed to extract Reader Mode."
            }
        }
    }

    fun closeReaderMode() {
        _readerArticle.value = null
    }

    fun askAgentAboutReader(article: ReaderArticle) {
        _readerArticle.value = null
        _rootScreen.value = RootScreen.AGENT
        sendAgentMessage("Summarize and extract key insights from this article (${article.title}):\n\n${article.content.take(4000)}")
    }

    fun enqueueDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val ctx = getApplication<Application>()
        val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        runCatching {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading via Star Browser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val sysId = dm.enqueue(request)
            viewModelScope.launch {
                repo.recordDownload(sysId, fileName, url, mimeType)
            }
            _statusBanner.value = "Downloading $fileName"
        }.onFailure {
            _statusBanner.value = "Download error: ${it.message}"
        }
    }

    fun deleteDownload(id: Long) = viewModelScope.launch {
        repo.deleteDownload(id)
    }

    fun clearDownloads() = viewModelScope.launch {
        repo.clearDownloads()
        _statusBanner.value = "Cleared download list"
    }

    // --- Agent Conversations & Execution ---

    fun createNewConversation() {
        viewModelScope.launch {
            val id = "thread_${UUID.randomUUID().toString().take(8)}"
            repo.upsertConversation(AgentConversationEntity(id = id, title = "New Conversation"))
            _activeConversationId.value = id
            _grabbedPage.value = null
            _attachedImageBase64.value = null
            _attachedSandboxFiles.value = emptyList()
        }
    }

    fun selectConversation(id: String) {
        _activeConversationId.value = id
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val currentList = conversations.value
            repo.deleteConversation(id)
            if (_activeConversationId.value == id) {
                val next = currentList.firstOrNull { it.id != id }
                if (next != null) {
                    _activeConversationId.value = next.id
                } else {
                    createNewConversation()
                }
            }
        }
    }

    fun grabActivePageContext() {
        val tab = activeTab()
        if (tab == null || tab.url.startsWith("about:")) {
            _statusBanner.value = "Open a webpage in Browser View first to attach page context."
            return
        }
        viewModelScope.launch {
            val snapRes = evaluateDomJs(DomBridge.SNAPSHOT_INTERACTIVE_JS)
            snapRes.onSuccess { jsonStr ->
                val obj = runCatching { JSONObject(jsonStr) }.getOrNull()
                val title = obj?.optString("title").orEmpty().ifBlank { tab.title }
                val url = obj?.optString("url").orEmpty().ifBlank { tab.url }
                val count = obj?.optJSONArray("elements")?.length() ?: 0
                val textSnippet = obj?.optString("text").orEmpty().take(2400)
                _grabbedPage.value = GrabbedPageContext(
                    title = title,
                    url = url,
                    summarySnippet = textSnippet,
                    interactiveCount = count,
                    rawSnapshotJson = jsonStr
                )
                if (activeProvider().supportsVision) {
                    captureViewportBase64()?.let { _attachedImageBase64.value = it }
                }
                _statusBanner.value = "Attached page context ($count interactive elements)"
            }.onFailure {
                _statusBanner.value = "Could not capture page: ${it.message}"
            }
        }
    }

    fun clearGrabbedPage() {
        _grabbedPage.value = null
    }

    fun attachImageBytes(bytes: ByteArray) {
        viewModelScope.launch {
            val compressed = withContext(Dispatchers.Default) {
                BrowserUrlAndMediaHelper.compressImageForVision(bytes)
            }
            if (compressed != null) {
                _attachedImageBase64.value = compressed
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImageBase64.value = null
    }

    fun toggleAttachSandboxFile(path: String) {
        val cur = _attachedSandboxFiles.value
        _attachedSandboxFiles.value = if (path in cur) cur - path else cur + path
    }

    fun sendAgentMessage(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() && _attachedImageBase64.value == null && _grabbedPage.value == null) return
        if (_isAgentRunning.value) return

        val convId = _activeConversationId.value
        val snap = settings.value
        val provider = activeProvider()
        val grabbed = _grabbedPage.value
        val imgB64 = _attachedImageBase64.value
        val sandboxAttachments = _attachedSandboxFiles.value

        val effectiveUserPrompt = buildString {
            append(trimmed.ifBlank { "Analyze the attached page/image context." })
            if (grabbed != null) {
                append("\n\n[Attached Page Snapshot: ${grabbed.title} (${grabbed.url}), ${grabbed.interactiveCount} refs]\n")
                append(grabbed.rawSnapshotJson.take(4500))
            }
        }

        _grabbedPage.value = null
        _attachedImageBase64.value = null
        _attachedSandboxFiles.value = emptyList()

        activeAgentJob = viewModelScope.launch {
            _isAgentRunning.value = true
            _streamingReplyText.value = ""
            _streamingReasoningText.value = ""
            try {
                agentCoordinator.executeTurn(
                    convId = convId,
                    displayInput = trimmed.ifBlank { "Analyze attached page context" },
                    effectiveUserPrompt = effectiveUserPrompt,
                    fallbackTitleSource = trimmed,
                    grabbedTitle = grabbed?.title,
                    provider = provider,
                    settings = snap,
                    attachedImageBase64 = imgB64,
                    attachedSandboxFiles = sandboxAttachments,
                    requestApproval = { action ->
                        val def = CompletableDeferred<ApprovalDecision>()
                        approvalDeferred = def
                        _pendingApproval.value = action
                        val decision = def.await()
                        _pendingApproval.value = null
                        approvalDeferred = null
                        decision
                    },
                    onStreamDelta = { textDelta, reasoningDelta ->
                        if (textDelta.isNotEmpty()) _streamingReplyText.value += textDelta
                        if (reasoningDelta.isNotEmpty()) _streamingReasoningText.value += reasoningDelta
                    },
                    onTurnResetStream = {
                        _streamingReplyText.value = ""
                        _streamingReasoningText.value = ""
                    }
                )
                refreshSandbox()
            } catch (c: CancellationException) {
                throw c
            } finally {
                _streamingReplyText.value = ""
                _streamingReasoningText.value = ""
                _pendingApproval.value = null
                approvalDeferred = null
                _isAgentRunning.value = false
            }
        }
    }

    fun stopAgentRun() {
        approvalDeferred?.complete(ApprovalDecision.DENY)
        _pendingApproval.value = null
        activeAgentJob?.cancel()
        activeAgentJob = null
    }

    fun resolveApproval(decision: ApprovalDecision) {
        approvalDeferred?.complete(decision)
    }

    // --- AgentHostBridge Implementation ---

    override fun currentUrl(): String = activeTab()?.url.orEmpty()
    override fun currentTitle(): String = activeTab()?.title.orEmpty()

    override suspend fun navigateAndWait(url: String, newTab: Boolean): Result<String> {
        val targetTabId: String
        val wv: WebView
        if (newTab) {
            val id = "tab_${UUID.randomUUID().toString().take(8)}"
            val desktop = settings.value.desktopByDefault
            val waiter = CompletableDeferred<WebView>()
            webViewReadyWaiters[id] = waiter
            repo.upsertTab(
                BrowserTabEntity(
                    id = id,
                    url = "about:blank",
                    title = url,
                    isDesktopMode = desktop,
                    isActive = true
                )
            )
            repo.setActiveTab(id)
            targetTabId = id
            wv = try {
                withTimeout(8_000L) { waiter.await() }
            } catch (t: Throwable) {
                webViewReadyWaiters.remove(id)
                return Result.failure(IllegalStateException("WebView creation timed out for $url: ${t.message}"))
            }
        } else {
            val tab = activeTab() ?: return Result.failure(IllegalStateException("No active browser tab."))
            targetTabId = tab.id
            wv = webViews[tab.id] ?: return Result.failure(
                IllegalStateException("WebView surface not initialized for tab ${tab.id}.")
            )
        }

        val deferred = CompletableDeferred<Result<String>>()
        pendingPageLoads[targetTabId]?.cancel()
        pendingPageLoads[targetTabId] = deferred

        repo.getTabById(targetTabId)?.let { existing ->
            repo.upsertTab(existing.copy(url = url, updatedAt = System.currentTimeMillis()))
        }
        withContext(Dispatchers.Main) {
            wv.loadUrl(url)
        }

        return runCatching {
            withTimeout(18_000L) { deferred.await().getOrThrow() }
        }.also {
            pendingPageLoads.remove(targetTabId)
        }
    }

    override suspend fun goBackAndWait(): Result<String> {
        val tab = activeTab() ?: return Result.failure(IllegalStateException("No active tab."))
        val wv = webViews[tab.id] ?: return Result.failure(IllegalStateException("WebView unavailable."))
        val canBack = withContext(Dispatchers.Main) { wv.canGoBack() }
        if (!canBack) return Result.failure(IllegalStateException("No previous page in tab history."))
        val deferred = CompletableDeferred<Result<String>>()
        pendingPageLoads[tab.id] = deferred
        withContext(Dispatchers.Main) { wv.goBack() }
        return runCatching { withTimeout(12_000L) { deferred.await().getOrThrow() } }
            .also { pendingPageLoads.remove(tab.id) }
    }

    override suspend fun goForwardAndWait(): Result<String> {
        val tab = activeTab() ?: return Result.failure(IllegalStateException("No active tab."))
        val wv = webViews[tab.id] ?: return Result.failure(IllegalStateException("WebView unavailable."))
        val canFwd = withContext(Dispatchers.Main) { wv.canGoForward() }
        if (!canFwd) return Result.failure(IllegalStateException("No forward page in tab history."))
        val deferred = CompletableDeferred<Result<String>>()
        pendingPageLoads[tab.id] = deferred
        withContext(Dispatchers.Main) { wv.goForward() }
        return runCatching { withTimeout(12_000L) { deferred.await().getOrThrow() } }
            .also { pendingPageLoads.remove(tab.id) }
    }

    override suspend fun reloadAndWait(): Result<String> {
        val tab = activeTab() ?: return Result.failure(IllegalStateException("No active tab."))
        val wv = webViews[tab.id] ?: return Result.failure(IllegalStateException("WebView unavailable."))
        val deferred = CompletableDeferred<Result<String>>()
        pendingPageLoads[tab.id] = deferred
        withContext(Dispatchers.Main) { wv.reload() }
        return runCatching { withTimeout(15_000L) { deferred.await().getOrThrow() } }
            .also { pendingPageLoads.remove(tab.id) }
    }

    override suspend fun evaluateDomJs(script: String): Result<String> {
        val tab = activeTab() ?: return Result.failure(IllegalStateException("No active browser tab."))
        val wv = webViews[tab.id] ?: return Result.failure(IllegalStateException("WebView not mounted."))
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(script) { rawResult ->
                    val unquoted = unquoteJsResult(rawResult)
                    if (cont.isActive) cont.resume(Result.success(unquoted))
                }
            }
        }
    }

    override suspend fun captureViewportBase64(): String? = withContext(Dispatchers.Main) {
        val tabId = activeTab()?.id ?: return@withContext null
        val wv = webViews[tabId] ?: return@withContext null
        if (wv.width <= 0 || wv.height <= 0) return@withContext null
        runCatching {
            val bmp = Bitmap.createBitmap(wv.width, wv.height.coerceAtMost(1400), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            wv.draw(canvas)
            val scaled = Bitmap.createScaledBitmap(bmp, 720, (720f * bmp.height / bmp.width).toInt().coerceAtLeast(100), true)
            if (scaled !== bmp) bmp.recycle()
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            scaled.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    override fun copyToClipboard(text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Star Browser", text))
        _statusBanner.value = "Copied to clipboard"
    }

    private fun unquoteJsResult(raw: String?): String {
        if (raw == null || raw == "null") return "{}"
        return runCatching {
            JSONObject("{\"v\":$raw}").getString("v")
        }.getOrDefault(raw)
    }

    // --- Settings & Agent Studio Mutators ---

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setAccentPreset(accent: AccentPreset) = viewModelScope.launch { prefs.setAccentPreset(accent) }
    fun setTextScalePreset(scale: TextScalePreset) = viewModelScope.launch { prefs.setTextScalePreset(scale) }
    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAdBlockEnabled(enabled) }
    fun setJavaScriptEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setJavaScriptEnabled(enabled) }
    fun setDesktopByDefault(enabled: Boolean) = viewModelScope.launch { prefs.setDesktopByDefault(enabled) }
    fun setSearchEngine(engine: SearchEngine) = viewModelScope.launch { prefs.setSearchEngine(engine) }
    fun setExecutionMode(mode: ExecutionMode) = viewModelScope.launch { prefs.setExecutionMode(mode) }
    fun setReasoningRequested(enabled: Boolean) = viewModelScope.launch { prefs.setReasoningRequested(enabled) }

    fun clearBrowsingData() {
        viewModelScope.launch {
            repo.clearHistory()
            withContext(Dispatchers.Main) {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                webViews.values.forEach { it.clearCache(true) }
            }
            _statusBanner.value = "Cleared browsing history, cookies, and cache"
        }
    }

    fun selectProvider(id: String) = viewModelScope.launch {
        prefs.setActiveProviderId(id)
        _providerTestStatus.value = null
    }

    fun saveProvider(updated: LlmProviderConfig) = viewModelScope.launch {
        val current = settings.value.providers.toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) current[idx] = updated else current.add(updated)
        prefs.saveProviders(current)
        prefs.setActiveProviderId(updated.id)
        _statusBanner.value = "Saved provider ${updated.name}"
    }

    fun testProviderConnection(provider: LlmProviderConfig) {
        viewModelScope.launch {
            _providerTestStatus.value = "Testing ${provider.name}..."
            val res = brain.testConnection(provider)
            _providerTestStatus.value = res.fold(
                onSuccess = { it },
                onFailure = { "Connection failed: ${it.message}" }
            )
        }
    }

    fun selectPromptPreset(id: String) = viewModelScope.launch { prefs.setActivePromptId(id) }

    fun savePromptPreset(preset: SystemPromptPreset) = viewModelScope.launch {
        val list = settings.value.prompts.toMutableList()
        val idx = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        prefs.savePrompts(list)
        prefs.setActivePromptId(preset.id)
        _statusBanner.value = "Saved prompt '${preset.name}'"
    }

    fun toggleSkill(skillId: String) = viewModelScope.launch {
        val updated = settings.value.skills.map {
            if (it.id == skillId) it.copy(enabled = !it.enabled) else it
        }
        prefs.saveSkills(updated)
    }

    fun addOrUpdateSkill(skill: AgentSkill) = viewModelScope.launch {
        val list = settings.value.skills.toMutableList()
        val idx = list.indexOfFirst { it.id == skill.id }
        if (idx >= 0) list[idx] = skill else list.add(skill)
        prefs.saveSkills(list)
    }

    fun toggleToolkit(toolkit: ToolkitId) = viewModelScope.launch {
        val cur = settings.value.enabledToolkits.toMutableSet()
        if (toolkit in cur) cur.remove(toolkit) else cur.add(toolkit)
        prefs.saveToolkits(cur)
    }

    fun saveAndDiscoverConnector(connector: McpConnectorConfig) = viewModelScope.launch {
        val list = settings.value.mcpConnectors.toMutableList()
        val idx = list.indexOfFirst { it.id == connector.id }
        if (idx >= 0) list[idx] = connector else list.add(connector)
        prefs.saveConnectors(list)

        val discovered = mcpClient.discoverTools(connector)
        discovered.onSuccess { tools ->
            val updated = settings.value.mcpConnectors.map {
                if (it.id == connector.id) it.copy(discoveredTools = tools) else it
            }
            prefs.saveConnectors(updated)
            _statusBanner.value = "Discovered ${tools.size} tools from ${connector.name}"
        }.onFailure { err ->
            _statusBanner.value = "Saved connector (${err.message})"
        }
    }

    fun toggleConnectorEnabled(id: String) = viewModelScope.launch {
        val updated = settings.value.mcpConnectors.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        prefs.saveConnectors(updated)
    }

    fun deleteConnector(id: String) = viewModelScope.launch {
        prefs.saveConnectors(settings.value.mcpConnectors.filterNot { it.id == id })
    }

    fun setSandboxInternetEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSandboxInternetEnabled(enabled)
    }

    fun linkExternalSafFolder(uri: Uri?) = viewModelScope.launch {
        prefs.setSandboxFolderUri(uri?.toString())
        _statusBanner.value = if (uri != null) "External folder linked" else "External folder unlinked"
    }

    fun refreshSafFiles() = viewModelScope.launch {
        _safFiles.value = sandboxManager.listSafFiles(settings.value.sandboxFolderUri)
    }

    fun importSafFile(entry: SafEntry) = viewModelScope.launch {
        val treeUri = settings.value.sandboxFolderUri ?: return@launch
        sandboxManager.importFromSaf(treeUri, entry.documentId, entry.name).onSuccess { imported ->
            refreshSandbox()
            _statusBanner.value = "Imported ${imported.relativePath}"
        }.onFailure {
            _statusBanner.value = it.message ?: "Import failed"
        }
    }

    fun exportSandboxFileToSaf(relativePath: String) = viewModelScope.launch {
        val treeUri = settings.value.sandboxFolderUri
        if (treeUri.isNullOrBlank()) {
            _statusBanner.value = "Link a device folder first in Sandbox."
            return@launch
        }
        sandboxManager.exportToSaf(relativePath, treeUri).onSuccess { name ->
            refreshSafFiles()
            _statusBanner.value = "Exported $name to linked folder"
        }.onFailure {
            _statusBanner.value = it.message ?: "Export failed"
        }
    }

    fun refreshSandbox() = viewModelScope.launch {
        _sandboxFiles.value = sandboxManager.listFiles()
        _sandboxUsedBytes.value = sandboxManager.usedBytes()
    }

    fun writeSandboxFile(path: String, content: String) = viewModelScope.launch {
        sandboxManager.writeFile(path, content).onSuccess {
            refreshSandbox()
            _statusBanner.value = "Saved ${it.relativePath}"
        }.onFailure {
            _statusBanner.value = it.message ?: "Sandbox write failed"
        }
    }

    fun deleteSandboxFile(path: String) = viewModelScope.launch {
        sandboxManager.deleteFile(path).onSuccess {
            refreshSandbox()
            _statusBanner.value = "Deleted $path"
        }
    }

    fun requestOnDemandPermission(kind: PermissionRequestKind) {
        _pendingPermissionDialog.value = kind
    }

    fun dismissOnDemandPermission() {
        _pendingPermissionDialog.value = null
    }
}
