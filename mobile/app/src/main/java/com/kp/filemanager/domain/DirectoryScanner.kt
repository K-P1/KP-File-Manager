/** mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt: Indexes document trees and defines parent-aware empty-directory cleanup access; last changed for exhaustive ancestor cleanup. */
package com.kp.filemanager.domain

import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DocumentEntry(
    val uri: String,
    val parentUri: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val documentId: String = "",
    val mimeType: String = "",
    val lastModified: Long = 0,
    val flags: Int = 0,
)

interface DocumentTreeSource {
    fun root(treeUri: String): DocumentEntry
    fun children(directoryUri: String): List<DocumentEntry>
}

fun interface DirectoryCleanupGateway {
    fun deleteDirectoryIfEmpty(uri: String, parentUri: String?): Boolean
}

class DirectoryScanner(
    private val source: DocumentTreeSource,
    private val parser: MediaMetadataParser = MediaNameParser(),
) {
    suspend fun scan(
        treeUri: String,
        maximumDepth: Int = DEFAULT_MAXIMUM_DEPTH,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        require(maximumDepth >= 0) { "Maximum scan depth cannot be negative." }
        val root = source.root(treeUri)
        require(root.isDirectory) { "The selected item is not a folder." }

        val indexedFiles = mutableListOf<IndexedMediaFile>()
        val indexedDirectories = mutableListOf<IndexedDirectory>()
        val warnings = mutableListOf<ScanWarning>()
        val visitedDirectories = mutableSetOf<String>()
        val queue = ArrayDeque<PendingDirectory>()
        queue.add(PendingDirectory(root, "", 0))
        var ignoredDirectories = 0
        var movieCount = 0
        var episodeCount = 0
        var subtitleCount = 0

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = queue.removeFirst()
            if (!visitedDirectories.add(directory.entry.uri)) continue

            val children = try {
                source.children(directory.entry.uri)
            } catch (error: SecurityException) {
                warnings += ScanWarning(directory.path, "Folder access was denied.")
                continue
            } catch (error: IOException) {
                warnings += ScanWarning(directory.path, "Folder contents could not be read.")
                continue
            } catch (error: IllegalStateException) {
                warnings += ScanWarning(directory.path, error.message ?: "Folder contents could not be read.")
                continue
            }

            children.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { child ->
                currentCoroutineContext().ensureActive()
                val childPath = listOf(directory.path, child.name)
                    .filter(String::isNotBlank)
                    .joinToString("/")
                if (child.isDirectory) {
                    if (directory.depth < maximumDepth) {
                        val depth = directory.depth + 1
                        indexedDirectories += IndexedDirectory(
                            uri = child.uri,
                            parentUri = directory.entry.uri,
                            path = childPath,
                            name = child.name,
                            depth = depth,
                            documentId = child.documentId,
                            lastModified = child.lastModified,
                            flags = child.flags,
                        )
                        queue.add(PendingDirectory(child, childPath, depth))
                    } else {
                        ignoredDirectories += 1
                    }
                } else {
                    val scannedFile = ScannedFile.from(
                        sourceUri = child.uri,
                        parentUri = directory.entry.uri,
                        parentPath = directory.path,
                        fileName = child.name,
                        depth = directory.depth,
                        size = child.size,
                        mimeType = child.mimeType,
                        lastModified = child.lastModified,
                        flags = child.flags,
                        documentId = child.documentId,
                    )
                    val parsed = parser.parse(scannedFile)
                    indexedFiles += IndexedMediaFile(scannedFile, parsed)
                    when (scannedFile.fileType) {
                        MediaFileType.VIDEO -> {
                            if (parsed.season != null && parsed.episode != null) {
                                episodeCount += 1
                            } else {
                                movieCount += 1
                            }
                        }
                        MediaFileType.SUBTITLE -> subtitleCount += 1
                        else -> Unit
                    }
                    onProgress(
                        ScanProgress(
                            filesScanned = indexedFiles.size,
                            directoriesScanned = visitedDirectories.size,
                            moviesDetected = movieCount,
                            episodesDetected = episodeCount,
                            subtitlesDetected = subtitleCount,
                            currentPath = childPath,
                        ),
                    )
                }
            }
        }

        return ScanResult(
            index = MediaIndex(
                root = root,
                directories = indexedDirectories,
                items = indexedFiles,
                treeUri = treeUri,
            ),
            warnings = warnings,
            ignoredDirectoryCount = ignoredDirectories,
        )
    }

    private data class PendingDirectory(
        val entry: DocumentEntry,
        val path: String,
        val depth: Int,
    )

    companion object {
        const val DEFAULT_MAXIMUM_DEPTH = 4
    }
}
