/** mobile/app/src/test/java/com/kp/filemanager/ui/FileManagerPresentationTest.kt: Verifies UI summaries and review filtering; created for the UI refactor. */
package com.kp.filemanager.ui

import com.kp.filemanager.domain.MediaFileType
import com.kp.filemanager.domain.Confidence
import com.kp.filemanager.domain.GroupedFile
import com.kp.filemanager.domain.MediaItem
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.OrganizationGroup
import com.kp.filemanager.domain.ParsedMediaName
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.domain.ScannedFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FileManagerPresentationTest {
    @Test
    fun `review summary reports ready conflict skipped and unchanged operations`() {
        val plan = plan(
            operation("ready", OperationStatus.READY, OperationType.MOVE),
            operation("conflict", OperationStatus.CONFLICT, OperationType.RENAME),
            operation("skipped", OperationStatus.SKIPPED, OperationType.SKIP),
            operation("unchanged", OperationStatus.UNCHANGED, OperationType.SKIP),
        )

        val summary = plan.reviewSummary()

        assertEquals(1, summary.ready)
        assertEquals(1, summary.conflicts)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.unchanged)
        assertEquals(4, summary.detected)
    }

    @Test
    fun `confirmation summary separates move rename unchanged and skipped work`() {
        val plan = plan(
            operation("move", OperationStatus.READY, OperationType.MOVE),
            operation("rename", OperationStatus.READY, OperationType.RENAME),
            operation("both", OperationStatus.READY, OperationType.MOVE_AND_RENAME),
            operation("skipped", OperationStatus.SKIPPED, OperationType.SKIP),
            operation("unchanged", OperationStatus.UNCHANGED, OperationType.SKIP),
        )

        val summary = plan.confirmationSummary()

        assertEquals(2, summary.moves)
        assertEquals(2, summary.renames)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.unchanged)
        assertEquals(3, summary.changes)
    }

    @Test
    fun `review filter selects conflicts and skipped items without hiding nonblocking skips`() {
        val conflict = operation("conflict", OperationStatus.CONFLICT, OperationType.RENAME)
        val skipped = operation("skipped", OperationStatus.SKIPPED, OperationType.SKIP)
        val unchanged = operation("unchanged", OperationStatus.UNCHANGED, OperationType.SKIP)
        val ready = operation("ready", OperationStatus.READY, OperationType.MOVE)
        val operations = listOf(conflict, skipped, unchanged, ready)

        assertEquals(listOf(conflict), operations.filteredBy(ReviewFilter.CONFLICTS))
        assertEquals(listOf(skipped, unchanged), operations.filteredBy(ReviewFilter.SKIPPED))
        assertEquals(operations, operations.filteredBy(ReviewFilter.ALL))
    }

    @Test
    fun `media review filters follow organizer group identities`() {
        val movie = operation("movie", OperationStatus.READY, OperationType.MOVE)
        val episode = operation("episode", OperationStatus.READY, OperationType.MOVE)
        val plan = OperationPlan(
            destinationTreeUri = "destination",
            operations = listOf(movie, episode),
            groups = listOf(
                group(movie, MediaItem.Movie("Movie", 2024, Confidence.HIGH)),
                group(episode, MediaItem.TvEpisode("Show", null, 1, 1, null, Confidence.HIGH)),
            ),
        )

        assertEquals(listOf(movie), plan.operationsFor(ReviewFilter.MOVIES))
        assertEquals(listOf(episode), plan.operationsFor(ReviewFilter.TV_SHOWS))
    }

    @Test
    fun `review search matches paths case insensitively and sorts by destination`() {
        val second = operation("Zulu", OperationStatus.READY, OperationType.MOVE)
        val first = operation("alpha", OperationStatus.CONFLICT, OperationType.RENAME)
        val operations = listOf(second, first)

        assertEquals(
            listOf(first),
            operations.searchedAndSorted("ALPHA", ReviewSort.DESTINATION),
        )
        assertEquals(
            listOf(first, second),
            operations.searchedAndSorted("", ReviewSort.DESTINATION),
        )
    }

    @Test
    fun `advanced review criteria combine operation status and low confidence filters`() {
        val lowConfidenceMove = operation("low", OperationStatus.READY, OperationType.MOVE)
            .copy(warnings = listOf("Low-confidence identity; review before applying."))
        val rename = operation("rename", OperationStatus.READY, OperationType.RENAME)
        val conflict = operation("conflict", OperationStatus.CONFLICT, OperationType.MOVE)

        val result = listOf(lowConfidenceMove, rename, conflict).refinedBy(
            ReviewCriteria(
                operationTypes = setOf(OperationType.MOVE),
                statuses = setOf(OperationStatus.READY),
                lowConfidenceOnly = true,
            ),
        )

        assertEquals(listOf(lowConfidenceMove), result)
    }

    private fun operation(
        id: String,
        status: OperationStatus,
        type: OperationType,
    ) = PlannedFileOperation(
        sourceUri = id,
        sourceParentUri = "source",
        sourcePath = "Source/$id.mkv",
        destinationTreeUri = "destination",
        destinationPath = "Movies/$id/$id.mkv",
        originalName = "$id.mkv",
        newName = "$id.mkv",
        mediaType = MediaFileType.VIDEO,
        operationType = type,
        reason = "test",
        status = status,
    )

    private fun plan(vararg operations: PlannedFileOperation) =
        OperationPlan("destination", operations.toList())

    private fun group(
        operation: PlannedFileOperation,
        item: MediaItem,
    ): OrganizationGroup {
        val scanned = ScannedFile.from(
            sourceUri = operation.sourceUri,
            parentUri = operation.sourceParentUri,
            parentPath = "Source",
            fileName = operation.originalName,
            depth = 0,
            size = 1,
        )
        return OrganizationGroup(
            mediaItem = item,
            files = listOf(
                GroupedFile(
                    file = scanned,
                    parsedName = ParsedMediaName(
                        originalName = operation.originalName,
                        normalizedTitle = item.title,
                        extension = "mkv",
                        confidence = Confidence.HIGH,
                    ),
                ),
            ),
        )
    }
}
