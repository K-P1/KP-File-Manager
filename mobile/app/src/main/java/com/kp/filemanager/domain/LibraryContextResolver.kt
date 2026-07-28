/** mobile/app/src/main/java/com/kp/filemanager/domain/LibraryContextResolver.kt: Resolves selected Movies, TV, movie, series, season, generic, and ambiguous library contexts from one media index; added for context-aware planning. */
package com.kp.filemanager.domain

import java.util.Locale

class LibraryContextResolver {
    fun resolve(
        index: MediaIndex,
        movieFolderName: String = "Movies",
        tvFolderName: String = "TV Shows",
    ): LibraryContext {
        val rootName = index.root.name
        val normalizedRoot = normalize(rootName)
        val movieAnchor = normalize(movieFolderName)
        val tvAnchors = setOf(normalize(tvFolderName), "tv", "television", "series")
        val pathSegments = index.root.documentId
            .substringAfter(':', index.root.documentId)
            .split('/', '\\')
            .map(::normalize)
            .filter(String::isNotBlank)
        val belowMoviesLibrary = pathSegments.dropLast(1).any { it == movieAnchor }
        val belowTvLibrary = pathSegments.dropLast(1).any { it in tvAnchors }
        val videos = index.items.filter { it.file.fileType == MediaFileType.VIDEO }
        val episodes = videos.filter { it.parsedName.season != null && it.parsedName.episode != null }
        val movies = videos - episodes.toSet()
        val selectedSeason = SEASON_PATTERN.matches(rootName.trim())

        if (normalizedRoot == movieAnchor) {
            return LibraryContext(
                LibraryContextType.MOVIES_LIBRARY_ROOT,
                rootName,
                reason = "The selected folder matches the configured Movies library name.",
            )
        }
        if (normalizedRoot in tvAnchors) {
            return LibraryContext(
                LibraryContextType.TV_LIBRARY_ROOT,
                rootName,
                reason = "The selected folder matches a configured TV or Series library name.",
            )
        }
        if (episodes.isNotEmpty() && movies.isNotEmpty()) {
            return LibraryContext(
                LibraryContextType.AMBIGUOUS,
                rootName,
                reason = "The selected folder mixes movie and series-root signals.",
            )
        }
        if (episodes.isNotEmpty()) {
            val episodeTitles = episodes.groupingBy { normalize(it.parsedName.normalizedTitle) }.eachCount()
            val hasSeasonDirectories = index.directories.any { SEASON_PATTERN.matches(it.name.trim()) }
            val rootMatchesSeries = episodeTitles.keys.any { title ->
                title.isNotBlank() && (title == normalizedRoot || normalizedRoot.startsWith("$title "))
            }
            if (selectedSeason || hasSeasonDirectories || rootMatchesSeries || episodeTitles.values.any { it > 1 }) {
                return LibraryContext(
                    LibraryContextType.SERIES_FOLDER,
                    rootName,
                    selectedRootIsSeason = selectedSeason,
                    reason = "Episode names and season structure identify the selected series context.",
                )
            }
            if (belowTvLibrary) {
                return LibraryContext(
                    LibraryContextType.TV_LIBRARY_ROOT,
                    rootName,
                    reason = "The provider path places the selection below the configured TV library.",
                )
            }
        }
        if (normalizedRoot in GENERIC_SOURCE_NAMES) {
            return LibraryContext(
                LibraryContextType.GENERIC_SOURCE,
                rootName,
                reason = "The selected folder is a generic incoming-media location.",
            )
        }
        if (movies.size == 1) {
            val parsed = movies.single().parsedName
            val normalizedTitle = normalize(parsed.normalizedTitle)
            if (
                normalizedTitle.isNotBlank() &&
                (normalizedRoot == normalizedTitle || normalizedRoot.startsWith("$normalizedTitle "))
            ) {
                return LibraryContext(
                    LibraryContextType.MOVIE_FOLDER,
                    rootName,
                    reason = "The selected folder name and contents identify one movie.",
                )
            }
        }
        if (movies.isNotEmpty() && belowMoviesLibrary) {
            return LibraryContext(
                LibraryContextType.MOVIES_LIBRARY_ROOT,
                rootName,
                reason = "The provider path places the selection below the configured Movies library.",
            )
        }
        if (episodes.isNotEmpty() && movies.isEmpty()) {
            return LibraryContext(
                LibraryContextType.GENERIC_SOURCE,
                rootName,
                reason = "Episodes were detected without enough evidence that the selected folder is their series root.",
            )
        }
        return LibraryContext(
            LibraryContextType.GENERIC_SOURCE,
            rootName,
            reason = "No established library structure was detected.",
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(SEPARATORS, " ")
        .replace(YEAR_SUFFIX, "")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        val SEASON_PATTERN = Regex("(?i)(?:Season\\s*|S)\\d{1,2}")
        val SEPARATORS = Regex("[._-]+")
        val YEAR_SUFFIX = Regex("\\s*\\(?((?:19|20)\\d{2})\\)?\\s*$")
        val WHITESPACE = Regex("\\s+")
        val GENERIC_SOURCE_NAMES = setOf("downloads", "download", "incoming", "random", "unsorted")
    }
}
