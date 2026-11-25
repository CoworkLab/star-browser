package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.example.browser.TrackerBlocker
import com.example.data.local.BrowserTabEntity
import java.util.WeakHashMap

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebViewHost(
    tabs: List<BrowserTabEntity>,
    adBlockEnabled: Boolean,
    javaScriptEnabled: Boolean,
    isDarkTheme: Boolean,
    isBrowserVisible: Boolean,
    topChromePx: Int,
    bottomDockPx: Int,
    onRegisterWebView: (String, WebView) -> Unit,
    onUnregisterWebView: (String) -> Unit,
    onPageStarted: (String, String) -> Unit,
    onPageFinished: (String, String, String) -> Unit,
    onPageError: (String, String, String) -> Unit,
    onProgressChanged: (String, Int, Boolean, Boolean) -> Unit,
    onTrackerBlocked: (String) -> Unit,
    onDownloadRequested: (String, String?, String?) -> Unit,
    onExternalSchemeUri: (Uri) -> Unit,
    onOpenNewTabRequested: (String) -> Unit,
    onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val topPadding = if (isBrowserVisible) with(density) { topChromePx.toDp() } else with(density) { 0.toDp() }
    val bottomPadding = if (isBrowserVisible) with(density) { bottomDockPx.toDp() } else with(density) { 0.toDp() }
    val desktopUaTrackers = remember { WeakHashMap<WebView, Boolean>() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        tabs.forEach { tab ->
            key(tab.id) {
                val showSurface = tab.isActive && !tab.url.startsWith("about:")
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = javaScriptEnabled
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                settings.isAlgorithmicDarkeningAllowed = isDarkTheme
                            }
                            if (tab.isDesktopMode) {
                                settings.userAgentString = DESKTOP_USER_AGENT
                            }
                            desktopUaTrackers[this] = tab.isDesktopMode

                            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                onDownloadRequested(url, contentDisposition, mimeType)
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    onProgressChanged(
                                        tab.id,
                                        newProgress,
                                        view?.canGoBack() ?: false,
                                        view?.canGoForward() ?: false
                                    )
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val hitExtra = view?.hitTestResult?.extra
                                    if (!hitExtra.isNullOrBlank() &&
                                        (hitExtra.startsWith("http://", true) || hitExtra.startsWith("https://", true))
                                    ) {
                                        onOpenNewTabRequested(hitExtra)
                                        return false
                                    }
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    val tempWebView = WebView(context).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                v: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val target = request?.url?.toString().orEmpty()
                                                if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                                                    onOpenNewTabRequested(target)
                                                }
                                                destroy()
                                                return true
                                            }
                                        }
                                    }
                                    transport.webView = tempWebView
                                    resultMsg.sendToTarget()
                                    return true
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    if (filePathCallback == null) return false
                                    onFileChooserRequested(filePathCallback, fileChooserParams)
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString()
                                    if (adBlockEnabled && TrackerBlocker.shouldBlock(reqUrl)) {
                                        onTrackerBlocked(tab.id)
                                        return TrackerBlocker.emptyResponse()
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val scheme = uri.scheme?.lowercase().orEmpty()
                                    if (scheme == "http" || scheme == "https" || uri.toString().startsWith("about:")) {
                                        return false
                                    }
                                    if (scheme in setOf("javascript", "file", "data", "content")) {
                                        return true
                                    }
                                    onExternalSchemeUri(uri)
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!url.isNullOrBlank()) {
                                        onPageStarted(tab.id, url)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val finalUrl = url.orEmpty()
                                    val title = view?.title.orEmpty()
                                    if (finalUrl.isNotBlank()) {
                                        onPageFinished(tab.id, finalUrl, title)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        onPageError(
                                            tab.id,
                                            request.url?.toString().orEmpty(),
                                            error?.description?.toString() ?: "Page load error"
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        onProgressChanged(
                                            tab.id,
                                            100,
                                            view?.canGoBack() ?: false,
                                            view?.canGoForward() ?: false
                                        )
                                    }
                                }
                            }

                            onRegisterWebView(tab.id, this)
                            if (tab.url.isNotBlank() && !tab.url.startsWith("about:")) {
                                loadUrl(tab.url)
                            }
                        }
                    },
                    update = { wv ->
                        wv.visibility = if (showSurface) android.view.View.VISIBLE else android.view.View.INVISIBLE
                        wv.settings.javaScriptEnabled = javaScriptEnabled
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            wv.settings.isAlgorithmicDarkeningAllowed = isDarkTheme
                        }
                        val trackedDesktop = desktopUaTrackers[wv]
                        if (trackedDesktop != tab.isDesktopMode) {
                            desktopUaTrackers[wv] = tab.isDesktopMode
                            wv.settings.userAgentString = if (tab.isDesktopMode) DESKTOP_USER_AGENT else null
                            if (!tab.url.startsWith("about:")) {
                                wv.reload()
                            }
                        }
                    }
                )
                DisposableEffect(tab.id) {
                    onDispose {
                        onUnregisterWebView(tab.id)
                    }
                }
            }
        }
    }
}
