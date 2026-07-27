/** mobile/app/src/test/java/com/kp/filemanager/domain/MediaNameParserTest.kt: Verifies deterministic movie, episode, subtitle, and folder-context parsing; created for the media refactor. */
package com.kp.filemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaNameParserTest {
    private val parser = MediaNameParser()

    @Test
    fun `parses supported tv release patterns and removes release metadata`() {
        val cases = listOf(
            "Lost.S01E01.1080p.x264.mkv" to Triple("Lost", 1, 1),
            "Lost - 1x02.mp4" to Triple("Lost", 1, 2),
            "Lost Season 1 Episode 03.mkv" to Triple("Lost", 1, 3),
            "LOST_s01_e04.mp4" to Triple("Lost", 1, 4),
            "The.Office.US.S03E05.1080p.WEBRip.x265-GROUP.mkv" to Triple("The Office US", 3, 5),
        )

        cases.forEach { (name, expected) ->
            val parsed = parser.parse(file(name))

            assertEquals(name, expected.first, parsed.normalizedTitle)
            assertEquals(name, expected.second, parsed.season)
            assertEquals(name, expected.third, parsed.episode)
            assertEquals(name, Confidence.HIGH, parsed.confidence)
        }
    }

    @Test
    fun `preserves show year and multi episode range`() {
        val parsed = parser.parse(file("Lost (2004) S01E01-E02 WEB-DL.mkv"))

        assertEquals("Lost", parsed.normalizedTitle)
        assertEquals(2004, parsed.year)
        assertEquals(1, parsed.season)
        assertEquals(1, parsed.episode)
        assertEquals(2, parsed.episodeEnd)
    }

    @Test
    fun `parses all supported multi episode spellings`() {
        val names = listOf(
            "Lost.S01E01E02.mkv",
            "Lost.S01E01-E02.mkv",
            "Lost.S01E01-02.mkv",
        )

        names.forEach { name ->
            val parsed = parser.parse(file(name))

            assertEquals(name, 1, parsed.season)
            assertEquals(name, 1, parsed.episode)
            assertEquals(name, 2, parsed.episodeEnd)
        }
    }

    @Test
    fun `parses compact and separated season episode tokens`() {
        val names = listOf("Lost.S1E1.mkv", "Lost.S01 E01.mkv", "Lost.S01_E01.mkv")

        names.forEach { name ->
            val parsed = parser.parse(file(name))

            assertEquals(name, 1, parsed.season)
            assertEquals(name, 1, parsed.episode)
        }
    }

    @Test
    fun `parses movie titles with optional years`() {
        val cases = listOf(
            "Avatar (2009).mp4" to ("Avatar" to 2009),
            "Avatar.2009.1080p.x265.mkv" to ("Avatar" to 2009),
            "Interstellar.2014.BluRay.mkv" to ("Interstellar" to 2014),
            "Arrival.mkv" to ("Arrival" to null),
        )

        cases.forEach { (name, expected) ->
            val parsed = parser.parse(file(name))

            assertEquals(name, expected.first, parsed.normalizedTitle)
            assertEquals(name, expected.second, parsed.year)
            assertNull(name, parsed.season)
            assertNull(name, parsed.episode)
        }
    }

    @Test
    fun `equivalent separators resolve to the same identity`() {
        val names = listOf(
            "the.office.us.s03e05.mkv",
            "the-office-us-s03e05.mkv",
            "the_office_us_s03e05.mkv",
            "the office us s03e05.mkv",
        )

        val identities = names.map { parser.parse(file(it)) }
            .map { Triple(it.normalizedTitle, it.season, it.episode) }

        assertEquals(1, identities.distinct().size)
        assertEquals(Triple("The Office US", 3, 5), identities.first())
    }

    @Test
    fun `extracts subtitle language and qualifiers without adding them to title`() {
        val parsed = parser.parse(file("Lost.S01E01.en.forced.srt", MediaFileType.SUBTITLE))

        assertEquals("Lost", parsed.normalizedTitle)
        assertEquals("en", parsed.language)
        assertEquals(setOf(SubtitleQualifier.FORCED), parsed.subtitleQualifiers)
    }

    @Test
    fun `uses season and series folder context for episode only filename`() {
        val parsed = parser.parse(
            file(
                name = "Episode 03.mkv",
                parentPath = "Downloads/Random/Lost/Season 02",
            ),
        )

        assertEquals("Lost", parsed.normalizedTitle)
        assertEquals(2, parsed.season)
        assertEquals(3, parsed.episode)
        assertEquals(Confidence.MEDIUM, parsed.confidence)
    }

    @Test
    fun `episode only evidence without season context remains low confidence`() {
        val parsed = parser.parse(file("Lost Episode 05.mkv"))

        assertEquals("Lost", parsed.normalizedTitle)
        assertNull(parsed.season)
        assertEquals(5, parsed.episode)
        assertEquals(Confidence.LOW, parsed.confidence)
    }

    private fun file(
        name: String,
        type: MediaFileType = MediaFileType.VIDEO,
        parentPath: String = "",
    ): ScannedFile = ScannedFile.from(
        sourceUri = "content://test/$name",
        parentUri = "content://test/root",
        parentPath = parentPath,
        fileName = name,
        depth = parentPath.count { it == '/' },
        size = 1_024,
        fileType = type,
    )
}
