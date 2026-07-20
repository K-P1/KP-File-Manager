/** mobile/app/src/test/java/com/kp/filemanager/domain/OperationPlannerTest.kt: Defines mobile feature parity before implementation; created test-first. */
package com.kp.filemanager.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationPlannerTest {
    private val planner = OperationPlanner()

    @Test
    fun `sort assigns known and unknown file categories`() {
        val files = listOf(
            candidate("photo.JPG"),
            candidate("notes.unknown"),
        )

        val changes = planner.planSort(files, seriesMode = false)

        assertEquals("Images/photo.JPG", changes[0].destinationPath)
        assertEquals("Others/notes.unknown", changes[1].destinationPath)
    }

    @Test
    fun `series mode groups matching normalized titles and leaves singletons alone`() {
        val files = listOf(
            candidate("The.Bear.S03E01.1080p.mkv"),
            candidate("The Bear S03E02 1080p.mkv"),
            candidate("Movie.2025.mkv"),
        )

        val changes = planner.planSort(files, seriesMode = true)

        assertEquals(2, changes.size)
        assertTrue(changes.all { it.destinationPath.startsWith("The Bear/") })
    }

    @Test
    fun `mass rename preserves original extensions when filter is wildcard`() {
        val files = listOf(candidate("b.png"), candidate("a.jpg"))

        val changes = planner.planMassRename(
            candidates = files,
            extensionFilter = "*",
            prefix = "Holiday_",
            startIndex = 7,
            zeroPadding = 3,
            scramble = false,
            scrambleMultiplier = 1,
        )

        assertEquals(listOf("Holiday_007.jpg", "Holiday_008.png"), changes.map { it.targetName })
    }

    @Test
    fun `scramble produces unique values inside the configured range`() {
        val files = (1..8).map { candidate("$it.mp4") }

        val changes = planner.planMassRename(
            candidates = files,
            extensionFilter = "mp4",
            prefix = "Clip_",
            startIndex = 1,
            zeroPadding = 0,
            scramble = true,
            scrambleMultiplier = 2,
            random = Random(10),
        )

        val values = changes.map { it.targetName.removePrefix("Clip_").removeSuffix(".mp4").toInt() }
        assertEquals(values.size, values.toSet().size)
        assertTrue(values.all { it in 1..16 })
    }

    @Test
    fun `keyword rename replaces each matching occurrence`() {
        val changes = planner.planKeywordRename(
            candidates = listOf(candidate("house house s01e01.mp4")),
            extensionFilter = "mp4",
            keyword = "house",
            replacement = "Home",
        )

        assertEquals("Home Home s01e01.mp4", changes.single().targetName)
    }

    @Test
    fun `music rename uses track artist and title metadata`() {
        val changes = planner.planMusicRename(
            listOf(candidate("raw_file.mp3", track = "3/12", artist = "Little Simz", title = "Introvert")),
        )

        assertEquals("03. Little Simz - Introvert.mp3", changes.single().targetName)
    }

    private fun candidate(
        name: String,
        track: String? = null,
        artist: String? = null,
        title: String? = null,
    ) = FileCandidate(
        uri = "content://test/$name",
        parentUri = "content://test/root",
        name = name,
        isDirectory = false,
        descendantExtensions = emptyList(),
        metadata = AudioMetadata(track, artist, title),
    )
}
