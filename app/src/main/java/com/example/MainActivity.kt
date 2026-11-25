package com.example

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AgentStudioSheet
import com.example.ui.components.AgentView
import com.example.ui.components.BookmarksSheet
import com.example.ui.components.BrowserMenuSheet
import com.example.ui.components.BrowserView
import com.example.ui.components.BrowserWebViewHost
import com.example.ui.components.DownloadsSheet
import com.example.ui.components.HistorySheet
import com.example.ui.components.OnDemandPermissionDialog
import com.example.ui.components.ReaderModeOverlay
import com.example.ui.components.SettingsOverlay
import com.example.ui.components.TabSwitcherOverlay
import com.example.ui.theme.StarBrowserTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.BrowserViewModel
import com.example.ui.viewmodel.PermissionRequestKind
import com.example.ui.viewmodel.RootScreen
import com.example.ui.viewmodel.StudioTab
import com.example.ui.viewmodel.WebTabRuntime
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StarBrowserApp()
        }
    }
}

@Composable
fun StarBrowserApp(vm: BrowserViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val activeConversationId by vm.activeConversationId.collectAsStateWithLifecycle()
    val messages by vm.activeMessages.collectAsStateWithLifecycle()
    val rootScreen by vm.rootScreen.collectAsStateWithLifecycle()
    val tabRuntimes by vm.tabRuntimes.collectAsStateWithLifecycle()
    val isAgentRunning by vm.isAgentRunning.collectAsStateWithLifecycle()
    val streamingReplyText by vm.streamingReplyText.collectAsStateWithLifecycle()
    val streamingReasoningText by vm.streamingReasoningText.collectAsStateWithLifecycle()
    val pendingApproval by vm.pendingApproval.collectAsStateWithLifecycle()
    val attachedImage by vm.attachedImageBase64.collectAsStateWithLifecycle()
    val attachedSandboxFiles by vm.attachedSandboxFiles.collectAsStateWithLifecycle()
    val grabbedPage by vm.grabbedPage.collectAsStateWithLifecycle()
    val sandboxFiles by vm.sandboxFiles.collectAsStateWithLifecycle()
    val sandboxUsedBytes by vm.sandboxUsedBytes.collectAsStateWithLifecycle()
    val linkedSafLabel by vm.linkedSafLabel.collectAsStateWithLifecycle()
    val safFiles by vm.safFiles.collectAsStateWithLifecycle()
    val readerArticle by vm.readerArticle.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val statusBanner by vm.statusBanner.collectAsStateWithLifecycle()
    val providerTestStatus by vm.providerTestStatus.collectAsStateWithLifecycle()
    val findQuery by vm.findInPageQuery.collectAsStateWithLifecycle()
    val showFindBar by vm.showFindBar.collectAsStateWithLifecycle()
    val pendingPermission by vm.pendingPermissionDialog.collectAsStateWithLifecycle()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showBrowserMenu by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var studioTab by remember { mutableStateOf<StudioTab?>(null) }
    var voiceDraftText by remember { mutableStateOf<String?>(null) }
    var topChromePx by remember { mutableIntStateOf(0) }
    var bottomDockPx by remember { mutableIntStateOf(0) }
    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearStatusBanner()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                vm.attachImageBytes(bytes)
            }
        }
    }

    val webFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val cb = pendingFileChooserCallback
        pendingFileChooserCallback = null
        cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.linkExternalSafFolder(uri)
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceDraftText = spoken
            }
        }
    }

    fun launchSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Agent")
        }
        runCatching {
            speechRecognizerLauncher.launch(intent)
        }.onFailure {
            voiceDraftText = null
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechInput()
        }
    }

    fun openAndroidAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    val isDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val activeTab = tabs.firstOrNull { it.isActive } ?: tabs.firstOrNull()
    val activeProvider = settings.providers.firstOrNull { it.id == settings.activeProviderId }
        ?: settings.providers.first()
    val activeTabHost = activeTab?.url?.takeIf { !it.startsWith("about:") }?.let {
        Uri.parse(it).host ?: ""
    }.orEmpty()

    StarBrowserTheme(
        themeMode = settings.themeMode,
        accentPreset = settings.accentPreset,
        textScalePreset = settings.textScalePreset
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Keep WebView host mounted beneath Agent/Browser views so live DOM snapshots work seamlessly
            BrowserWebViewHost(
                tabs = tabs,
                adBlockEnabled = settings.adBlockEnabled,
                javaScriptEnabled = settings.javaScriptEnabled,
                isDarkTheme = isDark,
                isBrowserVisible = rootScreen == RootScreen.BROWSER,
                topChromePx = topChromePx,
                bottomDockPx = bottomDockPx,
                onRegisterWebView = vm::registerWebView,
                onUnregisterWebView = vm::unregisterWebView,
                onPageStarted = vm::onPageStarted,
                onPageFinished = vm::onPageFinished,
                onPageError = vm::onPageLoadError,
                onProgressChanged = vm::onWebProgressChanged,
                onTrackerBlocked = vm::onTrackerBlocked,
                onDownloadRequested = vm::enqueueDownload,
                onExternalSchemeUri = { uri ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        scope.launch {
                            snackbarHostState.showSnackbar("No app found to handle ${uri.scheme ?: "external"} link")
                        }
                    }
                },
                onOpenNewTabRequested = { url ->
                    vm.newTab(url, switchToBrowser = true)
                },
                onFileChooserRequested = { callback, params ->
                    pendingFileChooserCallback?.onReceiveValue(null)
                    pendingFileChooserCallback = callback
                    val accept = params?.acceptTypes
                        ?.map { it.trim() }
                        ?.filter { it.contains("/") }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() }
                        ?: arrayOf("*/*")
                    runCatching {
                        webFileChooserLauncher.launch(accept)
                    }.onFailure {
                        pendingFileChooserCallback?.onReceiveValue(null)
                        pendingFileChooserCallback = null
                    }
                }
            )

            when (rootScreen) {
                RootScreen.AGENT -> {
                    AgentView(
                        conversations = conversations,
                        activeConversationId = activeConversationId,
                        messages = messages,
                        activeProvider = activeProvider,
                        executionMode = settings.executionMode,
                        reasoningRequested = settings.reasoningRequested,
                        isAgentRunning = isAgentRunning,
                        streamingReplyText = streamingReplyText,
                        streamingReasoningText = streamingReasoningText,
                        pendingApproval = pendingApproval,
                        grabbedPage = grabbedPage,
                        hasAttachedImage = attachedImage != null,
                        attachedSandboxFiles = attachedSandboxFiles,
                        sandboxFiles = sandboxFiles,
                        activeTabHost = activeTabHost,
                        onSelectConversation = vm::selectConversation,
                        onNewConversation = vm::createNewConversation,
                        onDeleteConversation = vm::deleteConversation,
                        onModeChange = vm::setExecutionMode,
                        onToggleReasoning = vm::setReasoningRequested,
                        onSendMessage = vm::sendAgentMessage,
                        onStopRun = vm::stopAgentRun,
                        onResolveApproval = vm::resolveApproval,
                        onGrabPageContext = vm::grabActivePageContext,
                        onClearGrabbedPage = vm::clearGrabbedPage,
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearImage = vm::clearAttachedImage,
                        onToggleSandboxFile = vm::toggleAttachSandboxFile,
                        onStartVoiceInput = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) launchSpeechInput()
                            else vm.requestOnDemandPermission(PermissionRequestKind.MICROPHONE)
                        },
                        onOpenStudioProviders = { studioTab = StudioTab.PROVIDERS },
                        onOpenStudioRoot = { studioTab = StudioTab.PROVIDERS },
                        onOpenSettings = { showSettings = true },
                        onSwitchToBrowser = { vm.switchRootScreen(RootScreen.BROWSER) },
                        onOpenUrlInBrowser = { url ->
                            vm.loadInputUrlOrSearch(url)
                            vm.switchRootScreen(RootScreen.BROWSER)
                        },
                        onCopyText = vm::copyToClipboard,
                        voiceDraftText = voiceDraftText,
                        onConsumeVoiceDraft = { voiceDraftText = null }
                    )
                }

                RootScreen.BROWSER -> {
                    BackHandler {
                        val rt = activeTab?.id?.let { tabRuntimes[it] }
                        if (rt?.canGoBack == true) vm.goBackUi()
                        else vm.switchRootScreen(RootScreen.AGENT)
                    }
                    BrowserView(
                        activeTab = activeTab,
                        tabCount = tabs.size.coerceAtLeast(1),
                        runtime = activeTab?.id?.let { tabRuntimes[it] } ?: WebTabRuntime(),
                        history = history,
                        showFindBar = showFindBar,
                        findQuery = findQuery,
                        onNavigateUrl = vm::loadInputUrlOrSearch,
                        onBack = vm::goBackUi,
                        onForward = vm::goForwardUi,
                        onReloadOrStop = vm::reloadOrStopUi,
                        onHome = vm::goHomeUi,
                        onOpenTabSwitcher = { showTabSwitcher = true },
                        onOpenMenu = { showBrowserMenu = true },
                        onSwitchToAgent = { vm.switchRootScreen(RootScreen.AGENT) },
                        onAskAgentCurrentPage = {
                            vm.grabActivePageContext()
                            vm.switchRootScreen(RootScreen.AGENT)
                        },
                        onUpdateFindQuery = vm::updateFindQuery,
                        onFindNext = vm::findNext,
                        onCloseFindBar = { vm.toggleFindBar(false) },
                        onTopChromeHeightPx = { topChromePx = it },
                        onBottomDockHeightPx = { bottomDockPx = it }
                    )
                }
            }

            if (showTabSwitcher) {
                TabSwitcherOverlay(
                    tabs = tabs,
                    onSelectTab = vm::selectTab,
                    onCloseTab = vm::closeTab,
                    onNewTab = { vm.newTab("about:blank") },
                    onDismiss = { showTabSwitcher = false }
                )
            }

            if (showSettings) {
                SettingsOverlay(
                    settings = settings,
                    sandboxUsedBytes = sandboxUsedBytes,
                    onSetThemeMode = vm::setThemeMode,
                    onSetAccentPreset = vm::setAccentPreset,
                    onSetTextScalePreset = vm::setTextScalePreset,
                    onSetAdBlock = vm::setAdBlockEnabled,
                    onSetJavaScript = vm::setJavaScriptEnabled,
                    onSetDesktopDefault = vm::setDesktopByDefault,
                    onSetSearchEngine = vm::setSearchEngine,
                    onOpenAgentStudio = { studioTab = StudioTab.PROVIDERS },
                    onOpenDownloads = { showDownloads = true },
                    onClearBrowsingData = vm::clearBrowsingData,
                    onDismiss = { showSettings = false }
                )
            }

            studioTab?.let { initial ->
                AgentStudioSheet(
                    initialTab = initial,
                    settings = settings,
                    providerTestStatus = providerTestStatus,
                    sandboxFiles = sandboxFiles,
                    sandboxUsedBytes = sandboxUsedBytes,
                    linkedSafLabel = linkedSafLabel,
                    safFiles = safFiles,
                    onSelectProvider = vm::selectProvider,
                    onSaveProvider = vm::saveProvider,
                    onTestProvider = vm::testProviderConnection,
                    onSelectPrompt = vm::selectPromptPreset,
                    onSavePrompt = vm::savePromptPreset,
                    onToggleSkill = vm::toggleSkill,
                    onAddSkill = vm::addOrUpdateSkill,
                    onToggleToolkit = vm::toggleToolkit,
                    onSaveConnector = vm::saveAndDiscoverConnector,
                    onToggleConnector = vm::toggleConnectorEnabled,
                    onDeleteConnector = vm::deleteConnector,
                    onSetSandboxInternet = vm::setSandboxInternetEnabled,
                    onWriteSandboxFile = vm::writeSandboxFile,
                    onDeleteSandboxFile = vm::deleteSandboxFile,
                    onReadSandboxFile = { path -> vm.sandboxManager.readFile(path) },
                    onRequestLinkSafFolder = {
                        vm.requestOnDemandPermission(PermissionRequestKind.EXTERNAL_FOLDER)
                    },
                    onUnlinkSafFolder = { vm.linkExternalSafFolder(null) },
                    onImportSafFile = vm::importSafFile,
                    onExportSandboxFile = vm::exportSandboxFileToSaf,
                    onRequestMicCheck = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchSpeechInput()
                        else vm.requestOnDemandPermission(PermissionRequestKind.MICROPHONE)
                    },
                    onOpenAppSettings = { openAndroidAppSettings() },
                    onTryInChat = {
                        studioTab = null
                        vm.switchRootScreen(RootScreen.AGENT)
                    },
                    onDismiss = { studioTab = null }
                )
            }

            readerArticle?.let { article ->
                ReaderModeOverlay(
                    article = article,
                    onAskAgent = vm::askAgentAboutReader,
                    onDismiss = vm::closeReaderMode
                )
            }

            if (showBrowserMenu) {
                val isBookmarked = activeTab?.url?.let { u -> bookmarks.any { it.url == u } } ?: false
                BrowserMenuSheet(
                    isBookmarked = isBookmarked,
                    isDesktopMode = activeTab?.isDesktopMode ?: false,
                    onNewTab = { vm.newTab("about:blank", switchToBrowser = true) },
                    onToggleBookmark = vm::toggleCurrentBookmark,
                    onOpenBookmarks = { showBookmarks = true },
                    onOpenHistory = { showHistory = true },
                    onOpenDownloads = { showDownloads = true },
                    onOpenReader = vm::openReaderMode,
                    onFindInPage = { vm.toggleFindBar(true) },
                    onToggleDesktopMode = vm::toggleDesktopMode,
                    onOpenSettings = { showSettings = true },
                    onDismiss = { showBrowserMenu = false }
                )
            }

            if (showBookmarks) {
                BookmarksSheet(
                    bookmarks = bookmarks,
                    onOpenUrl = { url ->
                        vm.loadInputUrlOrSearch(url)
                        vm.switchRootScreen(RootScreen.BROWSER)
                    },
                    onDeleteBookmark = { bm ->
                        vm.removeBookmark(bm.url)
                    },
                    onDismiss = { showBookmarks = false }
                )
            }

            if (showHistory) {
                HistorySheet(
                    history = history,
                    onOpenUrl = { url ->
                        vm.loadInputUrlOrSearch(url)
                        vm.switchRootScreen(RootScreen.BROWSER)
                    },
                    onClearHistory = vm::clearBrowsingData,
                    onDismiss = { showHistory = false }
                )
            }

            if (showDownloads) {
                DownloadsSheet(
                    downloads = downloads,
                    onOpenDownload = { d ->
                        runCatching {
                            context.startActivity(
                                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar("Saved ${d.fileName} to Downloads")
                            }
                        }
                    },
                    onDeleteDownload = vm::deleteDownload,
                    onClearDownloads = vm::clearDownloads,
                    onDismiss = { showDownloads = false }
                )
            }

            pendingPermission?.let { kind ->
                OnDemandPermissionDialog(
                    kind = kind,
                    onAllow = {
                        vm.dismissOnDemandPermission()
                        when (kind) {
                            PermissionRequestKind.MICROPHONE ->
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            PermissionRequestKind.EXTERNAL_FOLDER ->
                                safFolderLauncher.launch(null)
                        }
                    },
                    onOpenSettings = {
                        vm.dismissOnDemandPermission()
                        openAndroidAppSettings()
                    },
                    onDismiss = vm::dismissOnDemandPermission
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp)
            )
        }
    }
}
