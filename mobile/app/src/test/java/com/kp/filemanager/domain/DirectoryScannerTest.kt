/** mobile/app/src/test/java/com/kp/filemanager/domain/DirectoryScannerTest.kt: Covers bounded recursive discovery, classification, and scan warnings; created for the media refactor. */
package com.kp.filemanager.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryScannerTest {
    @Test
    fun `scanner traverses recursively through configured depth and classifies files`() = runBlocking {
        val source = FakeTreeSource(
            mapOf(
                "root" to listOf(dir("shows", "Shows"), file("movie", "Avatar.2009.mkv", 10)),
                "shows" to listOf(dir("lost", "Lost"), file("notes", "notes.txt", 2)),
                "lost" to listOf(dir("season", "Season 01")),
                "season" to listOf(file("episode", "Lost.S01E01.mp4", 20), file("subtitle", "Lost.S01E01.en.srt", 3)),
            ),
        )

        val result = DirectoryScanner(source).scan("root", maximumDepth = 3)

        assertEquals(
            listOf("Avatar.2009.mkv", "notes.txt", "Lost.S01E01.en.srt", "Lost.S01E01.mp4"),
            result.files.map(ScannedFile::fileName),
        )
        assertEquals(
            listOf(MediaFileType.VIDEO, MediaFileType.UNKNOWN, MediaFileType.SUBTITLE, MediaFileType.VIDEO),
            result.files.map(ScannedFile::fileType),
        )
        assertEquals("Shows/Lost/Season 01", result.files.last().parentPath)
    }

    @Test
    fun `scanner does not descend beyond maximum depth`() = runBlocking {
        val source = FakeTreeSource(
            mapOf(
                "root" to listOf(dir("one", "One")),
                "one" to listOf(dir("two", "Two")),
                "two" to listOf(file("deep", "Deep.mkv", 1)),
            ),
        )

        val result = DirectoryScanner(source).scan("root", maximumDepth = 1)

        assertTrue(result.files.isEmpty())
        assertEquals(1, result.ignoredDirectoryCount)
    }

    @Test
    fun `scanner keeps accessible results when one directory fails`() = runBlocking {
        val source = FakeTreeSource(
            children = mapOf("root" to listOf(dir("denied", "Denied"), file("movie", "Arrival.mkv", 1))),
            failingDirectories = setOf("denied"),
        )

        val result = DirectoryScanner(source).scan("root", maximumDepth = 4)

        assertEquals(listOf("Arrival.mkv"), result.files.map(ScannedFile::fileName))
        assertEquals(1, result.warnings.size)
    }

    private class FakeTreeSource(
        private val children: Map<String, List<DocumentEntry>>,
        private val failingDirectories: Set<String> = emptySet(),
    ) : DocumentTreeSource {
        override fun root(treeUri: String) = DocumentEntry(treeUri, "", "Root", true, 0)

        override fun children(directoryUri: String): List<DocumentEntry> {
            if (directoryUri in failingDirectories) throw SecurityException("Denied")
            return children[directoryUri].orEmpty()
        }
    }

    private companion object {
        fun dir(uri: String, name: String) = DocumentEntry(uri, "parent", name, true, 0)
        fun file(uri: String, name: String, size: Long) = DocumentEntry(uri, "parent", name, false, size)
    }
}
