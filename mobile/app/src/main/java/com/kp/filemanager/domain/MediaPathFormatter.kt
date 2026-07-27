/** mobile/app/src/main/java/com/kp/filemanager/domain/MediaPathFormatter.kt: Centralizes safe movie, TV, subtitle, artwork, and metadata destination names; created for the media refactor. */
package com.kp.filemanager.domain

class MediaPathFormatter {
    fun destinationPath(
        group: OrganizationGroup,
        groupedFile: GroupedFile,
        movieFolderName: String = "Movies",
        tvFolderName: String = "TV Shows",
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
                "${sanitizeSegment(movieFolderName)}/${sanitizeSegment(baseName)}/${sanitizeSegment(fileName)}"
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
                "${sanitizeSegment(tvFolderName)}/$seriesTitle/Season " +
                    "${item.season.toString().padStart(2, '0')}/${sanitizeSegment(fileName)}"
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
