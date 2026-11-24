/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportModels.kt
 * Purpose: Strict source/destination rules and selection-ready KP organizer plans for protected media.
 * Last changed: 2026-11-23, introduce protected media exporting.
 */
package com.kp.filemanager.export

import com.kp.filemanager.domain.Confidence
import com.kp.filemanager.domain.DocumentEntry
import com.kp.filemanager.domain.IndexedMediaFile
import com.kp.filemanager.domain.LibraryContext
import com.kp.filemanager.domain.LibraryContextType
import com.kp.filemanager.domain.MediaFileType
import com.kp.filemanager.domain.MediaItem
import com.kp.filemanager.domain.MediaNameParser
import com.kp.filemanager.domain.MediaOrganizationPlanner
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.domain.ScannedFile
import com.kp.filemanager.domain.MediaIndex
import java.util.Locale

object ProtectedExportPaths {
    const val VIDEO_SOURCE = "/storage/emulated/0/Android/data/com.community.oneroom/files/Download/d"
    const val SUBTITLE_SOURCE = "/storage/emulated/0/Android/data/com.community.oneroom/files/Download/subtitle"
    const val LIBRARY_ROOT = "/storage/emulated/0"
    const val LIBRARY_ID = "shizuku://public-media-library"
    private val safeName = Regex("^[^/\\\\\\u0000-\\u001f]+$")

    fun inProtectedSource(path: String): Boolean =
        (path.startsWith("$VIDEO_SOURCE/") || path.startsWith("$SUBTITLE_SOURCE/")) &&
            path != VIDEO_SOURCE && path != SUBTITLE_SOURCE &&
            path.split('/').none { it == "." || it == ".." }

    fun destination(relativePath: String): String {
        val segments = relativePath.split('/')
        require(segments.size >= 3 && segments.all {
            it.isNotBlank() && it != "." && it != ".." && safeName.matches(it)
        }) { "Unsafe destination path" }
        require(segments.first() == "Movies" || segments.first() == "TV Shows") {
            "Files can only be exported into Movies or TV Shows"
        }
        return "$LIBRARY_ROOT/$relativePath"
    }

    fun stagingPath(destination: String, token: String): String {
        require(token.matches(Regex("[a-f0-9-]{36}")))
        require(destination.startsWith("$LIBRARY_ROOT/"))
        return "${destination.substringBeforeLast('/')}/.kp-export-$token.partial"
    }
}

data class ExportBundle(
    val id: String,
    val label: String,
    val detail: String,
    val operations: List<PlannedFileOperation>,
    val warnings: List<String>,
    val totalBytes: Long,
) {
    val blocked: Boolean get() = operations.isEmpty() || operations.any { it.status != OperationStatus.READY }
    val subtitleCount: Int get() = operations.count { it.mediaType == MediaFileType.SUBTITLE }
}

data class ExportPreview(
    val plan: OperationPlan,
    val bundles: List<ExportBundle>,
    val unmatched: List<PlannedFileOperation>,
    val sizes: Map<String, Long>,
    val sourceCount: Int,
) {
    val eligible: List<ExportBundle> get() = bundles.filterNot(ExportBundle::blocked)
}

/**
 * Uses KP's actual parser, companion grouper, path formatter, and conflict detector.
 * No separate MovieBox renaming or matching logic exists in this feature.
 */
class ProtectedExportPlanner(
    private val parser: MediaNameParser = MediaNameParser(),
    private val planner: MediaOrganizationPlanner = MediaOrganizationPlanner(),
) {
    fun build(files: List<ScannedFile>, existingPaths: Set<String> = emptySet()): ExportPreview {
        val root = DocumentEntry("shizuku://protected-sources", "", "Protected downloads", true, 0)
        val index = MediaIndex(
            root = root,
            directories = emptyList(),
            items = files.map { IndexedMediaFile(it, parser.parse(it)) },
        )
        val plan = planner.plan(
            index = index,
            destinationTreeUri = ProtectedExportPaths.LIBRARY_ID,
            existingDestinationPaths = existingPaths,
            sourceAndDestinationAreSame = false,
            libraryContext = LibraryContext(LibraryContextType.GENERIC_SOURCE, "Protected downloads"),
            sourceTreeUri = root.uri,
        )
        val operations = plan.operations.associateBy { it.sourceUri }
        val sizes = files.associate { it.sourceUri to it.size }
        val bundles = plan.groups.mapNotNull { group ->
            val video = group.videos.firstOrNull() ?: return@mapNotNull null
            val matchingOperations = group.files.mapNotNull { operations[it.file.sourceUri] }
            val item = group.mediaItem
            val label = when (item) {
                is MediaItem.Movie -> item.title + (item.year?.let { " ($it)" } ?: "")
                is MediaItem.TvEpisode -> "${item.title} · S${item.season.toString().padStart(2, '0')}E${item.episode.toString().padStart(2, '0')}"
            }
            val warnings = buildList {
                addAll(group.warnings)
                if (item.confidence == Confidence.LOW || item.confidence == Confidence.UNKNOWN) {
                    add("Check the detected title and episode before exporting.")
                }
                matchingOperations.flatMapTo(this) { op -> op.conflicts.map { it.message } }
            }.distinct()
            ExportBundle(
                id = video.file.sourceUri,
                label = label,
                detail = if (item is MediaItem.Movie) "Movie" else "TV episode",
                operations = matchingOperations,
                warnings = warnings,
                totalBytes = matchingOperations.sumOf { sizes[it.sourceUri] ?: 0L },
            )
        }
        val groupedIds = bundles.flatMapTo(mutableSetOf()) { bundle -> bundle.operations.map { it.sourceUri } }
        return ExportPreview(
            plan = plan,
            bundles = bundles.sortedWith(compareBy({ it.detail }, { it.label.lowercase(Locale.ROOT) })),
            unmatched = plan.operations.filter { it.sourceUri !in groupedIds },
            sizes = sizes,
            sourceCount = files.size,
        )
    }
}

enum class ExportStage(val label: String) {
    IDLE("Ready"), CHECKING("Checking Shizuku"), SCANNING("Scanning protected folders"),
    PLANNING("Matching and organizing"), COPYING("Copying media"), VERIFYING("Verifying integrity"),
    PUBLISHING("Publishing to media library"), CLEANING("Cleaning original files"),
    RESTORING("Restoring original files"), COMPLETE("Finished"), ERROR("Action needed"),
}

data class ExportProgress(
    val stage: ExportStage = ExportStage.IDLE,
    val currentFile: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val completedBytes: Long = 0,
    val totalBytes: Long = 0,
    val currentCopiedBytes: Long = 0,
    val message: String = "",
) {
    val fraction: Float? get() = totalBytes.takeIf { it > 0L }?.let {
        ((completedBytes + currentCopiedBytes).toDouble() / it).toFloat().coerceIn(0f, 1f)
    }
    val percent: Int? get() = fraction?.times(100)?.toInt()
}

data class ExportOutcome(
    val exported: Int,
    val failedBundles: List<String>,
    val warnings: List<String>,
    val destinations: List<String>,
) {
    val succeeded: Boolean get() = failedBundles.isEmpty() && warnings.isEmpty()
}
