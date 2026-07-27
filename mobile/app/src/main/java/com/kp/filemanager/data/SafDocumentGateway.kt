/** mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt: Implements bounded SAF discovery and exact overwrite-safe file mutation for Android document providers; refactored for media organization. */
package com.kp.filemanager.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.kp.filemanager.domain.DocumentEntry
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import java.io.FileNotFoundException
import java.io.IOException

class SafDocumentGateway(context: Context) : DocumentGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun displayName(treeUri: String): String =
        queryDocument(treeDocumentUri(treeUri))?.name ?: "Selected folder"

    override fun root(treeUri: String): DocumentEntry {
        val uri = treeDocumentUri(treeUri)
        val document = requireNotNull(queryDocument(uri)) { "The selected folder is no longer available." }
        require(document.isDirectory) { "The selected item is not a folder." }
        return document.toEntry(uri, "")
    }

    override fun children(directoryUri: String): List<DocumentEntry> {
        val parent = Uri.parse(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        return resolver.query(childrenUri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val document = cursor.toDocumentRecord(Uri.EMPTY)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(parent, document.documentId)
                    add(document.copy(uri = uri).toEntry(uri, directoryUri))
                }
            }
        } ?: emptyList()
    }

    override fun documentExists(uri: String): Boolean = try {
        queryDocument(Uri.parse(uri)) != null
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun documentExistsInParent(uri: String, parentUri: String): Boolean =
        children(parentUri).any { it.uri == uri }

    override fun destinationExists(treeUri: String, relativePath: String, excludingUri: String?): Boolean {
        val destination = resolvePath(treeDocumentUri(treeUri), relativePath) ?: return false
        return excludingUri == null || destination.toString() != excludingUri
    }

    override fun ensureDirectoryPath(treeUri: String, relativePath: String): PreparedDirectory {
        var current = treeDocumentUri(treeUri)
        val created = mutableListOf<String>()
        relativePath.split('/').filter(String::isNotBlank).forEach { segment ->
            val matches = findChildrenByName(current, segment)
            require(matches.size <= 1) { "Multiple folders match '$segment' on this provider." }
            val existing = matches.singleOrNull()
            current = when {
                existing == null -> {
                    val createdUri = requireNotNull(
                        DocumentsContract.createDocument(
                            resolver,
                            current,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            segment,
                        ),
                    ) { "Could not create $segment." }
                    created += createdUri.toString()
                    createdUri
                }
                existing.isDirectory -> existing.uri
                else -> error("$segment already exists as a file.")
            }
        }
        return PreparedDirectory(current.toString(), created)
    }

    override fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation {
        val source = Uri.parse(operation.sourceUri)
        val sourceParent = Uri.parse(operation.sourceParentUri)
        val targetParent = Uri.parse(targetParentUri)
        check(findChildrenByName(targetParent, operation.newName).isEmpty()) {
            "A destination file with this name already exists."
        }
        val afterUri = when (operation.operationType) {
            OperationType.RENAME -> rename(source, operation.newName)
            OperationType.MOVE -> moveOrCopy(source, sourceParent, targetParent, operation.newName)
            OperationType.MOVE_AND_RENAME -> copyThenDelete(source, targetParent, operation.newName)
            OperationType.SKIP -> error("Skipped operations cannot be applied.")
        }
        return DocumentMutation(
            beforeUri = operation.sourceUri,
            beforeParentUri = operation.sourceParentUri,
            beforeName = operation.originalName,
            afterUri = afterUri.toString(),
            afterParentUri = targetParent.toString(),
            afterName = operation.newName,
            createdDirectoryUris = emptyList(),
            operation = operation,
        )
    }

    override fun undo(mutation: DocumentMutation) {
        val current = Uri.parse(mutation.afterUri)
        val currentParent = Uri.parse(mutation.afterParentUri)
        val originalParent = Uri.parse(mutation.beforeParentUri)
        check(documentExists(mutation.afterUri)) { "The organized file no longer exists." }
        check(findChildrenByName(originalParent, mutation.beforeName).isEmpty()) {
            "The original path is now occupied."
        }

        if (currentParent == originalParent) {
            rename(current, mutation.beforeName)
        } else if (mutation.afterName == mutation.beforeName) {
            moveOrCopy(current, currentParent, originalParent, mutation.beforeName)
        } else {
            copyThenDelete(current, originalParent, mutation.beforeName)
        }
    }

    override fun deleteDirectoryIfEmpty(uri: String): Boolean {
        val directory = Uri.parse(uri)
        val record = queryDocument(directory) ?: return true
        if (!record.isDirectory || children(uri).isNotEmpty()) return false
        return DocumentsContract.deleteDocument(resolver, directory)
    }

    private fun moveOrCopy(source: Uri, sourceParent: Uri, targetParent: Uri, targetName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent)?.let { return it }
            } catch (_: FileNotFoundException) {
                // Fall back when the provider advertises move support but cannot resolve the document.
            } catch (_: UnsupportedOperationException) {
                // Fall back for providers that do not implement native move.
            } catch (_: IllegalArgumentException) {
                // Fall back for cross-provider or otherwise unsupported move combinations.
            }
        }
        return copyThenDelete(source, targetParent, targetName)
    }

    private fun copyThenDelete(source: Uri, targetParent: Uri, targetName: String): Uri {
        val sourceRecord = requireNotNull(queryDocument(source)) { "The source file no longer exists." }
        require(!sourceRecord.isDirectory) { "Only file operations are supported." }
        val target = requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                targetParent,
                sourceRecord.mimeType.ifBlank { "application/octet-stream" },
                targetName,
            ),
        ) { "Could not create $targetName." }

        var completed = false
        try {
            resolver.openInputStream(source).use { input ->
                resolver.openOutputStream(target, "w").use { output ->
                    requireNotNull(input) { "Could not read ${sourceRecord.name}." }
                    requireNotNull(output) { "Could not write $targetName." }
                    input.copyTo(output)
                }
            }
            if (!DocumentsContract.deleteDocument(resolver, source)) {
                DocumentsContract.deleteDocument(resolver, target)
                throw IOException("The provider copied the file but would not remove the original.")
            }
            completed = true
        } finally {
            if (!completed) runCatching { DocumentsContract.deleteDocument(resolver, target) }
        }
        return target
    }

    private fun rename(uri: Uri, name: String): Uri =
        requireNotNull(DocumentsContract.renameDocument(resolver, uri, name)) {
            "The document provider declined the rename."
        }

    private fun resolvePath(root: Uri, relativePath: String): Uri? {
        var current = root
        val segments = relativePath.split('/').filter(String::isNotBlank)
        segments.forEachIndexed { index, segment ->
            val matches = findChildrenByName(current, segment)
            if (matches.isEmpty()) return null
            if (matches.size > 1) return matches.first().uri
            val match = matches.single()
            if (index < segments.lastIndex && !match.isDirectory) return null
            current = match.uri
        }
        return current
    }

    private fun findChildrenByName(parent: Uri, name: String): List<DocumentRecord> =
        children(parent.toString())
            .filter { it.name.equals(name, ignoreCase = true) }
            .mapNotNull { entry -> queryDocument(Uri.parse(entry.uri)) }

    private fun treeDocumentUri(treeUri: String): Uri {
        val tree = Uri.parse(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun queryDocument(uri: Uri): DocumentRecord? =
        resolver.query(uri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toDocumentRecord(uri) else null
        }

    private fun Cursor.toDocumentRecord(uri: Uri): DocumentRecord {
        val id = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        return DocumentRecord(
            documentId = id,
            uri = uri,
            name = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) ?: "Untitled",
            mimeType = getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)).orEmpty(),
            size = getLong(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)),
        )
    }

    private fun DocumentRecord.toEntry(uri: Uri, parentUri: String) = DocumentEntry(
        uri = uri.toString(),
        parentUri = parentUri,
        name = name,
        isDirectory = isDirectory,
        size = size,
    )

    private data class DocumentRecord(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private companion object {
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
