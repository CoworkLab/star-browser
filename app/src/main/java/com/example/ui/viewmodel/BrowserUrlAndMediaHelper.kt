package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.preferences.SearchEngine
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object BrowserUrlAndMediaHelper {

    private val ipv4OrLocalhostRegex = Regex(
        """^(localhost|(?:[0-9]{1,3}\.){3}[0-9]{1,3})(:\d{1,5})?(/.*)?$""",
        RegexOption.IGNORE_CASE
    )

    fun resolveInputToUrl(rawInput: String, searchEngine: SearchEngine): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "about:blank"
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:") -> trimmed
            ipv4OrLocalhostRegex.matches(trimmed) -> "http://$trimmed"
            !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
            else -> searchEngine.queryTemplate + Uri.encode(trimmed)
        }
    }

    fun compressImageForVision(
        bytes: ByteArray,
        maxEdgePx: Int = 1280,
        jpegQuality: Int = 80
    ): String? {
        if (bytes.isEmpty()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) {
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        var sampleSize = 1
        while (max(rawW / sampleSize, rawH / sampleSize) > maxEdgePx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return Base64.encodeToString(bytes, Base64.NO_WRAP)

        val longest = max(decoded.width, decoded.height)
        val finalBitmap = if (longest > maxEdgePx) {
            val ratio = maxEdgePx.toFloat() / longest.toFloat()
            val targetW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        return try {
            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            finalBitmap.recycle()
        }
    }

    fun buildFallbackIntentTitle(rawPrompt: String, grabbedTitle: String?): String {
        val base = rawPrompt.trim().ifBlank { grabbedTitle ?: "New Chat" }
        val cleaned = base
            .replace(
                Regex("""^(hey agent|agent|please|can you|could you|help me|i want to)\s+""", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()
            .ifBlank { base }
        return cleaned.split(Regex("""\s+"""))
            .take(5)
            .joinToString(" ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(38)
    }
}
