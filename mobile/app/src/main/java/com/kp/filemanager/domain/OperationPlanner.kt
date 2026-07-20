/** mobile/app/src/main/java/com/kp/filemanager/domain/OperationPlanner.kt: Produces deterministic, testable file-operation plans; created from desktop parity tests. */
package com.kp.filemanager.domain

import java.util.Locale
import kotlin.random.Random

class OperationPlanner(
    private val categories: Map<String, Set<String>> = DEFAULT_CATEGORIES,
) {
    fun planSort(candidates: List<FileCandidate>, seriesMode: Boolean): List<PlannedChange> {
        if (seriesMode) {
            return candidates
                .groupBy { extractSeriesTitle(it.name) }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size > 1 }
                .flatMap { (title, items) -> items.map { it.moveTo(title) } }
        }

        return candidates.map { candidate ->
            val category = if (candidate.isDirectory) {
                classifyFolder(candidate.descendantExtensions)
            } else {
                classifyExtension(extensionOf(candidate.name))
            }
            candidate.moveTo(category)
        }
    }

    @Suppress("LongParameterList")
    fun planMassRename(
        candidates: List<FileCandidate>,
        extensionFilter: String,
        prefix: String,
        startIndex: Int,
        zeroPadding: Int,
        scramble: Boolean,
        scrambleMultiplier: Int,
        random: Random = Random.Default,
    ): List<PlannedChange> {
        val matches = matchingFiles(candidates, extensionFilter)
        val numbers = if (scramble) {
            val limit = matches.size * scrambleMultiplier.coerceIn(1, 10)
            (1..limit).shuffled(random).take(matches.size)
        } else {
            (startIndex until startIndex + matches.size).toList()
        }

        return matches.zip(numbers).map { (candidate, number) ->
            val padded = number.toString().padStart(zeroPadding.coerceAtLeast(0), '0')
            candidate.renameTo(sanitizeFilename("$prefix$padded.${extensionOf(candidate.name)}"))
        }
    }

    fun planKeywordRename(
        candidates: List<FileCandidate>,
        extensionFilter: String,
        keyword: String,
        replacement: String,
    ): List<PlannedChange> {
        if (keyword.isEmpty()) return emptyList()
        return matchingFiles(candidates, extensionFilter)
            .filter { keyword in stemOf(it.name) }
            .map { candidate ->
                val newStem = stemOf(candidate.name).replace(keyword, replacement)
                candidate.renameTo(sanitizeFilename("$newStem.${extensionOf(candidate.name)}"))
            }
    }

    fun planMusicRename(candidates: List<FileCandidate>): List<PlannedChange> =
        matchingFiles(candidates, "mp3").map { candidate ->
            val metadata = candidate.metadata
            val track = metadata.track
                ?.substringBefore('/')
                ?.trim()
                ?.toIntOrNull()
                ?.toString()
                ?.padStart(2, '0')
            val artist = metadata.artist?.trim().orEmpty()
            val title = metadata.title?.trim().orEmpty()
            val baseName = if (artist.isNotEmpty() && title.isNotEmpty()) {
                listOfNotNull(track?.let { "$it." }, "$artist - $title").joinToString(" ")
            } else {
                cleanupStem(stemOf(candidate.name))
            }
            candidate.renameTo(sanitizeFilename("$baseName.mp3"))
        }

    fun extractSeriesTitle(name: String): String {
        val withoutExtension = stemOf(name).lowercase(Locale.ROOT)
        val normalized = withoutExtension
            .replace(Regex("[\\[({].*?[\\])}]") , " ")
            .replace(Regex("[\\[({].*"), " ")
            .replace(Regex("[.\\\\/_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return normalized
            .split(' ')
            .filterNot(::isNoiseToken)
            .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .trim()
    }

    private fun classifyFolder(extensions: List<String>): String {
        if (extensions.isEmpty()) return "EmptyFolders"
        val mostCommon = extensions
            .map { it.lowercase(Locale.ROOT).trimStart('.') }
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
        return classifyExtension(mostCommon)
    }

    private fun classifyExtension(extension: String): String = categories.entries
        .firstOrNull { extension.lowercase(Locale.ROOT) in it.value }
        ?.key
        ?: "Others"

    private fun matchingFiles(candidates: List<FileCandidate>, extensionFilter: String): List<FileCandidate> {
        val normalizedFilter = extensionFilter.trim().trimStart('.').lowercase(Locale.ROOT)
        return candidates
            .asSequence()
            .filterNot(FileCandidate::isDirectory)
            .filter { normalizedFilter.isBlank() || normalizedFilter == "*" || extensionOf(it.name) == normalizedFilter }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .toList()
    }

    private fun isNoiseToken(token: String): Boolean =
        token.toIntOrNull() != null ||
            token.matches(Regex("(19|20)\\d{2}")) ||
            token.matches(Regex("s\\d{1,2}e\\d{1,2}")) ||
            token.matches(Regex("[se]\\d{1,2}")) ||
            token in SERIES_NOISE

    private fun cleanupStem(stem: String): String = stem.replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun FileCandidate.moveTo(folder: String) = PlannedChange(
        sourceUri = uri,
        sourceParentUri = parentUri,
        sourceName = name,
        targetName = name,
        destinationFolder = folder,
        kind = ChangeKind.MOVE,
    )

    private fun FileCandidate.renameTo(newName: String) = PlannedChange(
        sourceUri = uri,
        sourceParentUri = parentUri,
        sourceName = name,
        targetName = newName,
        kind = ChangeKind.RENAME,
    )

    companion object {
        private val DEFAULT_CATEGORIES = mapOf(
            "Images" to setOf("jpg", "jpeg", "png", "gif", "tiff", "bmp", "webp", "heic"),
            "Documents" to setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv"),
            "Audio" to setOf("mp3", "wav", "ogg", "flac", "m4a", "aac"),
            "Video" to setOf("mp4", "mov", "avi", "mkv", "webm"),
            "Archives" to setOf("zip", "rar", "7z", "tar", "gz"),
        )

        private val SERIES_NOISE = setOf(
            "season", "episode", "ep", "e", "s", "part", "complete", "final", "finale",
            "brrip", "webrip", "hdrip", "bluray", "x264", "x265", "1080p", "720p", "2160p",
            "web", "rip", "dvdrip", "cam", "hd", "uhd", "hdtv", "aac", "hevc", "ac3", "amzn",
            "dl", "subs", "internal", "10bit", "esubs", "mp4", "mkv", "nf",
        )

        private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        private fun stemOf(name: String): String = name.substringBeforeLast('.', name)
        private fun sanitizeFilename(name: String): String = name
            .replace(Regex("[\\u0000-\\u001f\\u007f/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "Untitled" }
    }
}
