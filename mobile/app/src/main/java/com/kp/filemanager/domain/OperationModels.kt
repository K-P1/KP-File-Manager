/** mobile/app/src/main/java/com/kp/filemanager/domain/OperationModels.kt: Defines platform-neutral file operation inputs and previews; created for mobile parity. */
package com.kp.filemanager.domain

data class AudioMetadata(
    val track: String? = null,
    val artist: String? = null,
    val title: String? = null,
)

data class FileCandidate(
    val uri: String,
    val parentUri: String,
    val name: String,
    val isDirectory: Boolean,
    val descendantExtensions: List<String>,
    val metadata: AudioMetadata = AudioMetadata(),
)

enum class ChangeKind {
    MOVE,
    RENAME,
}

data class PlannedChange(
    val sourceUri: String,
    val sourceParentUri: String,
    val sourceName: String,
    val targetName: String,
    val destinationFolder: String? = null,
    val kind: ChangeKind,
) {
    val destinationPath: String
        get() = destinationFolder?.let { "$it/$targetName" } ?: targetName
}

enum class OperationMode(val label: String, val supportingText: String) {
    SORT("Sort", "Move items into tidy categories"),
    MASS_RENAME("Batch", "Number many files consistently"),
    MUSIC_RENAME("Music", "Use track, artist, and title tags"),
    KEYWORD_RENAME("Replace", "Swap text inside filenames"),
}

data class OperationRequest(
    val mode: OperationMode,
    val sourceTreeUri: String,
    val destinationTreeUri: String? = null,
    val seriesMode: Boolean = false,
    val extensionFilter: String = "*",
    val prefix: String = "Renamed_",
    val startIndex: Int = 1,
    val zeroPadding: Int = 2,
    val scramble: Boolean = false,
    val scrambleMultiplier: Int = 1,
    val keyword: String = "",
    val replacement: String = "",
)

data class OperationResult(
    val changes: List<PlannedChange>,
    val completedCount: Int = 0,
    val failures: List<String> = emptyList(),
)
