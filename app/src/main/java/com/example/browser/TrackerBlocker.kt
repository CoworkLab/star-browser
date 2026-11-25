package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object TrackerBlocker {

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adservice.google.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "segment.com",
        "hotjar.com",
        "fullstory.com",
        "quantserve.com",
        "chartbeat.com",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "moatads.com"
    )

    private val blockedPathTokens = listOf(
        "/pagead/",
        "/gtag/js",
        "/fbevents.js",
        "/analytics.js",
        "/adserver"
    )

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
            return true
        }
        val path = uri.encodedPath?.lowercase().orEmpty()
        return blockedPathTokens.any { token -> path.contains(token) }
    }

    fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        200,
        "OK",
        mapOf("Access-Control-Allow-Origin" to "*"),
        ByteArrayInputStream(ByteArray(0))
    )
}
