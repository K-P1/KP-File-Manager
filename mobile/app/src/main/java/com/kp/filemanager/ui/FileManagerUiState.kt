/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt: Models core workflow inputs, scan progress, immutable plans, execution results, and session undo availability; refactored for media organization. */
package com.kp.filemanager.ui

import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.domain.ScanProgress

enum class OrganizerWorkflowState {
    IDLE,
    FOLDER_SELECTED,
    SCANNING,
    SCAN_FAILED,
    SCAN_COMPLETE,
    REVIEWING,
    RESOLVING_CONFLICTS,
    CONFIRMING,
    APPLYING,
    APPLY_COMPLETE,
    APPLY_PARTIAL_FAILURE,
    UNDOING,
    UNDO_COMPLETE,
}

data class FileManagerUiState(
    val mode: OperationMode = OperationMode.ORGANIZE_MEDIA,
    val sourceUri: String? = null,
    val sourceName: String = "",
    val destinationUri: String? = null,
    val destinationName: String = "",
    val maximumScanDepth: Int = 4,
    val movieFolderName: String = "Movies",
    val tvFolderName: String = "TV Shows",
    val extensionFilter: String = "*",
    val prefix: String = "Renamed_",
    val suffix: String = "",
    val startIndex: Int = 1,
    val zeroPadding: Int = 2,
    val replacementExtension: String = "",
    val keyword: String = "",
    val replacement: String = "",
    val workflowState: OrganizerWorkflowState = OrganizerWorkflowState.IDLE,
    val scanProgress: ScanProgress = ScanProgress(),
    val activePlan: OperationPlan? = null,
    val isWorking: Boolean = false,
    val progressText: String = "",
    val statusTitle: String? = null,
    val statusMessage: String? = null,
    val failures: List<String> = emptyList(),
    val canUndo: Boolean = false,
) {
    val preview: List<PlannedFileOperation>
        get() = activePlan?.operations.orEmpty()

    val canPreview: Boolean
        get() = sourceUri != null &&
            !isWorking &&
            (mode != OperationMode.FIND_AND_REPLACE || keyword.isNotBlank())

    val canApply: Boolean
        get() = canPreview &&
            activePlan != null &&
            activePlan.readyCount > 0 &&
            !activePlan.hasBlockingConflicts
}
