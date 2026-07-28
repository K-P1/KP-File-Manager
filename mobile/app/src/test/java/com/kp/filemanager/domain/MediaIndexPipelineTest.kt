/** mobile/app/src/test/java/com/kp/filemanager/domain/MediaIndexPipelineTest.kt: Verifies one-pass indexing, context-aware planning, and parsed metadata reuse; added for media workflow optimization. */
package com.kp.filemanager.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIndexPipelineTest {
    @Test
    fun `scanner parses each discovered item once and grouping reuses the index`() = runBlocking {
        val parser = CountingParser()
        val source = object : DocumentTreeSource {
            var childQueries = 0

            override fun root(treeUri: String) = directory(treeUri, "", "Downloads")

            override fun children(directoryUri: String): List<DocumentEntry> {
                childQueries += 1
                return listOf(
                    file("movie", directoryUri, "Arrival.2016.mkv"),
                    file("subtitle", directoryUri, "Arrival.2016.en.srt"),
                )
            }
        }

        val result = DirectoryScanner(source, parser).scan("root")
        val grouping = MediaGrouper().group(result.index)

        assertEquals(1, source.childQueries)
        assertEquals(2, parser.parseCount)
        assertEquals(1, grouping.groups.size)
        assertEquals(2, parser.parseCount)
    }

    @Test
    fun `resolver recognizes library series season generic movie and ambiguous selections`() {
        val resolver = LibraryContextResolver()

        assertEquals(
            LibraryContextType.TV_LIBRARY_ROOT,
            resolver.resolve(index("TV Shows", listOf("Lost.S01E01.mkv"))).type,
        )
        assertEquals(
            LibraryContextType.SERIES_FOLDER,
            resolver.resolve(
                index(
                    "Game of Thrones",
                    listOf("Season 01/Game.of.Thrones.S01E01.mkv", "Season 01/Game.of.Thrones.S01E02.mkv"),
                    directories = listOf("Season 01"),
                ),
            ).type,
        )
        assertEquals(
            LibraryContextType.SERIES_FOLDER,
            resolver.resolve(index("Season 01", listOf("Game.of.Thrones.S01E01.mkv"))).type,
        )
        assertEquals(
            LibraryContextType.GENERIC_SOURCE,
            resolver.resolve(index("Downloads", listOf("Arrival.2016.mkv"))).type,
        )
        assertEquals(
            LibraryContextType.MOVIE_FOLDER,
            resolver.resolve(index("Arrival (2016)", listOf("Arrival.2016.mkv", "Arrival.2016.en.srt"))).type,
        )
        assertEquals(
            LibraryContextType.AMBIGUOUS,
            resolver.resolve(index("Media", listOf("Arrival.2016.mkv", "Lost.S01E01.mkv"))).type,
        )
        assertEquals(
            LibraryContextType.TV_LIBRARY_ROOT,
            resolver.resolve(
                index(
                    "Incoming",
                    listOf("Lost.S01E01.mkv"),
                    rootDocumentId = "primary:TV Shows/Incoming",
                ),
            ).type,
        )
    }

    @Test
    fun `selected tv library and series roots do not duplicate destination folders`() {
        val planner = MediaOrganizationPlanner()
        val tvRootIndex = index("TV Shows", listOf("Lost.S01E01.mkv"))
        val tvRootPlan = planner.plan(
            index = tvRootIndex,
            destinationTreeUri = "root",
            existingDestinationPaths = tvRootIndex.files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
            libraryContext = LibraryContextResolver().resolve(tvRootIndex),
        )
        assertEquals("Lost/Season 01/Lost - S01E01.mkv", tvRootPlan.operations.single().destinationPath)

        val seriesIndex = index(
            "Game of Thrones",
            listOf("Game.of.Thrones.S01E01.mkv"),
        )
        val seriesPlan = planner.plan(
            index = seriesIndex,
            destinationTreeUri = "series-root",
            existingDestinationPaths = seriesIndex.files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
            libraryContext = LibraryContextResolver().resolve(seriesIndex),
        )
        assertEquals("Season 01/Game Of Thrones - S01E01.mkv", seriesPlan.operations.single().destinationPath)
        assertFalse(seriesPlan.operations.single().destinationPath.contains("TV Shows"))
    }

    @Test
    fun `movie and season roots retain their selected destination root`() {
        val planner = MediaOrganizationPlanner()
        val movieIndex = index("Arrival (2016)", listOf("Arrival.2016.mkv"))
        val moviePlan = planner.plan(
            index = movieIndex,
            destinationTreeUri = "movie-root",
            existingDestinationPaths = movieIndex.files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
            libraryContext = LibraryContextResolver().resolve(movieIndex),
        )
        assertEquals("Arrival (2016).mkv", moviePlan.operations.single().destinationPath)

        val seasonIndex = index("Season 01", listOf("Lost.S01E01.mkv"))
        val seasonPlan = planner.plan(
            index = seasonIndex,
            destinationTreeUri = "season-root",
            existingDestinationPaths = seasonIndex.files.mapTo(mutableSetOf(), ScannedFile::sourcePath),
            sourceAndDestinationAreSame = true,
            libraryContext = LibraryContextResolver().resolve(seasonIndex),
        )
        assertEquals("Lost - S01E01.mkv", seasonPlan.operations.single().destinationPath)
        assertTrue(seasonPlan.operations.single().destinationDirectoryPath.isEmpty())
    }

    private class CountingParser : MediaMetadataParser {
        private val delegate = MediaNameParser()
        var parseCount = 0

        override fun parse(file: ScannedFile): ParsedMediaName {
            parseCount += 1
            return delegate.parse(file)
        }
    }

    private fun index(
        rootName: String,
        paths: List<String>,
        directories: List<String> = paths.mapNotNull { it.substringBeforeLast('/', "").takeIf(String::isNotBlank) },
        rootDocumentId: String = "",
    ): MediaIndex {
        val parser = MediaNameParser()
        val root = directory("root", "", rootName).copy(documentId = rootDocumentId)
        val indexedDirectories = directories.distinct().map { path ->
            IndexedDirectory(
                uri = "dir:$path",
                parentUri = "root",
                path = path,
                name = path.substringAfterLast('/'),
                depth = path.count { it == '/' } + 1,
            )
        }
        val items = paths.map { path ->
            val parentPath = path.substringBeforeLast('/', "")
            val scanned = ScannedFile.from(
                sourceUri = "file:$path",
                parentUri = indexedDirectories.firstOrNull { it.path == parentPath }?.uri ?: "root",
                parentPath = parentPath,
                fileName = path.substringAfterLast('/'),
                depth = parentPath.count { it == '/' },
                size = 100,
            )
            IndexedMediaFile(scanned, parser.parse(scanned))
        }
        return MediaIndex(root, indexedDirectories, items)
    }

    private companion object {
        fun directory(uri: String, parentUri: String, name: String) =
            DocumentEntry(uri, parentUri, name, isDirectory = true, size = 0)

        fun file(uri: String, parentUri: String, name: String) =
            DocumentEntry(uri, parentUri, name, isDirectory = false, size = 100)
    }
}
