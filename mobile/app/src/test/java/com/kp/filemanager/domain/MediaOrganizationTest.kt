/** mobile/app/src/test/java/com/kp/filemanager/domain/MediaOrganizationTest.kt: Verifies matching, naming, idempotency, and organization conflicts; created for the media refactor. */
package com.kp.filemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOrganizationTest {
    private val planner = MediaOrganizationPlanner()

    @Test
    fun `representative library groups companions and creates normalized destinations`() {
        val files = listOf(
            file("Lost.S01E01.1080p.x264.mkv"),
            file("Lost - 1x01.en.srt"),
            file("Lost Season 1 Episode 02.mp4"),
            file("Lost_s01_e02.forced.srt"),
            file("Avatar.2009.1080p.mkv"),
            file("Avatar.2009.en.srt"),
            file("Avatar.2009.Poster.jpg"),
        )

        val plan = planner.plan(
            files = files,
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
        )

        assertEquals(
            setOf(
                "TV Shows/Lost/Season 01/Lost - S01E01.mkv",
                "TV Shows/Lost/Season 01/Lost - S01E01.en.srt",
                "TV Shows/Lost/Season 01/Lost - S01E02.mp4",
                "TV Shows/Lost/Season 01/Lost - S01E02.forced.srt",
                "Movies/Avatar (2009)/Avatar (2009).mkv",
                "Movies/Avatar (2009)/Avatar (2009).en.srt",
                "Movies/Avatar (2009)/Poster.jpg",
            ),
            plan.operations.map(PlannedFileOperation::destinationPath).toSet(),
        )
        assertTrue(plan.operations.all { it.status == OperationStatus.READY })
    }

    @Test
    fun `already organized file produces unchanged operation`() {
        val path = "TV Shows/Lost/Season 01/Lost - S01E01.mkv"
        val media = ScannedFile.from(
            sourceUri = "content://test/episode",
            parentUri = "content://test/season",
            parentPath = "TV Shows/Lost/Season 01",
            fileName = "Lost - S01E01.mkv",
            depth = 3,
            size = 1,
            fileType = MediaFileType.VIDEO,
        )

        val operation = planner.plan(
            files = listOf(media),
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = setOf(path),
            sourceAndDestinationAreSame = true,
        ).operations.single()

        assertEquals(OperationType.SKIP, operation.operationType)
        assertEquals(OperationStatus.UNCHANGED, operation.status)
    }

    @Test
    fun `existing destination and duplicate target are blocking conflicts`() {
        val files = listOf(
            file("Lost.S01E01.mkv", uriSuffix = "a"),
            file("Lost - S01E01.mp4", uriSuffix = "b"),
        )

        val plan = planner.plan(
            files = files,
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = setOf("TV Shows/Lost/Season 01/Lost - S01E01.mkv"),
            sourceAndDestinationAreSame = true,
        )

        assertTrue(plan.operations.all { it.status == OperationStatus.CONFLICT })
        assertTrue(
            plan.operations.flatMap(PlannedFileOperation::conflicts)
                .any { it.type == ConflictType.DUPLICATE_VIDEO },
        )
        assertTrue(
            plan.operations.flatMap(PlannedFileOperation::conflicts)
                .any { it.type == ConflictType.DESTINATION_EXISTS },
        )
    }

    @Test
    fun `ambiguous subtitle is skipped instead of attached`() {
        val files = listOf(
            file("Movie One.mkv", parentPath = "Incoming"),
            file("Movie Two.mkv", parentPath = "Incoming"),
            file("English.srt", parentPath = "Incoming"),
        )

        val plan = planner.plan(
            files = files,
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = emptySet(),
            sourceAndDestinationAreSame = true,
        )

        val subtitle = plan.operations.single { it.originalName == "English.srt" }
        assertEquals(OperationStatus.CONFLICT, subtitle.status)
        assertEquals(ConflictType.AMBIGUOUS_MATCH, subtitle.conflicts.single().type)
    }

    @Test
    fun `weak episode without season context is skipped instead of treated as a movie`() {
        val weakEpisode = file("Lost Episode 05.mkv")

        val operation = planner.plan(
            files = listOf(weakEpisode),
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = emptySet(),
            sourceAndDestinationAreSame = true,
        ).operations.single()

        assertEquals(OperationStatus.SKIPPED, operation.status)
        assertEquals(ConflictType.UNKNOWN_MEDIA, operation.conflicts.single().type)
    }

    @Test
    fun `case only collision is detected on case insensitive destinations`() {
        val movie = file("Arrival.mkv")

        val operation = planner.plan(
            files = listOf(movie),
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = setOf("movies/arrival/arrival.mkv"),
            sourceAndDestinationAreSame = true,
        ).operations.single()

        assertEquals(OperationStatus.CONFLICT, operation.status)
        assertTrue(operation.conflicts.any { it.type == ConflictType.CASE_ONLY_COLLISION })
    }

    @Test
    fun `duplicate subtitles receive a companion conflict`() {
        val files = listOf(
            file("Lost.S01E01.mkv"),
            file("Lost.S01E01.en.srt", uriSuffix = "subtitle-a"),
            file("Lost - 1x01.en.srt", uriSuffix = "subtitle-b"),
        )

        val plan = planner.plan(
            files = files,
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = emptySet(),
            sourceAndDestinationAreSame = true,
        )

        val subtitleConflicts = plan.operations
            .filter { it.mediaType == MediaFileType.SUBTITLE }
            .flatMap(PlannedFileOperation::conflicts)
        assertTrue(subtitleConflicts.any { it.type == ConflictType.DUPLICATE_COMPANION })
    }

    @Test
    fun `conflict resolver can safely skip or rename a planned item`() {
        val movie = file("Arrival.mkv")
        val plan = planner.plan(
            files = listOf(movie),
            destinationTreeUri = "content://test/root",
            existingDestinationPaths = setOf("Movies/Arrival/Arrival.mkv"),
            sourceAndDestinationAreSame = true,
        )
        val resolver = PlanConflictResolver()

        val renamed = resolver.rename(
            plan = plan,
            sourceUri = movie.sourceUri,
            alternativeName = "Arrival (Alternative).mkv",
        )
        assertEquals(OperationStatus.READY, renamed.operations.single().status)
        assertEquals("Movies/Arrival/Arrival (Alternative).mkv", renamed.operations.single().destinationPath)

        val skipped = resolver.skip(plan, movie.sourceUri)
        assertEquals(OperationStatus.SKIPPED, skipped.operations.single().status)
        assertTrue(skipped.operations.single().conflicts.isEmpty())
    }

    private fun file(
        name: String,
        uriSuffix: String = name,
        parentPath: String = "",
    ): ScannedFile {
        val type = MediaFileClassifier.classify(name)
        return ScannedFile.from(
            sourceUri = "content://test/$uriSuffix",
            parentUri = "content://test/root",
            parentPath = parentPath,
            fileName = name,
            depth = 0,
            size = 1,
            fileType = type,
        )
    }
}
