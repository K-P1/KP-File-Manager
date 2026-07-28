/** mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt: Produces complete deterministic context-aware plans from a reusable media index; optimized for one-pass organization. */
package com.kp.filemanager.domain

import java.util.Locale

class MediaOrganizationPlanner(
    private val grouper: MediaGrouper = MediaGrouper(),
    private val formatter: MediaPathFormatter = MediaPathFormatter(),
) {
    fun plan(
        files: List<ScannedFile>,
        destinationTreeUri: String,
        existingDestinationPaths: Set<String>,
        sourceAndDestinationAreSame: Boolean,
        movieFolderName: String = "Movies",
        tvFolderName: String = "TV Shows",
        scanWarnings: List<ScanWarning> = emptyList(),
    ): OperationPlan {
        val parser = MediaNameParser()
        val index = MediaIndex(
            root = DocumentEntry(destinationTreeUri, "", "Selected folder", true, 0),
            directories = emptyList(),
            items = files.map { IndexedMediaFile(it, parser.parse(it)) },
        )
        return plan(
            index = index,
            destinationTreeUri = destinationTreeUri,
            existingDestinationPaths = existingDestinationPaths,
            sourceAndDestinationAreSame = sourceAndDestinationAreSame,
            movieFolderName = movieFolderName,
            tvFolderName = tvFolderName,
            scanWarnings = scanWarnings,
            libraryContext = LibraryContext(
                LibraryContextType.GENERIC_SOURCE,
                selectedRootName = index.root.name,
            ),
        )
    }

    fun plan(
        index: MediaIndex,
        destinationTreeUri: String,
        existingDestinationPaths: Set<String>,
        sourceAndDestinationAreSame: Boolean,
        movieFolderName: String = "Movies",
        tvFolderName: String = "TV Shows",
        scanWarnings: List<ScanWarning> = emptyList(),
        libraryContext: LibraryContext = LibraryContextResolver().resolve(
            index,
            movieFolderName,
            tvFolderName,
        ),
        progressReporter: ProgressReporter? = null,
    ): OperationPlan {
        progressReporter?.begin(OrganizationPhase.MATCHING_TITLES, index.items.size)
        val grouping = grouper.group(index)
        index.items.forEach { progressReporter?.completePhaseItem(it.file.fileName) }
        progressReporter?.begin(OrganizationPhase.PLANNING, index.items.size)
        val operations = mutableListOf<PlannedFileOperation>()
        val contextConflict = if (libraryContext.type == LibraryContextType.AMBIGUOUS) {
            OperationConflict(
                ConflictType.AMBIGUOUS_LIBRARY_CONTEXT,
                "Choose whether the selected folder is a generic source, Movies library, or TV library.",
            )
        } else {
            null
        }

        grouping.groups.forEach { group ->
            val duplicateVideoConflict = if (group.videos.size > 1) {
                OperationConflict(
                    ConflictType.DUPLICATE_VIDEO,
                    "Multiple video files were detected for the same media item.",
                )
            } else {
                null
            }
            group.files.forEach { groupedFile ->
                val destinationPath = formatter.destinationPath(
                    group = group,
                    groupedFile = groupedFile,
                    movieFolderName = movieFolderName,
                    tvFolderName = tvFolderName,
                    libraryContext = libraryContext,
                )
                val conflicts = listOfNotNull(duplicateVideoConflict, contextConflict)
                operations += operationFor(
                    file = groupedFile.file,
                    destinationTreeUri = destinationTreeUri,
                    destinationPath = destinationPath,
                    reason = reasonFor(groupedFile.file.fileType),
                    conflicts = conflicts,
                    warnings = group.warnings + lowConfidenceWarning(group.mediaItem),
                    sourceAndDestinationAreSame = sourceAndDestinationAreSame,
                )
            }
        }
        grouping.unmatched.forEach { unmatched ->
            operations += skippedOperation(
                file = unmatched.file,
                destinationTreeUri = destinationTreeUri,
                conflict = OperationConflict(
                    ConflictType.UNKNOWN_MEDIA,
                    "No reliable movie or TV identity could be determined.",
                    blocking = false,
                ),
            )
        }
        grouping.ambiguous.forEach { (ambiguousFile, _) ->
            operations += skippedOperation(
                file = ambiguousFile.file,
                destinationTreeUri = destinationTreeUri,
                conflict = OperationConflict(
                    ConflictType.AMBIGUOUS_MATCH,
                    "This companion file has multiple equally plausible media matches.",
                ),
            )
        }

        val withConflicts = ConflictDetector.detect(
            operations = operations,
            existingDestinationPaths = existingDestinationPaths,
            sourceAndDestinationAreSame = sourceAndDestinationAreSame,
        )
        val sortedOperations = withConflicts.sortedWith(
            compareBy({ it.destinationPath.lowercase(Locale.ROOT) }, { it.sourcePath }),
        )
        index.items.forEach { progressReporter?.completePhaseItem(it.file.fileName) }
        val protectedDestinationPaths = sortedOperations
            .asSequence()
            .filter { it.operationType != OperationType.SKIP }
            .map(PlannedFileOperation::destinationDirectoryPath)
            .filter(String::isNotBlank)
            .flatMap { path ->
                val segments = path.replace('\\', '/').trim('/').split('/').filter(String::isNotBlank)
                segments.indices.asSequence().map { index ->
                    segments.take(index + 1).joinToString("/").lowercase(Locale.ROOT)
                }
            }
            .toSet()
        val protectedDirectoryUris = buildSet {
            add(index.root.uri)
            index.directories
                .filter { it.path.replace('\\', '/').trim('/').lowercase(Locale.ROOT) in protectedDestinationPaths }
                .mapTo(this, IndexedDirectory::uri)
        }
        return OperationPlan(
            destinationTreeUri = destinationTreeUri,
            operations = sortedOperations,
            groups = grouping.groups,
            scanWarnings = scanWarnings,
            existingDestinationPaths = existingDestinationPaths,
            sourceAndDestinationAreSame = sourceAndDestinationAreSame,
            mediaIndex = index,
            libraryContext = libraryContext,
            protectedDirectoryUris = protectedDirectoryUris,
            movieFolderName = movieFolderName,
            tvFolderName = tvFolderName,
        )
    }

    private fun operationFor(
        file: ScannedFile,
        destinationTreeUri: String,
        destinationPath: String,
        reason: String,
        conflicts: List<OperationConflict>,
        warnings: List<String>,
        sourceAndDestinationAreSame: Boolean,
    ): PlannedFileOperation {
        val newName = destinationPath.substringAfterLast('/')
        val newParent = destinationPath.substringBeforeLast('/', "")
        val sameParent = sourceAndDestinationAreSame && file.parentPath == newParent
        val sameName = file.fileName == newName
        val operationType = when {
            sameParent && sameName -> OperationType.SKIP
            sameParent -> OperationType.RENAME
            sameName -> OperationType.MOVE
            else -> OperationType.MOVE_AND_RENAME
        }
        val status = when {
            operationType == OperationType.SKIP -> OperationStatus.UNCHANGED
            conflicts.any(OperationConflict::blocking) -> OperationStatus.CONFLICT
            else -> OperationStatus.READY
        }
        return PlannedFileOperation(
            sourceUri = file.sourceUri,
            sourceParentUri = file.parentUri,
            sourcePath = file.sourcePath,
            destinationTreeUri = destinationTreeUri,
            destinationPath = destinationPath,
            originalName = file.fileName,
            newName = newName,
            mediaType = file.fileType,
            operationType = operationType,
            reason = reason,
            status = status,
            conflicts = conflicts,
            warnings = warnings,
        )
    }

    private fun skippedOperation(
        file: ScannedFile,
        destinationTreeUri: String,
        conflict: OperationConflict,
    ) = PlannedFileOperation(
        sourceUri = file.sourceUri,
        sourceParentUri = file.parentUri,
        sourcePath = file.sourcePath,
        destinationTreeUri = destinationTreeUri,
        destinationPath = file.sourcePath,
        originalName = file.fileName,
        newName = file.fileName,
        mediaType = file.fileType,
        operationType = OperationType.SKIP,
        reason = "Skipped because a safe destination could not be determined.",
        status = if (conflict.blocking) OperationStatus.CONFLICT else OperationStatus.SKIPPED,
        conflicts = listOf(conflict),
    )

    private fun reasonFor(type: MediaFileType): String = when (type) {
        MediaFileType.VIDEO -> "Normalize the detected media file."
        MediaFileType.SUBTITLE -> "Keep the matched subtitle beside its video."
        MediaFileType.ARTWORK -> "Keep recognized artwork with its movie."
        MediaFileType.METADATA -> "Keep metadata with its movie."
        MediaFileType.UNKNOWN -> "No operation."
    }

    private fun lowConfidenceWarning(item: MediaItem): List<String> =
        if (item.confidence == Confidence.LOW) {
            listOf("Low-confidence identity; review before applying.")
        } else {
            emptyList()
        }
}

class FileToolPlanner(
    private val formatter: MediaPathFormatter = MediaPathFormatter(),
) {
    fun planBatchRename(
        files: List<ScannedFile>,
        request: BatchRenameRequest,
        destinationTreeUri: String = files.firstOrNull()?.parentUri.orEmpty(),
    ): List<PlannedFileOperation> {
        val matches = matchingFiles(files, request.extensionFilter)
        val operations = matches.mapIndexed { index, file ->
            val number = (request.startingNumber + index)
                .toString()
                .padStart(request.digitPadding.coerceIn(0, 12), '0')
            val extension = normalizedReplacementExtension(request.replacementExtension) ?: file.extension
            val newName = formatter.sanitizeSegment("${request.prefix}$number${request.suffix}.${extension}")
            renameOperation(file, newName, destinationTreeUri, "Apply the batch rename pattern.")
        }
        return ConflictDetector.detect(
            operations = operations,
            existingDestinationPaths = files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
        )
    }

    fun planFindAndReplace(
        files: List<ScannedFile>,
        request: FindAndReplaceRequest,
        destinationTreeUri: String = files.firstOrNull()?.parentUri.orEmpty(),
    ): List<PlannedFileOperation> {
        if (request.findText.isEmpty()) return emptyList()
        val operations = matchingFiles(files, request.extensionFilter)
            .filter { request.findText in it.baseName }
            .map { file ->
                val newBaseName = file.baseName.replace(request.findText, request.replacementText)
                val newName = formatter.sanitizeSegment("$newBaseName.${file.extension}")
                renameOperation(file, newName, destinationTreeUri, "Replace matching filename text.")
            }
        return ConflictDetector.detect(
            operations = operations,
            existingDestinationPaths = files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
        )
    }

    private fun matchingFiles(files: List<ScannedFile>, extensionFilter: String): List<ScannedFile> {
        val filter = extensionFilter.trim().trimStart('.').lowercase(Locale.ROOT)
        return files.asSequence()
            .filter { filter.isBlank() || filter == "*" || it.extension == filter }
            .sortedWith(compareBy({ it.fileName.lowercase(Locale.ROOT) }, ScannedFile::sourcePath))
            .toList()
    }

    private fun renameOperation(
        file: ScannedFile,
        newName: String,
        destinationTreeUri: String,
        reason: String,
    ): PlannedFileOperation {
        val destinationPath = listOf(file.parentPath, newName).filter(String::isNotBlank).joinToString("/")
        val unchanged = file.fileName == newName
        return PlannedFileOperation(
            sourceUri = file.sourceUri,
            sourceParentUri = file.parentUri,
            sourcePath = file.sourcePath,
            destinationTreeUri = destinationTreeUri,
            destinationPath = destinationPath,
            originalName = file.fileName,
            newName = newName,
            mediaType = file.fileType,
            operationType = if (unchanged) OperationType.SKIP else OperationType.RENAME,
            reason = reason,
            status = if (unchanged) OperationStatus.UNCHANGED else OperationStatus.READY,
        )
    }

    private fun normalizedReplacementExtension(value: String?): String? = value
        ?.trim()
        ?.trimStart('.')
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
}

object ConflictDetector {
    fun detect(
        operations: List<PlannedFileOperation>,
        existingDestinationPaths: Set<String>,
        sourceAndDestinationAreSame: Boolean,
    ): List<PlannedFileOperation> {
        val existingByCaseFolded = existingDestinationPaths.groupBy(::caseFold)
        val duplicates = operations
            .filter { it.operationType != OperationType.SKIP }
            .groupBy { caseFold(it.destinationPath) }
            .filterValues { it.size > 1 }
            .keys
        val duplicateVideoIdentities = operations
            .filter { it.operationType != OperationType.SKIP && it.mediaType == MediaFileType.VIDEO }
            .groupBy { caseFold(it.destinationPath.substringBeforeLast('.', it.destinationPath)) }
            .filterValues { it.size > 1 }
            .keys

        return operations.map { operation ->
            if (operation.status in setOf(OperationStatus.SKIPPED, OperationStatus.UNCHANGED)) return@map operation
            val conflicts = operation.conflicts.toMutableList()
            val targetKey = caseFold(operation.destinationPath)
            val sourceKey = caseFold(operation.sourcePath)
            val videoIdentityKey = caseFold(
                operation.destinationPath.substringBeforeLast('.', operation.destinationPath),
            )
            if (operation.mediaType == MediaFileType.VIDEO && videoIdentityKey in duplicateVideoIdentities) {
                conflicts += OperationConflict(
                    ConflictType.DUPLICATE_VIDEO,
                    "Multiple video files were detected for the same media item.",
                )
            }
            if (targetKey in duplicates) {
                conflicts += OperationConflict(
                    ConflictType.DUPLICATE_DESTINATION,
                    "Multiple source files map to the same destination.",
                )
                if (operation.mediaType != MediaFileType.VIDEO) {
                    conflicts += OperationConflict(
                        ConflictType.DUPLICATE_COMPANION,
                        "Multiple companion files map to the same normalized name.",
                    )
                }
            }
            val existingMatches = existingByCaseFolded[targetKey].orEmpty()
            val targetIsOwnSource = sourceAndDestinationAreSame && targetKey == sourceKey
            if (existingMatches.isNotEmpty() && !targetIsOwnSource) {
                if (existingMatches.any { it != operation.destinationPath }) {
                    conflicts += OperationConflict(
                        ConflictType.CASE_ONLY_COLLISION,
                        "The destination differs from an existing path only by letter case.",
                    )
                } else {
                    conflicts += OperationConflict(
                        ConflictType.DESTINATION_EXISTS,
                        "A file already exists at the destination.",
                    )
                }
            }
            operation.copy(
                status = if (conflicts.any(OperationConflict::blocking)) OperationStatus.CONFLICT else operation.status,
                conflicts = conflicts.distinctBy { it.type to it.message },
            )
        }
    }

    private fun caseFold(path: String): String = path.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
}

class PlanConflictResolver(
    private val formatter: MediaPathFormatter = MediaPathFormatter(),
) {
    fun skip(plan: OperationPlan, sourceUri: String): OperationPlan =
        plan.copy(
            operations = ConflictDetector.detect(
                operations = plan.operations.map { operation ->
                if (operation.sourceUri != sourceUri) {
                    operation.copy(conflicts = operation.conflicts.filterNot { it.type in RENAMING_CONFLICTS })
                } else {
                    operation.copy(
                        operationType = OperationType.SKIP,
                        status = OperationStatus.SKIPPED,
                        reason = "Skipped by the user during conflict review.",
                        conflicts = emptyList(),
                    )
                }
                },
                existingDestinationPaths = plan.existingDestinationPaths,
                sourceAndDestinationAreSame = plan.sourceAndDestinationAreSame,
            ),
        )

    fun rename(
        plan: OperationPlan,
        sourceUri: String,
        alternativeName: String,
    ): OperationPlan {
        require('/' !in alternativeName && '\\' !in alternativeName) {
            "The alternative name must not contain path separators."
        }
        val safeName = formatter.sanitizeSegment(alternativeName)
        val revised = plan.operations.map { operation ->
            if (operation.sourceUri != sourceUri) return@map operation
            val destinationDirectory = operation.destinationDirectoryPath
            val destinationPath = listOf(destinationDirectory, safeName)
                .filter(String::isNotBlank)
                .joinToString("/")
            val sourceDirectory = operation.sourcePath.substringBeforeLast('/', "")
            val sameParent = plan.sourceAndDestinationAreSame && sourceDirectory == destinationDirectory
            val sameName = operation.originalName == safeName
            val operationType = when {
                sameParent && sameName -> OperationType.SKIP
                sameParent -> OperationType.RENAME
                sameName -> OperationType.MOVE
                else -> OperationType.MOVE_AND_RENAME
            }
            val remainingConflicts = operation.conflicts.filterNot {
                it.type in RENAMING_CONFLICTS
            }
            operation.copy(
                destinationPath = destinationPath,
                newName = safeName,
                operationType = operationType,
                status = when {
                    operationType == OperationType.SKIP -> OperationStatus.UNCHANGED
                    remainingConflicts.any(OperationConflict::blocking) -> OperationStatus.CONFLICT
                    else -> OperationStatus.READY
                },
                conflicts = remainingConflicts,
                reason = "Use the alternative name selected during conflict review.",
            )
        }
        return plan.copy(
            operations = ConflictDetector.detect(
                operations = revised,
                existingDestinationPaths = plan.existingDestinationPaths,
                sourceAndDestinationAreSame = plan.sourceAndDestinationAreSame,
            ),
        )
    }

    private companion object {
        val RENAMING_CONFLICTS = setOf(
            ConflictType.DESTINATION_EXISTS,
            ConflictType.DUPLICATE_DESTINATION,
            ConflictType.DUPLICATE_COMPANION,
            ConflictType.DUPLICATE_VIDEO,
            ConflictType.CASE_ONLY_COLLISION,
            ConflictType.INVALID_FILENAME,
        )
    }
}
