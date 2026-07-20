/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt: Models screen inputs, operation progress, previews, and results; last updated for visible progress text. */
package com.kp.filemanager.ui

import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.PlannedChange

data class FileManagerUiState(
    val mode: OperationMode = OperationMode.SORT,
    val sourceUri: String? = null,
    val sourceName: String = "",
    val destinationUri: String? = null,
    val destinationName: String = "",
    val seriesMode: Boolean = false,
    val extensionFilter: String = "*",
    val prefix: String = "Renamed_",
    val startIndex: Int = 1,
    val zeroPadding: Int = 2,
    val scramble: Boolean = false,
    val scrambleMultiplier: Int = 1,
    val keyword: String = "",
    val replacement: String = "",
    val isWorking: Boolean = false,
    val progressText: String = "",
    val preview: List<PlannedChange> = emptyList(),
    val statusTitle: String? = null,
    val statusMessage: String? = null,
    val failures: List<String> = emptyList(),
    val canUndo: Boolean = false,
) {
    val canRun: Boolean
        get() = sourceUri != null && !isWorking && (mode != OperationMode.KEYWORD_RENAME || keyword.isNotBlank())
}
