/** mobile/app/src/main/java/com/kp/filemanager/domain/MediaNameParser.kt: Compiles and applies deterministic release-name parsing once per indexed media item; optimized for the indexed pipeline. */
package com.kp.filemanager.domain

import java.util.Locale

fun interface MediaMetadataParser {
    fun parse(file: ScannedFile): ParsedMediaName
}

class MediaNameParser : MediaMetadataParser {
    override fun parse(file: ScannedFile): ParsedMediaName {
        val parserText = preprocess(file.baseName)
        val strongEpisode = findStrongEpisode(parserText)
        val weakEpisode = if (strongEpisode == null) findWeakEpisode(parserText) else null
        val folderContext = parseFolderContext(file.parentPath)
        val episodeMatch = strongEpisode ?: weakEpisode
        val season = episodeMatch?.season ?: if (weakEpisode != null) folderContext.season else null
        val episode = episodeMatch?.episode
        val yearMatch = YEAR_PATTERN.find(parserText)
        val year = yearMatch?.value?.toIntOrNull()
        val subtitleMetadata = parseSubtitleMetadata(parserText, file.fileType)
        val title = extractTitle(
            parserText = parserText,
            structuralRange = episodeMatch?.range,
            yearRange = yearMatch?.range,
            folderTitle = folderContext.seriesTitle.takeIf { weakEpisode != null },
        )
        val confidence = confidenceFor(
            file = file,
            title = title,
            strongEpisode = strongEpisode,
            weakEpisode = weakEpisode,
            folderContext = folderContext,
            year = year,
        )

        return ParsedMediaName(
            originalName = file.fileName,
            normalizedTitle = title,
            year = year,
            season = season,
            episode = episode,
            episodeEnd = episodeMatch?.episodeEnd,
            part = PART_PATTERN.find(parserText)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            language = subtitleMetadata.language,
            subtitleQualifiers = subtitleMetadata.qualifiers,
            edition = EDITION_PATTERN.find(parserText)?.value?.lowercase(Locale.ROOT),
            extension = file.extension,
            confidence = confidence,
            evidence = buildEvidence(strongEpisode, weakEpisode, folderContext, year),
        )
    }

    private fun preprocess(baseName: String): String = baseName
        .replace(SEPARATOR_PATTERN, " ")
        .replace(BRACKET_PATTERN, " $1 ")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

    private fun findStrongEpisode(text: String): EpisodeMatch? {
        STANDARD_EPISODE_PATTERN.find(text)?.let { match ->
            return EpisodeMatch(
                season = match.groupValues[1].toInt(),
                episode = match.groupValues[2].toInt(),
                episodeEnd = match.groupValues.getOrNull(3)?.takeIf(String::isNotBlank)?.toInt(),
                range = match.range,
            )
        }
        X_EPISODE_PATTERN.find(text)?.let { match ->
            return EpisodeMatch(
                season = match.groupValues[1].toInt(),
                episode = match.groupValues[2].toInt(),
                episodeEnd = null,
                range = match.range,
            )
        }
        VERBOSE_EPISODE_PATTERN.find(text)?.let { match ->
            return EpisodeMatch(
                season = match.groupValues[1].toInt(),
                episode = match.groupValues[2].toInt(),
                episodeEnd = null,
                range = match.range,
            )
        }
        return null
    }

    private fun findWeakEpisode(text: String): EpisodeMatch? =
        WEAK_EPISODE_PATTERN.find(text)?.let { match ->
            EpisodeMatch(
                season = null,
                episode = match.groupValues[1].toInt(),
                episodeEnd = null,
                range = match.range,
            )
        }

    private fun parseFolderContext(parentPath: String): FolderContext {
        val segments = parentPath.split('/').filter(String::isNotBlank)
        for (index in segments.indices.reversed()) {
            val season = SEASON_FOLDER_PATTERN.matchEntire(segments[index].trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: continue
            val series = segments.subList(0, index)
                .asReversed()
                .firstOrNull { it.lowercase(Locale.ROOT) !in GENERIC_FOLDER_NAMES }
                ?.let(::normalizeTitle)
            return FolderContext(season, series)
        }
        return FolderContext(null, null)
    }

    private fun parseSubtitleMetadata(text: String, fileType: MediaFileType): SubtitleMetadata {
        if (fileType != MediaFileType.SUBTITLE) return SubtitleMetadata(null, emptySet())
        val tokens = text.split(' ')
        val language = tokens.asReversed()
            .firstOrNull { it.lowercase(Locale.ROOT) in LANGUAGE_CODES }
            ?.lowercase(Locale.ROOT)
        val qualifiers = tokens.mapNotNull { token ->
            val normalized = token.lowercase(Locale.ROOT)
            SubtitleQualifier.entries.firstOrNull { it.suffix == normalized }
        }.toSet()
        return SubtitleMetadata(language, qualifiers)
    }

    private fun extractTitle(
        parserText: String,
        structuralRange: IntRange?,
        yearRange: IntRange?,
        folderTitle: String?,
    ): String {
        if (!folderTitle.isNullOrBlank()) return folderTitle
        val releaseRange = RELEASE_METADATA_PATTERN.find(parserText)?.range
        val firstMetadataIndex = listOfNotNull(
            structuralRange?.first,
            yearRange?.first,
            releaseRange?.first,
        ).minOrNull() ?: parserText.length
        var titleText = parserText.substring(0, firstMetadataIndex)
        titleText = removeTrailingSubtitleMetadata(titleText)
        if (titleText.isBlank() && structuralRange != null) {
            titleText = parserText.substring(0, structuralRange.first)
        }
        return normalizeTitle(titleText)
    }

    private fun removeTrailingSubtitleMetadata(value: String): String {
        val tokens = value.split(' ').toMutableList()
        while (tokens.lastOrNull()?.lowercase(Locale.ROOT) in SUBTITLE_SUFFIXES) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.joinToString(" ")
    }

    private fun normalizeTitle(value: String): String {
        val trimmed = value
            .replace(RELEASE_METADATA_PATTERN, " ")
            .replace(WHITESPACE_PATTERN, " ")
            .trim(' ', '-', '_', '.', '(', ')', '[', ']')
        return trimmed.split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { token ->
                when (token.uppercase(Locale.ROOT)) {
                    "US", "UK", "TV" -> token.uppercase(Locale.ROOT)
                    else -> token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
            }
    }

    private fun confidenceFor(
        file: ScannedFile,
        title: String,
        strongEpisode: EpisodeMatch?,
        weakEpisode: EpisodeMatch?,
        folderContext: FolderContext,
        year: Int?,
    ): Confidence = when {
        title.isBlank() -> Confidence.UNKNOWN
        strongEpisode != null -> Confidence.HIGH
        weakEpisode != null && folderContext.season != null && folderContext.seriesTitle != null -> Confidence.MEDIUM
        weakEpisode != null -> Confidence.LOW
        file.fileType == MediaFileType.VIDEO && year != null -> Confidence.HIGH
        GENERIC_VIDEO_PATTERN.matches(title.replace(" ", "")) -> Confidence.UNKNOWN
        file.fileType in setOf(MediaFileType.VIDEO, MediaFileType.SUBTITLE, MediaFileType.ARTWORK, MediaFileType.METADATA) ->
            Confidence.MEDIUM
        else -> Confidence.UNKNOWN
    }

    private fun buildEvidence(
        strongEpisode: EpisodeMatch?,
        weakEpisode: EpisodeMatch?,
        folderContext: FolderContext,
        year: Int?,
    ): List<String> = buildList {
        if (strongEpisode != null) add("season and episode token")
        if (weakEpisode != null) add("episode-only token")
        if (folderContext.season != null) add("season folder")
        if (folderContext.seriesTitle != null) add("series folder")
        if (year != null) add("year token")
    }

    private data class EpisodeMatch(
        val season: Int?,
        val episode: Int,
        val episodeEnd: Int?,
        val range: IntRange,
    )

    private data class FolderContext(
        val season: Int?,
        val seriesTitle: String?,
    )

    private data class SubtitleMetadata(
        val language: String?,
        val qualifiers: Set<SubtitleQualifier>,
    )

    companion object {
        private val SEPARATOR_PATTERN = Regex("[._-]+")
        private val BRACKET_PATTERN = Regex("[\\[(]([^\\])]+)[\\])]")
        private val WHITESPACE_PATTERN = Regex("\\s+")
        private val YEAR_PATTERN = Regex("(?<!\\d)(?:19\\d{2}|20\\d{2})(?!\\d)")
        private val STANDARD_EPISODE_PATTERN =
            Regex("(?i)(?<![A-Za-z0-9])S(\\d{1,2})\\s*E\\s*(\\d{1,3})(?:\\s*(?:E\\s*)?(\\d{1,3}))?(?![A-Za-z0-9])")
        private val X_EPISODE_PATTERN = Regex("(?i)(?<!\\d)(\\d{1,2})\\s*x\\s*(\\d{1,3})(?!\\d)")
        private val VERBOSE_EPISODE_PATTERN =
            Regex("(?i)\\bSeason\\s*(\\d{1,2})\\s*Episode\\s*(\\d{1,3})\\b")
        private val WEAK_EPISODE_PATTERN = Regex("(?i)(?:\\bEpisode\\s*|(?<![A-Za-z])\\bE\\s*)(\\d{1,3})\\b")
        private val SEASON_FOLDER_PATTERN = Regex("(?i)(?:Season\\s*|S)(\\d{1,2})")
        private val PART_PATTERN = Regex("(?i)\\bPart\\s*(\\d{1,2})\\b")
        private val EDITION_PATTERN = Regex("(?i)\\b(?:extended|director'?s cut|theatrical)\\b")
        private val RELEASE_METADATA_PATTERN = Regex(
            "(?i)\\b(?:480p|720p|1080p|2160p|4k|web\\s*dl|webrip|bluray|bdrip|hdrip|dvdrip|" +
                "remux|x264|x265|h264|h265|hevc|aac|ac3|eac3|dts|atmos|hdr10?|repack|proper|extended)\\b.*$",
        )
        private val GENERIC_VIDEO_PATTERN = Regex("(?i)(?:video|movie|file|clip)\\d+")
        private val LANGUAGE_CODES = setOf(
            "ar", "ara", "da", "dan", "de", "deu", "en", "eng", "es", "spa", "fi", "fin",
            "fr", "fra", "it", "ita", "ja", "jpn", "ko", "kor", "nl", "nld", "no", "nor",
            "pl", "pol", "pt", "por", "ru", "rus", "sv", "swe", "tr", "tur", "zh", "zho",
        )
        private val SUBTITLE_SUFFIXES = LANGUAGE_CODES + SubtitleQualifier.entries.map(SubtitleQualifier::suffix)
        private val GENERIC_FOLDER_NAMES = setOf(
            "downloads", "download", "incoming", "media", "movies", "tv", "tv shows", "random",
        )
    }
}
