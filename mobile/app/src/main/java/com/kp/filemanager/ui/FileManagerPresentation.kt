/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerPresentation.kt: Centralizes destinations, review filters, and operation summaries for the adaptive UI; created for the UI refactor. */
package com.kp.filemanager.ui

import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.domain.MediaItem
import java.util.Locale

enum class AppDestination(
    val label: String,
    val compactLabel: String = label,
) {
    ORGANIZE("Organize Media", "Organize"),
    BATCH_RENAME("Batch Rename", "Rename"),
    FIND_AND_REPLACE("Find & Replace", "Replace"),
    SETTINGS("Settings"),
}

enum class ReviewFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
    CONFLICTS("Conflicts"),
    SKIPPED("Skipped"),
}

enum class ReviewSort(val label: String) {
    TITLE("Title"),
    DESTINATION("Destination"),
    OPERATION("Operation"),
    STATUS("Status"),
}

data class ReviewSummary(
    val detected: Int,
    val ready: Int,
    val conflicts: Int,
    val skipped: Int,
    val unchanged: Int,
)

data class ConfirmationSummary(
    val changes: Int,
    val moves: Int,
    val renames: Int,
    val skipped: Int,
    val unchanged: Int,
)

data class ReviewCriteria(
    val operationTypes: Set<OperationType> = emptySet(),
    val statuses: Set<OperationStatus> = emptySet(),
    val lowConfidenceOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = operationTypes.isNotEmpty() || statuses.isNotEmpty() || lowConfidenceOnly
}

fun OperationPlan.reviewSummary() = ReviewSummary(
    detected = operations.size,
    ready = operations.count { it.status == OperationStatus.READY },
    conflicts = operations.count { it.status == OperationStatus.CONFLICT },
    skipped = operations.count { it.status == OperationStatus.SKIPPED },
    unchanged = operations.count { it.status == OperationStatus.UNCHANGED },
)

fun OperationPlan.confirmationSummary() = ConfirmationSummary(
    changes = operations.count { it.status == OperationStatus.READY },
    moves = operations.count {
        it.status == OperationStatus.READY &&
            it.operationType in setOf(OperationType.MOVE, OperationType.MOVE_AND_RENAME)
    },
    renames = operations.count {
        it.status == OperationStatus.READY &&
            it.operationType in setOf(OperationType.RENAME, OperationType.MOVE_AND_RENAME)
    },
    skipped = operations.count { it.status == OperationStatus.SKIPPED },
    unchanged = operations.count { it.status == OperationStatus.UNCHANGED },
)

fun List<PlannedFileOperation>.filteredBy(filter: ReviewFilter): List<PlannedFileOperation> = when (filter) {
    ReviewFilter.ALL, ReviewFilter.MOVIES, ReviewFilter.TV_SHOWS -> this
    ReviewFilter.CONFLICTS -> filter { it.status == OperationStatus.CONFLICT }
    ReviewFilter.SKIPPED -> filter {
        it.status in setOf(OperationStatus.SKIPPED, OperationStatus.UNCHANGED)
    }
}

fun OperationPlan.operationsFor(filter: ReviewFilter): List<PlannedFileOperation> {
    if (filter !in setOf(ReviewFilter.MOVIES, ReviewFilter.TV_SHOWS)) {
        return operations.filteredBy(filter)
    }
    val sourceUris = groups
        .asSequence()
        .filter { group ->
            when (filter) {
                ReviewFilter.MOVIES -> group.mediaItem is MediaItem.Movie
                ReviewFilter.TV_SHOWS -> group.mediaItem is MediaItem.TvEpisode
                else -> false
            }
        }
        .flatMap { it.files.asSequence() }
        .map { it.file.sourceUri }
        .toSet()
    return operations.filter { it.sourceUri in sourceUris }
}

fun List<PlannedFileOperation>.searchedAndSorted(
    query: String,
    sort: ReviewSort,
): List<PlannedFileOperation> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val matching = if (normalizedQuery.isEmpty()) {
        this
    } else {
        filter { operation ->
            sequenceOf(
                operation.originalName,
                operation.sourcePath,
                operation.destinationPath,
                operation.reason,
            ).any { normalizedQuery in it.lowercase(Locale.ROOT) }
        }
    }
    val comparator = when (sort) {
        ReviewSort.TITLE -> compareBy<PlannedFileOperation> { it.originalName.lowercase(Locale.ROOT) }
        ReviewSort.DESTINATION -> compareBy { it.destinationPath.lowercase(Locale.ROOT) }
        ReviewSort.OPERATION -> compareBy({ it.operationType.ordinal }, { it.originalName.lowercase(Locale.ROOT) })
        ReviewSort.STATUS -> compareBy({ it.status.ordinal }, { it.originalName.lowercase(Locale.ROOT) })
    }
    return matching.sortedWith(comparator)
}

fun List<PlannedFileOperation>.refinedBy(criteria: ReviewCriteria): List<PlannedFileOperation> =
    filter { operation ->
        (criteria.operationTypes.isEmpty() || operation.operationType in criteria.operationTypes) &&
            (criteria.statuses.isEmpty() || operation.status in criteria.statuses) &&
            (!criteria.lowConfidenceOnly || operation.warnings.any {
                "low-confidence" in it.lowercase(Locale.ROOT)
            })
    }
