/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerUiState.kt: Models organizer inputs, effective output selection, progress, retained plans, results, and undo availability. */
package com.kp.filemanager.ui

import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OrganizationProgress
import com.kp.filemanager.domain.PlannedFileOperation

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

data class OrganizationDefaults(
    val maximumScanDepth: Int = 4,
    val movieFolderName: String = "Movies",
    val tvFolderName: String = "TV Shows",
)

data class FileManagerUiState(
    val mode: OperationMode = OperationMode.ORGANIZE_MEDIA,
    val sourceUri: String? = null,
    val sourceName: String = "",
    val destinationUri: String? = null,
    val destinationName: String = "",
    val movieLibraryUri: String? = null,
    val movieLibraryName: String = "",
    val tvLibraryUri: String? = null,
    val tvLibraryName: String = "",
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
    val organizationProgress: OrganizationProgress = OrganizationProgress(),
    val activePlan: OperationPlan? = null,
    val isWorking: Boolean = false,
    val progressText: String = "",
    val statusTitle: String? = null,
    val statusMessage: String? = null,
    val failures: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val hasManualReviewChanges: Boolean = false,
) {
    val effectiveDestinationUri: String?
        get() = destinationUri ?: sourceUri

    val effectiveDestinationName: String
        get() = destinationName.ifBlank { sourceName }

    val usesSourceAsDestination: Boolean
        get() = destinationUri == null

    val preview: List<PlannedFileOperation>
        get() = activePlan?.operations.orEmpty()

    val canPreview: Boolean
        get() = sourceUri != null &&
            !isWorking &&
            (mode != OperationMode.ORGANIZE_MEDIA ||
                (movieFolderName.isNotBlank() && tvFolderName.isNotBlank())) &&
            (mode != OperationMode.FIND_AND_REPLACE || keyword.isNotBlank())

    val canApply: Boolean
        get() = canPreview &&
            activePlan != null &&
            activePlan.readyCount > 0 &&
            !activePlan.hasBlockingConflicts
}
