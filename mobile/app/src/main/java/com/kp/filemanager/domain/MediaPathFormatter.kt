/** mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt: Centralizes safe context-aware movie, TV, subtitle, artwork, and metadata destinations; optimized to avoid redundant library nesting. */
package com.kp.filemanager.domain

class MediaPathFormatter {
    fun destinationPath(
        group: OrganizationGroup,
        groupedFile: GroupedFile,
        movieFolderName: String = "Movies",
        tvFolderName: String = "TV Shows",
        libraryContext: LibraryContext = LibraryContext(
            LibraryContextType.GENERIC_SOURCE,
            selectedRootName = "",
        ),
    ): String {
        val item = group.mediaItem
        val extension = groupedFile.file.extension
        val suffix = subtitleSuffix(groupedFile.parsedName)
        return when (item) {
            is MediaItem.Movie -> {
                val baseName = movieBaseName(item)
                val fileName = when (groupedFile.file.fileType) {
                    MediaFileType.VIDEO -> "$baseName.$extension"
                    MediaFileType.SUBTITLE -> "$baseName$suffix.$extension"
                    MediaFileType.ARTWORK -> "Poster.$extension"
                    MediaFileType.METADATA -> "Movie.nfo"
                    MediaFileType.UNKNOWN -> groupedFile.file.fileName
                }
                val directory = when (libraryContext.type) {
                    LibraryContextType.MOVIES_LIBRARY_ROOT -> sanitizeSegment(baseName)
                    LibraryContextType.MOVIE_FOLDER -> ""
                    else -> "${sanitizeSegment(movieFolderName)}/${sanitizeSegment(baseName)}"
                }
                listOf(directory, sanitizeSegment(fileName))
                    .filter(String::isNotBlank)
                    .joinToString("/")
            }
            is MediaItem.TvEpisode -> {
                val seriesTitle = sanitizeSegment(item.title)
                val episodeBase = buildString {
                    append(seriesTitle)
                    append(" - S")
                    append(item.season.toString().padStart(2, '0'))
                    append("E")
                    append(item.episode.toString().padStart(2, '0'))
                    item.episodeEnd?.let {
                        append("-E")
                        append(it.toString().padStart(2, '0'))
                    }
                }
                val fileName = when (groupedFile.file.fileType) {
                    MediaFileType.VIDEO -> "$episodeBase.$extension"
                    MediaFileType.SUBTITLE -> "$episodeBase$suffix.$extension"
                    else -> groupedFile.file.fileName
                }
                val seasonDirectory = "Season ${item.season.toString().padStart(2, '0')}"
                val directory = when (libraryContext.type) {
                    LibraryContextType.TV_LIBRARY_ROOT -> "$seriesTitle/$seasonDirectory"
                    LibraryContextType.SERIES_FOLDER -> {
                        if (libraryContext.selectedRootIsSeason) "" else seasonDirectory
                    }
                    else -> "${sanitizeSegment(tvFolderName)}/$seriesTitle/$seasonDirectory"
                }
                listOf(directory, sanitizeSegment(fileName))
                    .filter(String::isNotBlank)
                    .joinToString("/")
            }
        }
    }

    fun sanitizeSegment(value: String): String = value
        .replace(INVALID_FILENAME_CHARS, "_")
        .replace(CONTROL_CHARS, "_")
        .replace(WHITESPACE, " ")
        .trim()
        .trimEnd('.', ' ')
        .ifEmpty { "Untitled" }

    private fun movieBaseName(movie: MediaItem.Movie): String =
        movie.year?.let { "${sanitizeSegment(movie.title)} ($it)" } ?: sanitizeSegment(movie.title)

    private fun subtitleSuffix(parsed: ParsedMediaName): String {
        val parts = buildList {
            parsed.language?.let(::add)
            SubtitleQualifier.entries
                .filter { it in parsed.subtitleQualifiers }
                .mapTo(this, SubtitleQualifier::suffix)
        }
        return parts.joinToString(separator = ".", prefix = if (parts.isEmpty()) "" else ".")
    }

    private companion object {
        val INVALID_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")
        val CONTROL_CHARS = Regex("[\\u0000-\\u001f\\u007f]")
        val WHITESPACE = Regex("\\s+")
    }
}
