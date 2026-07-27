/** mobile/app/src/test/java/com/kp/filemanager/domain/FileToolPlannerTest.kt: Covers safe Batch Rename and Find & Replace previews; created for the media refactor. */
package com.kp.filemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileToolPlannerTest {
    private val planner = FileToolPlanner()

    @Test
    fun `batch rename supports prefix suffix padding and extension replacement`() {
        val operations = planner.planBatchRename(
            files = listOf(file("b.mp4"), file("a.mkv")),
            request = BatchRenameRequest(
                extensionFilter = "*",
                prefix = "Episode_",
                suffix = "_Final",
                startingNumber = 7,
                digitPadding = 3,
                replacementExtension = "mp4",
            ),
        )

        assertEquals(listOf("Episode_007_Final.mp4", "Episode_008_Final.mp4"), operations.map { it.newName })
    }

    @Test
    fun `find and replace supports an empty replacement and extension filter`() {
        val operations = planner.planFindAndReplace(
            files = listOf(file("Movie.1080p.mkv"), file("Movie.1080p.srt")),
            request = FindAndReplaceRequest(findText = ".1080p", replacementText = "", extensionFilter = "mkv"),
        )

        assertEquals(listOf("Movie.mkv"), operations.map { it.newName })
    }

    @Test
    fun `tool plans flag target collisions without inventing unique names`() {
        val operations = planner.planBatchRename(
            files = listOf(file("a.mkv"), file("Episode_001.mkv")),
            request = BatchRenameRequest(prefix = "Episode_", startingNumber = 1, digitPadding = 3),
        )

        assertTrue(operations.any { it.status == OperationStatus.CONFLICT })
        assertTrue(
            operations.flatMap(PlannedFileOperation::conflicts)
                .any { it.type == ConflictType.DESTINATION_EXISTS },
        )
    }

    private fun file(name: String) = ScannedFile.from(
        sourceUri = "content://test/$name",
        parentUri = "content://test/root",
        parentPath = "",
        fileName = name,
        depth = 0,
        size = 1,
        fileType = MediaFileClassifier.classify(name),
    )
}
