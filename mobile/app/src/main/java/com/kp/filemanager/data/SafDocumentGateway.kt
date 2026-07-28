/** mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt: Implements cached SAF discovery and safe mutations; last changed to refresh parent listings during exhaustive empty-folder cleanup. */
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
import com.kp.filemanager.domain.ProviderOperationCounters
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

class SafDocumentGateway(context: Context) : DocumentGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val childCache = mutableMapOf<String, List<DocumentRecord>>()
    private val directoryPathCache = mutableMapOf<String, Uri>()
    private val movePolicy = ProviderMovePolicy()
    private val verifiedCopyDelete = VerifiedCopyDelete(ResolverTransferBackend())
    private var counters = ProviderOperationCounters()

    override fun displayName(treeUri: String): String =
        queryDocument(treeDocumentUri(treeUri))?.name ?: "Selected folder"

    override fun root(treeUri: String): DocumentEntry {
        val uri = treeDocumentUri(treeUri)
        val document = requireNotNull(queryDocument(uri)) { "The selected folder is no longer available." }
        require(document.isDirectory) { "The selected item is not a folder." }
        directoryPathCache[pathCacheKey(uri, "")] = uri
        return document.toEntry(uri, "")
    }

    override fun children(directoryUri: String): List<DocumentEntry> {
        val parent = Uri.parse(directoryUri)
        return childrenRecords(parent).map { it.toEntry(it.uri, directoryUri) }
    }

    override fun documentExists(uri: String): Boolean = try {
        queryDocument(Uri.parse(uri)) != null
    } catch (_: FileNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    override fun documentExistsInParent(uri: String, parentUri: String): Boolean =
        childrenRecords(Uri.parse(parentUri)).any { it.uri.toString() == uri }

    override fun destinationExists(treeUri: String, relativePath: String, excludingUri: String?): Boolean {
        val destination = resolvePath(treeDocumentUri(treeUri), relativePath) ?: return false
        return excludingUri == null || destination.toString() != excludingUri
    }

    override fun ensureDirectoryPath(treeUri: String, relativePath: String): PreparedDirectory {
        val treeRoot = treeDocumentUri(treeUri)
        var current = treeRoot
        var currentPath = ""
        val created = mutableListOf<String>()
        relativePath.split('/').filter(String::isNotBlank).forEach { segment ->
            currentPath = listOf(currentPath, segment).filter(String::isNotBlank).joinToString("/")
            val cacheKey = pathCacheKey(treeRoot, currentPath)
            val cached = directoryPathCache[cacheKey]
            current = if (cached != null && documentExists(cached.toString())) {
                cached
            } else {
                val matches = findChildrenByName(current, segment)
                require(matches.size <= 1) { "Multiple folders match '$segment' on this provider." }
                val existing = matches.singleOrNull()
                when {
                    existing == null -> {
                        val createdUri = requireNotNull(
                            DocumentsContract.createDocument(
                                resolver,
                                current,
                                DocumentsContract.Document.MIME_TYPE_DIR,
                                segment,
                            ),
                        ) { "Could not create $segment." }
                        invalidateChildren(current)
                        created += createdUri.toString()
                        createdUri
                    }
                    existing.isDirectory -> existing.uri
                    else -> error("$segment already exists as a file.")
                }.also { directoryPathCache[cacheKey] = it }
            }
        }
        return PreparedDirectory(current.toString(), created)
    }

    override fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation {
        val source = Uri.parse(operation.sourceUri)
        val sourceParent = Uri.parse(operation.sourceParentUri)
        val targetParent = Uri.parse(targetParentUri)
        invalidateChildren(targetParent)
        check(findChildrenByName(targetParent, operation.newName).isEmpty()) {
            "A destination file with this name already exists."
        }
        val afterUri = when (operation.operationType) {
            OperationType.RENAME -> rename(source, operation.newName, operation.originalName)
            OperationType.MOVE,
            OperationType.MOVE_AND_RENAME,
            -> moveRenameOrCopy(
                source = source,
                sourceParent = sourceParent,
                targetParent = targetParent,
                originalName = operation.originalName,
                targetName = operation.newName,
            )
            OperationType.SKIP -> error("Skipped operations cannot be applied.")
        }
        invalidateChildren(sourceParent)
        invalidateChildren(targetParent)
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
        var recreatedParent: PreparedDirectory? = null
        val originalParent = if (documentExists(mutation.beforeParentUri)) {
            Uri.parse(mutation.beforeParentUri)
        } else {
            require(mutation.beforeTreeUri.isNotBlank()) {
                "The original folder no longer exists and cannot be recreated."
            }
            recreatedParent = ensureDirectoryPath(
                treeUri = mutation.beforeTreeUri,
                relativePath = mutation.beforeParentPath,
            )
            Uri.parse(recreatedParent.uri)
        }

        try {
            check(documentExists(mutation.afterUri)) { "The organized file no longer exists." }
            check(findChildrenByName(originalParent, mutation.beforeName).isEmpty()) {
                "The original path is now occupied."
            }

            if (currentParent == originalParent) {
                rename(current, mutation.beforeName, mutation.afterName)
            } else {
                moveRenameOrCopy(
                    source = current,
                    sourceParent = currentParent,
                    targetParent = originalParent,
                    originalName = mutation.afterName,
                    targetName = mutation.beforeName,
                )
            }
            invalidateChildren(currentParent)
            invalidateChildren(originalParent)
        } catch (error: Exception) {
            recreatedParent?.createdDirectoryUris
                ?.asReversed()
                ?.forEach { deleteDirectoryIfEmpty(it, null) }
            throw error
        }
    }

    override fun deleteDirectoryIfEmpty(uri: String, parentUri: String?): Boolean {
        val directory = Uri.parse(uri)
        val record = queryDocument(directory)
        if (record == null) {
            invalidateDeletedDirectory(directory, parentUri)
            return true
        }
        if (!record.isDirectory) return false
        invalidateChildren(directory)
        if (childrenRecords(directory).isNotEmpty()) return false
        val deleted = DocumentsContract.deleteDocument(resolver, directory)
        if (!deleted) {
            throw IOException("The document provider declined to delete an empty directory.")
        }
        invalidateDeletedDirectory(directory, parentUri)
        return true
    }

    private fun invalidateDeletedDirectory(directory: Uri, parentUri: String?) {
        childCache.remove(directory.toString())
        if (parentUri == null) {
            childCache.clear()
        } else {
            invalidateChildren(Uri.parse(parentUri))
        }
        directoryPathCache.entries.removeAll { it.value == directory }
    }

    override fun operationCounters(): ProviderOperationCounters = counters

    override fun resetOperationCounters() {
        counters = ProviderOperationCounters()
    }

    private fun moveRenameOrCopy(
        source: Uri,
        sourceParent: Uri,
        targetParent: Uri,
        originalName: String,
        targetName: String,
    ): Uri {
        val canMove = movePolicy.shouldAttemptDirectMove(
            sourceUri = source.toString(),
            targetParentUri = targetParent.toString(),
            apiLevel = Build.VERSION.SDK_INT,
        )
        if (canMove) {
            val moved = tryDirectMove(source, sourceParent, targetParent)
            if (moved != null) {
                if (originalName == targetName) return moved
                return try {
                    rename(moved, targetName, originalName)
                } catch (error: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        runCatching {
                            DocumentsContract.moveDocument(resolver, moved, targetParent, sourceParent)
                        }
                    }
                    throw error
                }
            }
        }
        counters = counters.copy(copyDeleteFallbacks = counters.copyDeleteFallbacks + 1)
        return Uri.parse(
            verifiedCopyDelete.transfer(
                sourceUri = source.toString(),
                targetParentUri = targetParent.toString(),
                targetName = targetName,
            ),
        )
    }

    private fun tryDirectMove(source: Uri, sourceParent: Uri, targetParent: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            val moved = DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent)
            if (moved == null) movePolicy.recordUnsupported(source.authority)
            moved?.also {
                counters = counters.copy(moves = counters.moves + 1)
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: UnsupportedOperationException) {
            movePolicy.recordUnsupported(source.authority)
            null
        } catch (_: IllegalArgumentException) {
            movePolicy.recordUnsupported(source.authority)
            null
        }
    }

    private fun rename(uri: Uri, name: String, rollbackName: String): Uri {
        val renamed = requireNotNull(DocumentsContract.renameDocument(resolver, uri, name)) {
            "The document provider declined the rename."
        }
        val actualName = queryDocument(renamed)?.name
        if (actualName != name) {
            runCatching { DocumentsContract.renameDocument(resolver, renamed, rollbackName) }
            error("The document provider changed the requested destination name.")
        }
        counters = counters.copy(renames = counters.renames + 1)
        return renamed
    }

    private fun resolvePath(root: Uri, relativePath: String): Uri? {
        val normalizedPath = relativePath.replace('\\', '/').trim('/')
        directoryPathCache[pathCacheKey(root, normalizedPath)]?.let { return it }
        var current = root
        var traversed = ""
        val segments = normalizedPath.split('/').filter(String::isNotBlank)
        segments.forEachIndexed { index, segment ->
            val matches = findChildrenByName(current, segment)
            if (matches.isEmpty()) return null
            val match = matches.first()
            if (index < segments.lastIndex && !match.isDirectory) return null
            current = match.uri
            traversed = listOf(traversed, segment).filter(String::isNotBlank).joinToString("/")
            if (match.isDirectory) directoryPathCache[pathCacheKey(root, traversed)] = current
        }
        return current
    }

    private fun findChildrenByName(parent: Uri, name: String): List<DocumentRecord> =
        childrenRecords(parent).filter { it.name.equals(name, ignoreCase = true) }

    private fun childrenRecords(parent: Uri): List<DocumentRecord> =
        childCache.getOrPut(parent.toString()) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parent,
                DocumentsContract.getDocumentId(parent),
            )
            query(childrenUri).map { record ->
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, record.documentId)
                record.copy(uri = uri)
            }
        }

    private fun queryDocument(uri: Uri): DocumentRecord? = query(uri).firstOrNull()

    private fun query(uri: Uri): List<DocumentRecord> {
        counters = counters.copy(queries = counters.queries + 1)
        return resolver.query(uri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toDocumentRecord(uri))
            }
        }.orEmpty()
    }

    private fun treeDocumentUri(treeUri: String): Uri {
        val tree = Uri.parse(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun Cursor.toDocumentRecord(uri: Uri): DocumentRecord {
        val id = stringColumn(DocumentsContract.Document.COLUMN_DOCUMENT_ID).orEmpty()
        return DocumentRecord(
            documentId = id,
            uri = uri,
            name = stringColumn(DocumentsContract.Document.COLUMN_DISPLAY_NAME) ?: "Untitled",
            mimeType = stringColumn(DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty(),
            size = longColumn(DocumentsContract.Document.COLUMN_SIZE) ?: -1,
            lastModified = longColumn(DocumentsContract.Document.COLUMN_LAST_MODIFIED) ?: 0,
            flags = longColumn(DocumentsContract.Document.COLUMN_FLAGS)?.toInt() ?: 0,
        )
    }

    private fun Cursor.stringColumn(name: String): String? =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.longColumn(name: String): Long? =
        getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private fun DocumentRecord.toEntry(uri: Uri, parentUri: String) = DocumentEntry(
        uri = uri.toString(),
        parentUri = parentUri,
        name = name,
        isDirectory = isDirectory,
        size = size,
        documentId = documentId,
        mimeType = mimeType,
        lastModified = lastModified,
        flags = flags,
    )

    private fun invalidateChildren(parent: Uri) {
        childCache.remove(parent.toString())
    }

    private fun pathCacheKey(root: Uri, path: String): String =
        "${root}|${path.replace('\\', '/').trim('/').lowercase(Locale.ROOT)}"

    private inner class ResolverTransferBackend : DocumentTransferBackend {
        override fun metadata(uri: String): TransferMetadata {
            val record = requireNotNull(queryDocument(Uri.parse(uri))) { "The source file no longer exists." }
            require(!record.isDirectory) { "Only file operations are supported." }
            return TransferMetadata(record.name, record.mimeType, record.size.takeIf { it >= 0 })
        }

        override fun createFinalDocument(parentUri: String, mimeType: String, name: String): String {
            val created = requireNotNull(
                DocumentsContract.createDocument(resolver, Uri.parse(parentUri), mimeType, name),
            ) { "Could not create $name." }
            if (queryDocument(created)?.name != name) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                error("The exact destination name is no longer available.")
            }
            return created.toString()
        }

        override fun copy(sourceUri: String, targetUri: String) {
            resolver.openInputStream(Uri.parse(sourceUri)).use { input ->
                resolver.openOutputStream(Uri.parse(targetUri), "w").use { output ->
                    requireNotNull(input) { "Could not read the source file." }
                    requireNotNull(output) { "Could not write the destination file." }
                    input.copyTo(output)
                }
            }
        }

        override fun exists(uri: String): Boolean = documentExists(uri)

        override fun size(uri: String): Long? =
            queryDocument(Uri.parse(uri))?.size?.takeIf { it >= 0 }

        override fun delete(uri: String): Boolean =
            DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
    }

    private data class DocumentRecord(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val flags: Int,
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
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}
