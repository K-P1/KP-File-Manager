/** mobile/app/src/main/java/com/kp/filemanager/domain/MediaGrouper.kt: Groups videos and safely matches subtitles, artwork, and metadata by parsed identity; created for the media refactor. */
package com.kp.filemanager.domain

import java.util.Locale

class MediaGrouper(
    private val parser: MediaNameParser = MediaNameParser(),
) {
    fun group(files: List<ScannedFile>): GroupingResult {
        val parsedFiles = files.map { GroupedFile(it, parser.parse(it)) }
        val videoGroups = parsedFiles
            .filter { it.file.fileType == MediaFileType.VIDEO }
            .mapNotNull { grouped -> identityOf(grouped.parsedName)?.let { it to grouped } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, videos) ->
                MutableGroup(
                    mediaItem = mediaItemFor(videos.first().parsedName),
                    files = videos.toMutableList(),
                    warnings = mutableListOf<String>().apply {
                        if (videos.size > 1) add("Multiple video files have the same media identity.")
                    },
                )
            }
            .toMutableMap()

        val unmatched = parsedFiles
            .filter { it.file.fileType == MediaFileType.VIDEO && identityOf(it.parsedName) == null }
            .toMutableList()
        val ambiguous = linkedMapOf<GroupedFile, List<OrganizationGroup>>()
        parsedFiles.filter { it.file.fileType != MediaFileType.VIDEO }.forEach { companion ->
            if (companion.file.fileType == MediaFileType.UNKNOWN) {
                unmatched += companion
                return@forEach
            }
            val candidates = matchingGroups(companion, videoGroups)
            when (candidates.size) {
                0 -> unmatched += companion
                1 -> candidates.single().files += companion
                else -> ambiguous[companion] = candidates.map(MutableGroup::snapshot)
            }
        }

        return GroupingResult(
            groups = videoGroups.entries
                .sortedBy { it.key.sortKey }
                .map { it.value.snapshot() },
            unmatched = unmatched,
            ambiguous = ambiguous,
        )
    }

    private fun matchingGroups(
        companion: GroupedFile,
        groups: Map<MediaIdentity, MutableGroup>,
    ): List<MutableGroup> {
        identityOf(companion.parsedName)?.let { exact ->
            groups[exact]?.let { return listOf(it) }
        }

        val sameTitle = groups.entries
            .filter { (identity, _) ->
                identity.title == companion.parsedName.normalizedTitle.lowercase(Locale.ROOT) &&
                    (companion.parsedName.year == null || identity.year == companion.parsedName.year)
            }
            .map(Map.Entry<MediaIdentity, MutableGroup>::value)
        if (sameTitle.size == 1) return sameTitle
        if (sameTitle.size > 1) return sameTitle

        val sameDirectory = groups.values.filter { group ->
            group.files.any { it.file.parentPath.equals(companion.file.parentPath, ignoreCase = true) }
        }
        return when (companion.file.fileType) {
            MediaFileType.SUBTITLE -> sameDirectory
            MediaFileType.ARTWORK ->
                sameDirectory.takeIf { isRecognizedArtwork(companion.file.baseName) }.orEmpty()
            MediaFileType.METADATA -> sameDirectory
            else -> emptyList()
        }
    }

    private fun identityOf(parsed: ParsedMediaName): MediaIdentity? {
        val title = parsed.normalizedTitle.lowercase(Locale.ROOT).takeIf(String::isNotBlank) ?: return null
        if (parsed.episode != null && parsed.season == null) return null
        return if (parsed.season != null && parsed.episode != null) {
            MediaIdentity.Tv(title, parsed.year, parsed.season, parsed.episode, parsed.episodeEnd)
        } else if (parsed.confidence != Confidence.UNKNOWN) {
            MediaIdentity.Movie(title, parsed.year)
        } else {
            null
        }
    }

    private fun mediaItemFor(parsed: ParsedMediaName): MediaItem =
        if (parsed.season != null && parsed.episode != null) {
            MediaItem.TvEpisode(
                title = parsed.normalizedTitle,
                year = parsed.year,
                season = parsed.season,
                episode = parsed.episode,
                episodeEnd = parsed.episodeEnd,
                confidence = parsed.confidence,
            )
        } else {
            MediaItem.Movie(
                title = parsed.normalizedTitle,
                year = parsed.year,
                confidence = parsed.confidence,
            )
        }

    private fun isRecognizedArtwork(baseName: String): Boolean {
        val normalized = baseName.lowercase(Locale.ROOT)
        return ARTWORK_MARKERS.any(normalized::contains)
    }

    private sealed interface MediaIdentity {
        val title: String
        val year: Int?
        val sortKey: String

        data class Movie(
            override val title: String,
            override val year: Int?,
        ) : MediaIdentity {
            override val sortKey: String = "movie:$title:${year.orEmpty()}"
        }

        data class Tv(
            override val title: String,
            override val year: Int?,
            val season: Int,
            val episode: Int,
            val episodeEnd: Int?,
        ) : MediaIdentity {
            override val sortKey: String = "tv:$title:$season:$episode:${episodeEnd.orEmpty()}"
        }
    }

    private data class MutableGroup(
        val mediaItem: MediaItem,
        val files: MutableList<GroupedFile>,
        val warnings: MutableList<String>,
    ) {
        fun snapshot() = OrganizationGroup(mediaItem, files.toList(), warnings.toList())
    }

    private companion object {
        val ARTWORK_MARKERS = setOf("poster", "cover", "folder", "fanart", "backdrop")
        fun Int?.orEmpty(): String = this?.toString().orEmpty()
    }
}
