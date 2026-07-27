/** mobile/app/src/main/java/com/kp/filemanager/domain/DirectoryScanner.kt: Performs cancellable, bounded, read-only recursive document discovery; created for the media refactor. */
package com.kp.filemanager.domain

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DocumentEntry(
    val uri: String,
    val parentUri: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

interface DocumentTreeSource {
    fun root(treeUri: String): DocumentEntry
    fun children(directoryUri: String): List<DocumentEntry>
}

class DirectoryScanner(
    private val source: DocumentTreeSource,
) {
    suspend fun scan(
        treeUri: String,
        maximumDepth: Int = DEFAULT_MAXIMUM_DEPTH,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult {
        require(maximumDepth >= 0) { "Maximum scan depth cannot be negative." }
        val root = source.root(treeUri)
        require(root.isDirectory) { "The selected item is not a folder." }

        val files = mutableListOf<ScannedFile>()
        val warnings = mutableListOf<ScanWarning>()
        val visitedDirectories = mutableSetOf<String>()
        val queue = ArrayDeque<PendingDirectory>()
        queue.add(PendingDirectory(root.uri, "", 0))
        var ignoredDirectories = 0
        var movieCount = 0
        var episodeCount = 0
        var subtitleCount = 0
        val parser = MediaNameParser()

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = queue.removeFirst()
            if (!visitedDirectories.add(directory.uri)) continue

            val children = try {
                source.children(directory.uri)
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

            children.sortedBy { it.name.lowercase() }.forEach { child ->
                currentCoroutineContext().ensureActive()
                val childPath = listOf(directory.path, child.name).filter(String::isNotBlank).joinToString("/")
                if (child.isDirectory) {
                    if (directory.depth < maximumDepth) {
                        queue.add(PendingDirectory(child.uri, childPath, directory.depth + 1))
                    } else {
                        ignoredDirectories += 1
                    }
                } else {
                    val scannedFile = ScannedFile.from(
                        sourceUri = child.uri,
                        parentUri = directory.uri,
                        parentPath = directory.path,
                        fileName = child.name,
                        depth = directory.depth,
                        size = child.size,
                    )
                    files += scannedFile
                    when (scannedFile.fileType) {
                        MediaFileType.VIDEO -> {
                            val parsed = parser.parse(scannedFile)
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
                            filesScanned = files.size,
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
            files = files,
            warnings = warnings,
            ignoredDirectoryCount = ignoredDirectories,
        )
    }

    private data class PendingDirectory(
        val uri: String,
        val path: String,
        val depth: Int,
    )

    companion object {
        const val DEFAULT_MAXIMUM_DEPTH = 4
    }
}
