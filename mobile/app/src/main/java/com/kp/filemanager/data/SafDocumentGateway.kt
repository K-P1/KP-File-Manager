/** mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt: Implements permission-free folder access, metadata, moves, and undo through Android SAF; last updated for safe, single-pass batch renames and interrupted-rename recovery. */
package com.kp.filemanager.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.kp.filemanager.domain.AudioMetadata
import com.kp.filemanager.domain.FileCandidate
import com.kp.filemanager.domain.PlannedChange
import java.io.FileNotFoundException
import java.io.IOException

class SafDocumentGateway(context: Context) : DocumentGateway {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    override fun displayName(treeUri: String): String = document(treeUri).name ?: "Selected folder"

    override fun listCandidates(treeUri: String, includeAudioMetadata: Boolean): List<FileCandidate> {
        val root = document(treeUri)
        check(root.isDirectory) { "The selected item is not a folder." }
        return root.listFiles().mapNotNull { child ->
            val name = child.name ?: return@mapNotNull null
            FileCandidate(
                uri = child.uri.toString(),
                parentUri = root.uri.toString(),
                name = name,
                isDirectory = child.isDirectory,
                descendantExtensions = if (child.isDirectory) collectExtensions(child) else emptyList(),
                metadata = if (includeAudioMetadata && name.endsWith(".mp3", ignoreCase = true)) {
                    readAudioMetadata(child.uri)
                } else {
                    AudioMetadata()
                },
                recoveryExtension = temporaryRenameExtension(name),
            )
        }
    }

    override fun ensureDirectory(treeUri: String, name: String): String {
        val root = document(treeUri)
        val existing = root.findFile(name)
        if (existing != null) {
            check(existing.isDirectory) { "$name already exists as a file." }
            return existing.uri.toString()
        }
        return checkNotNull(root.createDirectory(name)) { "Could not create $name." }.uri.toString()
    }

    override fun renameBatch(changes: List<PlannedChange>): Pair<List<DocumentMutation>, List<String>> {
        if (changes.isEmpty()) return emptyList<DocumentMutation>() to emptyList()
        val completed = mutableListOf<DocumentMutation>()
        val failures = mutableListOf<String>()
        val parent = document(changes.first().sourceParentUri)
        val targetNames = reserveTargetNames(parent, changes)

        changes.zip(targetNames).forEach { (change, targetName) ->
            try {
                val currentUri = rename(Uri.parse(change.sourceUri), targetName)
                completed += DocumentMutation(
                    currentUri = currentUri.toString(),
                    currentParentUri = change.sourceParentUri,
                    originalParentUri = change.sourceParentUri,
                    originalName = change.sourceName,
                )
            } catch (error: Exception) {
                failures += "${change.sourceName}: ${error.message ?: "Could not rename"}"
            }
        }
        return completed to failures
    }

    override fun move(change: PlannedChange, targetParentUri: String): DocumentMutation {
        val sourceUri = Uri.parse(change.sourceUri)
        val sourceParentUri = Uri.parse(change.sourceParentUri)
        val targetParent = document(targetParentUri)
        val targetName = uniqueName(targetParent, change.targetName)
        val movedUri = tryNativeMove(sourceUri, sourceParentUri, targetParent.uri, targetName)
            ?: copyThenDelete(document(change.sourceUri), targetParent, targetName)
        return DocumentMutation(
            currentUri = movedUri.toString(),
            currentParentUri = targetParent.uri.toString(),
            originalParentUri = change.sourceParentUri,
            originalName = change.sourceName,
        )
    }

    override fun undo(mutation: DocumentMutation): DocumentMutation {
        val current = document(mutation.currentUri)
        if (mutation.currentParentUri == mutation.originalParentUri && current.name == mutation.originalName) {
            return mutation
        }
        val originalParent = document(mutation.originalParentUri)
        val restoredName = uniqueName(originalParent, mutation.originalName)
        val movedUri = if (mutation.currentParentUri == mutation.originalParentUri) {
            rename(current.uri, restoredName)
        } else {
            tryNativeMove(current.uri, Uri.parse(mutation.currentParentUri), originalParent.uri, restoredName)
                ?: copyThenDelete(current, originalParent, restoredName)
        }
        return DocumentMutation(
            currentUri = movedUri.toString(),
            currentParentUri = originalParent.uri.toString(),
            originalParentUri = mutation.currentParentUri,
            originalName = current.name ?: mutation.originalName,
        )
    }

    private fun tryNativeMove(source: Uri, sourceParent: Uri, targetParent: Uri, targetName: String): Uri? {
        val originalName = document(source.toString()).name
        if (originalName != targetName || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            nativeMove(source, sourceParent, targetParent)
        } catch (error: Exception) {
            when (error) {
                is FileNotFoundException, is UnsupportedOperationException, is IllegalArgumentException -> null
                else -> throw error
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun nativeMove(source: Uri, sourceParent: Uri, targetParent: Uri): Uri? =
        DocumentsContract.moveDocument(resolver, source, sourceParent, targetParent)

    private fun copyThenDelete(source: DocumentFile, targetParent: DocumentFile, targetName: String): Uri {
        val copied = copyDocument(source, targetParent, targetName)
        if (!source.delete()) {
            copied.delete()
            throw IOException("The provider copied the item but would not remove the original.")
        }
        return copied.uri
    }

    private fun copyDocument(source: DocumentFile, targetParent: DocumentFile, targetName: String): DocumentFile {
        if (source.isDirectory) {
            val targetDirectory = checkNotNull(targetParent.createDirectory(targetName)) { "Could not create $targetName." }
            source.listFiles().forEach { child ->
                copyDocument(child, targetDirectory, child.name ?: "Untitled")
            }
            return targetDirectory
        }

        val target = checkNotNull(targetParent.createFile(source.type ?: "application/octet-stream", targetName)) {
            "Could not create $targetName."
        }
        resolver.openInputStream(source.uri).use { input ->
            resolver.openOutputStream(target.uri, "w").use { output ->
                checkNotNull(input) { "Could not read ${source.name}." }
                checkNotNull(output) { "Could not write $targetName." }
                input.copyTo(output)
            }
        }
        return target
    }

    private fun collectExtensions(directory: DocumentFile): List<String> {
        val extensions = mutableListOf<String>()
        val queue = ArrayDeque<DocumentFile>()
        queue.add(directory)
        while (queue.isNotEmpty() && extensions.size <= 50) {
            queue.removeFirst().listFiles().forEach { child ->
                if (child.isDirectory) queue.add(child)
                else child.name?.substringAfterLast('.', "")?.takeIf(String::isNotEmpty)?.let(extensions::add)
            }
        }
        return extensions
    }

    private fun readAudioMetadata(uri: Uri): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { descriptor -> retriever.setDataSource(descriptor.fileDescriptor) }
            AudioMetadata(
                track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            )
        } catch (_: RuntimeException) {
            AudioMetadata()
        } finally {
            retriever.release()
        }
    }

    private fun uniqueName(parent: DocumentFile, requestedName: String): String {
        if (parent.findFile(requestedName) == null) return requestedName
        val stem = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var counter = 1
        var candidate: String
        do {
            candidate = "${stem}_$counter$extension"
            counter += 1
        } while (parent.findFile(candidate) != null)
        return candidate
    }

    private fun reserveTargetNames(parent: DocumentFile, changes: List<PlannedChange>): List<String> {
        val occupied = parent.listFiles().mapNotNull { it.name }.toMutableSet()
        return changes.map { change ->
            occupied.remove(change.sourceName)
            val target = reserveUniqueName(occupied, change.targetName)
            occupied += target
            target
        }
    }

    private fun reserveUniqueName(occupied: Set<String>, requestedName: String): String {
        if (requestedName !in occupied) return requestedName
        val stem = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var counter = 1
        var candidate: String
        do {
            candidate = "${stem}_$counter$extension"
            counter += 1
        } while (candidate in occupied)
        return candidate
    }

    private fun temporaryRenameExtension(name: String): String? = TEMPORARY_RENAME_PATTERN
        .matchEntire(name)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    private fun rename(uri: Uri, name: String): Uri =
        checkNotNull(DocumentsContract.renameDocument(resolver, uri, name)) { "The provider declined the rename." }

    private fun document(uri: String): DocumentFile =
        checkNotNull(DocumentFile.fromTreeUri(appContext, Uri.parse(uri))) { "The selected folder is no longer available." }

    private companion object {
        val TEMPORARY_RENAME_PATTERN = Regex("^\\.kp_[0-9a-fA-F-]{36}_(.+)$")
    }
}
