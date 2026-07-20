/** mobile/app/src/main/java/com/kp/filemanager/data/SafDocumentGateway.kt: Implements permission-free folder access, metadata, moves, and undo through Android SAF; created for mobile parity. */
package com.kp.filemanager.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kp.filemanager.domain.AudioMetadata
import com.kp.filemanager.domain.FileCandidate
import com.kp.filemanager.domain.PlannedChange
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

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
        val staged = mutableListOf<Pair<PlannedChange, Uri>>()
        val completed = mutableListOf<DocumentMutation>()
        val failures = mutableListOf<String>()

        changes.forEach { change ->
            try {
                val temporaryName = ".kp_${UUID.randomUUID()}_${change.sourceName.substringAfterLast('.', "tmp")}" 
                val temporaryUri = rename(Uri.parse(change.sourceUri), temporaryName)
                staged += change to temporaryUri
            } catch (error: Exception) {
                failures += "${change.sourceName}: ${error.message ?: "Could not prepare rename"}"
            }
        }

        staged.forEach { (change, temporaryUri) ->
            try {
                val parent = document(change.sourceParentUri)
                val targetName = uniqueName(parent, change.targetName)
                val currentUri = rename(temporaryUri, targetName)
                completed += DocumentMutation(
                    currentUri = currentUri.toString(),
                    currentParentUri = change.sourceParentUri,
                    originalParentUri = change.sourceParentUri,
                    originalName = change.sourceName,
                )
            } catch (error: Exception) {
                runCatching { rename(temporaryUri, change.sourceName) }
                failures += "${change.sourceName}: ${error.message ?: "Could not finish rename"}"
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
        val renamedSource = if (originalName != targetName) rename(source, targetName) else source
        return try {
            DocumentsContract.moveDocument(resolver, renamedSource, sourceParent, targetParent)
        } catch (error: Exception) {
            if (renamedSource != source && originalName != null) runCatching { rename(renamedSource, originalName) }
            when (error) {
                is FileNotFoundException, is UnsupportedOperationException, is IllegalArgumentException -> null
                else -> throw error
            }
        }
    }

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

    private fun rename(uri: Uri, name: String): Uri =
        checkNotNull(DocumentsContract.renameDocument(resolver, uri, name)) { "The provider declined the rename." }

    private fun document(uri: String): DocumentFile =
        checkNotNull(DocumentFile.fromTreeUri(appContext, Uri.parse(uri))) { "The selected folder is no longer available." }
}
