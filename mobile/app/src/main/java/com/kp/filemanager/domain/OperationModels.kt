/** mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt: Defines indexed media discovery, context-aware planning, progress, execution, and retained tool contracts; optimized for one-pass organization. */
package com.kp.filemanager.domain

import java.util.Locale

enum class MediaFileType {
    VIDEO,
    SUBTITLE,
    ARTWORK,
    METADATA,
    UNKNOWN,
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

enum class SubtitleQualifier(val suffix: String) {
    FORCED("forced"),
    SDH("sdh"),
    CC("cc"),
    DEFAULT("default"),
}

data class ScannedFile(
    val sourceUri: String,
    val parentUri: String,
    val sourcePath: String,
    val fileName: String,
    val baseName: String,
    val extension: String,
    val parentPath: String,
    val depth: Int,
    val size: Long,
    val fileType: MediaFileType,
    val mimeType: String = "",
    val lastModified: Long = 0,
    val flags: Int = 0,
    val documentId: String = "",
) {
    companion object {
        fun from(
            sourceUri: String,
            parentUri: String,
            parentPath: String,
            fileName: String,
            depth: Int,
            size: Long,
            fileType: MediaFileType = MediaFileClassifier.classify(fileName),
            mimeType: String = "",
            lastModified: Long = 0,
            flags: Int = 0,
            documentId: String = "",
        ): ScannedFile {
            val cleanParent = parentPath.trim('/')
            val sourcePath = listOf(cleanParent, fileName).filter(String::isNotBlank).joinToString("/")
            return ScannedFile(
                sourceUri = sourceUri,
                parentUri = parentUri,
                sourcePath = sourcePath,
                fileName = fileName,
                baseName = fileName.substringBeforeLast('.', fileName),
                extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT),
                parentPath = cleanParent,
                depth = depth,
                size = size,
                fileType = fileType,
                mimeType = mimeType,
                lastModified = lastModified,
                flags = flags,
                documentId = documentId,
            )
        }
    }
}

data class ParsedMediaName(
    val originalName: String,
    val normalizedTitle: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeEnd: Int? = null,
    val part: Int? = null,
    val language: String? = null,
    val subtitleQualifiers: Set<SubtitleQualifier> = emptySet(),
    val edition: String? = null,
    val extension: String,
    val confidence: Confidence,
    val evidence: List<String> = emptyList(),
)

sealed interface MediaItem {
    val title: String
    val confidence: Confidence

    data class Movie(
        override val title: String,
        val year: Int?,
        override val confidence: Confidence,
    ) : MediaItem

    data class TvEpisode(
        override val title: String,
        val year: Int?,
        val season: Int,
        val episode: Int,
        val episodeEnd: Int?,
        override val confidence: Confidence,
    ) : MediaItem
}

data class GroupedFile(
    val file: ScannedFile,
    val parsedName: ParsedMediaName,
)

data class OrganizationGroup(
    val mediaItem: MediaItem,
    val files: List<GroupedFile>,
    val warnings: List<String> = emptyList(),
) {
    val videos: List<GroupedFile>
        get() = files.filter { it.file.fileType == MediaFileType.VIDEO }
}

data class GroupingResult(
    val groups: List<OrganizationGroup>,
    val unmatched: List<GroupedFile>,
    val ambiguous: Map<GroupedFile, List<OrganizationGroup>>,
)

data class IndexedDirectory(
    val uri: String,
    val parentUri: String,
    val path: String,
    val name: String,
    val depth: Int,
    val documentId: String = "",
    val lastModified: Long = 0,
    val flags: Int = 0,
)

data class IndexedMediaFile(
    val file: ScannedFile,
    val parsedName: ParsedMediaName,
)

data class MediaIndex(
    val root: DocumentEntry,
    val directories: List<IndexedDirectory>,
    val items: List<IndexedMediaFile>,
    val treeUri: String = root.uri,
) {
    val files: List<ScannedFile> = items.map(IndexedMediaFile::file)
    val parsedBySourceUri: Map<String, ParsedMediaName> =
        items.associate { it.file.sourceUri to it.parsedName }
    val filesByParentUri: Map<String, List<IndexedMediaFile>> =
        items.groupBy { it.file.parentUri }
    val directoriesByPath: Map<String, IndexedDirectory> =
        directories.associateBy { normalizePath(it.path) }

    companion object {
        private fun normalizePath(path: String): String =
            path.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
    }
}

enum class LibraryContextType {
    MOVIES_LIBRARY_ROOT,
    TV_LIBRARY_ROOT,
    MOVIE_FOLDER,
    SERIES_FOLDER,
    GENERIC_SOURCE,
    AMBIGUOUS,
}

data class LibraryContext(
    val type: LibraryContextType,
    val selectedRootName: String,
    val selectedRootIsSeason: Boolean = false,
    val reason: String = "",
)

enum class OperationType {
    MOVE,
    RENAME,
    MOVE_AND_RENAME,
    SKIP,
}

enum class OperationStatus {
    PENDING,
    READY,
    RUNNING,
    COMPLETED,
    SKIPPED,
    CONFLICT,
    FAILED,
    UNCHANGED,
}

enum class ConflictType {
    DESTINATION_EXISTS,
    DUPLICATE_DESTINATION,
    DUPLICATE_VIDEO,
    DUPLICATE_COMPANION,
    AMBIGUOUS_MATCH,
    UNKNOWN_MEDIA,
    INVALID_FILENAME,
    CASE_ONLY_COLLISION,
    SOURCE_MISSING,
    PLAN_INVALIDATED,
    AMBIGUOUS_LIBRARY_CONTEXT,
}

data class OperationConflict(
    val type: ConflictType,
    val message: String,
    val blocking: Boolean = true,
)

data class PlannedFileOperation(
    val sourceUri: String,
    val sourceParentUri: String,
    val sourcePath: String,
    val destinationTreeUri: String,
    val destinationPath: String,
    val originalName: String,
    val newName: String,
    val mediaType: MediaFileType,
    val operationType: OperationType,
    val reason: String,
    val status: OperationStatus = OperationStatus.READY,
    val conflicts: List<OperationConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val sourceName: String
        get() = originalName

    val destinationDirectoryPath: String
        get() = destinationPath.substringBeforeLast('/', "")

    val hasBlockingConflict: Boolean
        get() = conflicts.any(OperationConflict::blocking)
}

typealias PlannedChange = PlannedFileOperation

data class ScanWarning(
    val path: String,
    val reason: String,
)

data class ScanProgress(
    val filesScanned: Int = 0,
    val directoriesScanned: Int = 0,
    val moviesDetected: Int = 0,
    val episodesDetected: Int = 0,
    val subtitlesDetected: Int = 0,
    val currentPath: String = "",
)

data class ScanResult(
    val index: MediaIndex,
    val warnings: List<ScanWarning> = emptyList(),
    val ignoredDirectoryCount: Int = 0,
) {
    val files: List<ScannedFile>
        get() = index.files
}

enum class OrganizationPhase(val label: String) {
    IDLE("Ready"),
    SCANNING("Scanning"),
    MATCHING_TITLES("Matching Titles"),
    PLANNING("Planning"),
    ORGANIZING_FILES("Organizing Files"),
    RENAMING("Renaming"),
    CLEANING_UP("Cleaning Up"),
    COMPLETE("Complete"),
}

data class OrganizationProgress(
    val phase: OrganizationPhase = OrganizationPhase.IDLE,
    val isIndeterminate: Boolean = true,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val remainingItems: Int = 0,
    val percent: Int? = null,
    val currentItem: String = "",
    val filesDiscovered: Int = 0,
    val directoriesDiscovered: Int = 0,
    val moviesDetected: Int = 0,
    val episodesDetected: Int = 0,
    val subtitlesDetected: Int = 0,
    val movedCount: Int = 0,
    val renamedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val cleanedFolderCount: Int = 0,
    val etaMillis: Long? = null,
    val startedAtMillis: Long? = null,
)

data class ProviderOperationCounters(
    val queries: Int = 0,
    val moves: Int = 0,
    val renames: Int = 0,
    val copyDeleteFallbacks: Int = 0,
)

data class PerformanceMetrics(
    val scanDurationMillis: Long = 0,
    val matchingPlanningDurationMillis: Long = 0,
    val organizationDurationMillis: Long = 0,
    val cleanupDurationMillis: Long = 0,
    val providerOperations: ProviderOperationCounters = ProviderOperationCounters(),
    val failedOperations: Int = 0,
    val skippedOperations: Int = 0,
)

data class OperationPlan(
    val destinationTreeUri: String,
    val operations: List<PlannedFileOperation>,
    val groups: List<OrganizationGroup> = emptyList(),
    val scanWarnings: List<ScanWarning> = emptyList(),
    val existingDestinationPaths: Set<String> = emptySet(),
    val sourceAndDestinationAreSame: Boolean = false,
    val mediaIndex: MediaIndex? = null,
    val libraryContext: LibraryContext? = null,
    val protectedDirectoryUris: Set<String> = emptySet(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val movieFolderName: String = "Movies",
    val tvFolderName: String = "TV Shows",
) {
    val hasBlockingConflicts: Boolean
        get() = operations.any(PlannedFileOperation::hasBlockingConflict)

    val readyCount: Int
        get() = operations.count { it.status == OperationStatus.READY }
}

enum class OperationMode(val label: String, val supportingText: String) {
    ORGANIZE_MEDIA("Organize", "Scan and normalize movies, TV episodes, and companion files"),
    BATCH_RENAME("Batch Rename", "Rename files with a numbered pattern"),
    FIND_AND_REPLACE("Find & Replace", "Replace or remove text in filenames"),
}

data class OperationRequest(
    val mode: OperationMode,
    val sourceTreeUri: String,
    val destinationTreeUri: String? = null,
    val maximumScanDepth: Int = 4,
    val movieFolderName: String = "Movies",
    val tvFolderName: String = "TV Shows",
    val extensionFilter: String = "*",
    val prefix: String = "Renamed_",
    val suffix: String = "",
    val startIndex: Int = 1,
    val zeroPadding: Int = 2,
    val replacementExtension: String? = null,
    val keyword: String = "",
    val replacement: String = "",
)

data class BatchRenameRequest(
    val extensionFilter: String = "*",
    val prefix: String = "Renamed_",
    val suffix: String = "",
    val startingNumber: Int = 1,
    val digitPadding: Int = 2,
    val replacementExtension: String? = null,
)

data class FindAndReplaceRequest(
    val findText: String,
    val replacementText: String,
    val extensionFilter: String = "*",
)

data class OperationFailure(
    val operation: PlannedFileOperation,
    val reason: String,
)

data class OperationResult(
    val plan: OperationPlan,
    val completedOperations: List<PlannedFileOperation> = emptyList(),
    val failedOperations: List<OperationFailure> = emptyList(),
    val skippedOperations: List<PlannedFileOperation> = emptyList(),
    val warnings: List<String> = emptyList(),
    val cleanedDirectoryCount: Int = 0,
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
) {
    val changes: List<PlannedFileOperation>
        get() = plan.operations

    val completedCount: Int
        get() = completedOperations.size

    val failures: List<String>
        get() = failedOperations.map { "${it.operation.originalName}: ${it.reason}" }
}
