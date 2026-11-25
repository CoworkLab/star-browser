package com.example.sandbox

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SandboxFileEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

data class SafEntry(
    val documentId: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
)

class MobileSandboxManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val SANDBOX_DIR_NAME = "agent_sandbox"
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024 // 50 MB lightweight cap
        const val MAX_FILE_BYTES = 512 * 1024        // 512 KB per-file cap
    }

    private val rootDir: File by lazy {
        File(context.filesDir, SANDBOX_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun resolveSafePath(relativePath: String): File {
        val cleaned = relativePath.trim().trimStart('/', '\\')
        require(cleaned.isNotBlank()) { "Sandbox path must not be blank." }
        require(!cleaned.contains("..")) { "Path traversal ('..') is forbidden in sandbox." }
        val rootCanonical = rootDir.canonicalFile
        val target = File(rootCanonical, cleaned).canonicalFile
        val rootPrefix = rootCanonical.path.trimEnd(File.separatorChar) + File.separator
        require(target.path.startsWith(rootPrefix)) {
            "Path escapes mobile sandbox boundary."
        }
        return target
    }

    suspend fun listFiles(): List<SandboxFileEntry> = withContext(ioDispatcher) {
        val rootCanonical = rootDir.canonicalFile
        if (!rootCanonical.exists()) return@withContext emptyList()
        rootCanonical.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val rel = file.canonicalPath.removePrefix(rootCanonical.canonicalPath)
                    .trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                SandboxFileEntry(
                    relativePath = rel,
                    sizeBytes = file.length(),
                    lastModifiedMs = file.lastModified()
                )
            }
            .sortedByDescending { it.lastModifiedMs }
            .toList()
    }

    suspend fun usedBytes(): Long = withContext(ioDispatcher) {
        rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun readFile(relativePath: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val file = resolveSafePath(relativePath)
            require(file.exists() && file.isFile) { "File '$relativePath' does not exist in sandbox." }
            require(file.length() <= MAX_FILE_BYTES) {
                "File '$relativePath' exceeds 512 KB read cap."
            }
            file.readText(Charsets.UTF_8)
        }
    }

    suspend fun writeFile(
        relativePath: String,
        content: String,
        append: Boolean = false
    ): Result<SandboxFileEntry> = withContext(ioDispatcher) {
        runCatching {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val file = resolveSafePath(relativePath)
            val existingSize = if (file.exists()) file.length() else 0L
            val projectedFileSize = if (append) existingSize + bytes.size else bytes.size.toLong()
            require(projectedFileSize <= MAX_FILE_BYTES) {
                "File '$relativePath' exceeds 512 KB sandbox per-file cap."
            }
            val currentTotal = usedBytes()
            val projectedTotal = if (append) currentTotal + bytes.size else currentTotal - existingSize + bytes.size
            require(projectedTotal <= MAX_TOTAL_BYTES) {
                "Sandbox quota (50 MB) exceeded."
            }
            file.parentFile?.mkdirs()
            if (append && file.exists()) {
                file.appendText(content, Charsets.UTF_8)
            } else {
                file.writeText(content, Charsets.UTF_8)
            }
            SandboxFileEntry(
                relativePath = relativePath.trim().trimStart('/'),
                sizeBytes = file.length(),
                lastModifiedMs = file.lastModified()
            )
        }
    }

    suspend fun deleteFile(relativePath: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val file = resolveSafePath(relativePath)
            require(file.exists() && file.isFile) { "File '$relativePath' not found." }
            check(file.delete()) { "Failed to delete '$relativePath'." }
        }
    }

    suspend fun describeLinkedSafFolder(treeUriString: String?): String? = withContext(ioDispatcher) {
        if (treeUriString.isNullOrBlank()) return@withContext null
        runCatching {
            val uri = Uri.parse(treeUriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment
        }.getOrNull()
    }

    suspend fun listSafFiles(treeUriString: String?): List<SafEntry> = withContext(ioDispatcher) {
        if (treeUriString.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            val entries = mutableListOf<SafEntry>()
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: docId
                    val size = cursor.getLong(2)
                    val mime = cursor.getString(3) ?: "application/octet-stream"
                    if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        entries += SafEntry(docId, name, size, mime)
                    }
                }
            }
            entries.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    suspend fun importFromSaf(
        treeUriString: String,
        documentId: String,
        targetRelativePath: String
    ): Result<SandboxFileEntry> = withContext(ioDispatcher) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val rawBytes = context.contentResolver.openInputStream(docUri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    require(total <= MAX_FILE_BYTES) {
                        "File exceeds 512 KB sandbox import cap."
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: error("Unable to open SAF document.")

            val target = resolveSafePath(targetRelativePath)
            val existingSize = if (target.exists()) target.length() else 0L
            val currentTotal = usedBytes()
            require(currentTotal - existingSize + rawBytes.size <= MAX_TOTAL_BYTES) {
                "Sandbox quota (50 MB) exceeded."
            }
            target.parentFile?.mkdirs()
            target.writeBytes(rawBytes)
            SandboxFileEntry(
                relativePath = targetRelativePath.trim().trimStart('/'),
                sizeBytes = target.length(),
                lastModifiedMs = target.lastModified()
            )
        }
    }

    suspend fun exportToSaf(
        relativePath: String,
        treeUriString: String,
        mimeType: String = "text/plain"
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val source = resolveSafePath(relativePath)
            require(source.exists() && source.isFile) { "File '$relativePath' not found in sandbox." }
            val treeUri = Uri.parse(treeUriString)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val exportName = source.name
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                exportName
            ) ?: error("Could not create document in linked folder.")
            context.contentResolver.openOutputStream(createdUri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Could not write exported file.")
            exportName
        }
    }
}
