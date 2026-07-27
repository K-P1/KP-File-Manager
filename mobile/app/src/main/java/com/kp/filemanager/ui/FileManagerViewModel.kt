/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt: Owns the non-visual scan, review, apply, and undo state machine; refactored to execute only retained preview plans. */
package com.kp.filemanager.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kp.filemanager.data.FileOperationRepository
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.PlanConflictResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
interface FileManagerActions {
    fun selectMode(mode: OperationMode)
    fun selectSource(uri: String)
    fun selectDestination(uri: String)
    fun clearDestination()
    fun setMaximumScanDepth(value: Int)
    fun setExtensionFilter(value: String)
    fun setPrefix(value: String)
    fun setStartIndex(value: Int)
    fun setZeroPadding(value: Int)
    fun setKeyword(value: String)
    fun setReplacement(value: String)
    fun preview()
    fun cancelScan()
    fun skipConflict(sourceUri: String)
    fun renameConflict(sourceUri: String, alternativeName: String)
    fun run()
    fun undo()
    fun dismissStatus()
}

class FileManagerViewModel(
    private val repository: FileOperationRepository,
    private val conflictResolver: PlanConflictResolver = PlanConflictResolver(),
) : ViewModel(), FileManagerActions {
    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()
    private var previewJob: Job? = null

    override fun selectMode(mode: OperationMode) = invalidatePlan { copy(mode = mode) }

    override fun selectSource(uri: String) {
        val name = runCatching { repository.folderName(uri) }.getOrDefault("Selected folder")
        invalidatePlan {
            copy(
                sourceUri = uri,
                sourceName = name,
                workflowState = OrganizerWorkflowState.FOLDER_SELECTED,
            )
        }
    }

    override fun selectDestination(uri: String) {
        val name = runCatching { repository.folderName(uri) }.getOrDefault("Selected folder")
        invalidatePlan { copy(destinationUri = uri, destinationName = name) }
    }

    override fun clearDestination() = invalidatePlan { copy(destinationUri = null, destinationName = "") }
    override fun setMaximumScanDepth(value: Int) = invalidatePlan { copy(maximumScanDepth = value.coerceIn(0, 20)) }
    override fun setExtensionFilter(value: String) = invalidatePlan { copy(extensionFilter = value) }
    override fun setPrefix(value: String) = invalidatePlan { copy(prefix = value) }
    override fun setStartIndex(value: Int) = invalidatePlan { copy(startIndex = value.coerceAtLeast(1)) }
    override fun setZeroPadding(value: Int) = invalidatePlan { copy(zeroPadding = value.coerceIn(0, 12)) }
    override fun setKeyword(value: String) = invalidatePlan { copy(keyword = value) }
    override fun setReplacement(value: String) = invalidatePlan { copy(replacement = value) }

    override fun preview() {
        val snapshot = _uiState.value
        if (!snapshot.canPreview) return
        val request = snapshot.toRequest()
        previewJob = viewModelScope.launch {
            mutate {
                copy(
                    isWorking = true,
                    workflowState = if (mode == OperationMode.ORGANIZE_MEDIA) {
                        OrganizerWorkflowState.SCANNING
                    } else {
                        OrganizerWorkflowState.REVIEWING
                    },
                    scanProgress = com.kp.filemanager.domain.ScanProgress(),
                    progressText = if (mode == OperationMode.ORGANIZE_MEDIA) {
                        "Scanning media and building a safe change plan…"
                    } else {
                        "Building a safe filename preview…"
                    },
                    activePlan = null,
                    statusTitle = null,
                    statusMessage = null,
                    failures = emptyList(),
                )
            }
            try {
                repository.preview(request) { progress ->
                    mutate {
                        copy(
                            scanProgress = progress,
                            progressText = "Scanned ${progress.filesScanned} file(s): ${progress.currentPath}",
                        )
                    }
                }.fold(
                    onSuccess = { outcome ->
                        val plan = outcome.plan
                        mutate {
                            copy(
                                isWorking = false,
                                progressText = "",
                                activePlan = plan,
                                workflowState = when {
                                    mode == OperationMode.ORGANIZE_MEDIA && plan.groups.isEmpty() ->
                                        OrganizerWorkflowState.SCAN_COMPLETE
                                    plan.hasBlockingConflicts -> OrganizerWorkflowState.RESOLVING_CONFLICTS
                                    else -> OrganizerWorkflowState.REVIEWING
                                },
                                statusTitle = if (mode == OperationMode.ORGANIZE_MEDIA && plan.groups.isEmpty()) {
                                    "No movies or TV episodes found"
                                } else if (plan.scanWarnings.isNotEmpty()) {
                                    "Preview ready with warnings"
                                } else {
                                    "Preview ready"
                                },
                                statusMessage = if (mode == OperationMode.ORGANIZE_MEDIA && plan.groups.isEmpty()) {
                                    "Try another folder or increase the scan depth."
                                } else {
                                    buildString {
                                        append("${plan.operations.size} item(s) reviewed; ${plan.readyCount} change(s) ready.")
                                        if (plan.scanWarnings.isNotEmpty()) {
                                            append(" ${plan.scanWarnings.size} folder(s) could not be accessed.")
                                        }
                                        if (plan.hasBlockingConflicts) append(" Resolve blocking conflicts before applying.")
                                    }
                                },
                                failures = plan.scanWarnings.map { "${it.path}: ${it.reason}" },
                            )
                        }
                    },
                    onFailure = { showFailure(it, OrganizerWorkflowState.SCAN_FAILED) },
                )
            } finally {
                previewJob = null
            }
        }
    }

    override fun cancelScan() {
        if (_uiState.value.workflowState != OrganizerWorkflowState.SCANNING) return
        previewJob?.cancel()
        previewJob = null
        mutate {
            copy(
                isWorking = false,
                workflowState = OrganizerWorkflowState.FOLDER_SELECTED,
                progressText = "",
                activePlan = null,
                statusTitle = "Scan canceled",
                statusMessage = "Files were not changed.",
            )
        }
    }

    override fun skipConflict(sourceUri: String) = resolvePlan { conflictResolver.skip(this, sourceUri) }

    override fun renameConflict(sourceUri: String, alternativeName: String) {
        try {
            resolvePlan { conflictResolver.rename(this, sourceUri, alternativeName) }
        } catch (error: IllegalArgumentException) {
            mutate {
                copy(
                    statusTitle = "Invalid destination name",
                    statusMessage = error.message ?: "Choose another filename.",
                )
            }
        }
    }

    override fun run() {
        val plan = _uiState.value.activePlan ?: return
        if (!_uiState.value.canApply) return
        viewModelScope.launch {
            mutate {
                copy(
                    isWorking = true,
                    workflowState = OrganizerWorkflowState.APPLYING,
                    progressText = "Applying the approved change plan…",
                    statusTitle = null,
                    statusMessage = null,
                    failures = emptyList(),
                )
            }
            repository.execute(plan).fold(
                onSuccess = { outcome ->
                    mutate {
                        copy(
                            isWorking = false,
                            progressText = "",
                            activePlan = outcome.plan,
                            workflowState = if (outcome.failedOperations.isEmpty()) {
                                OrganizerWorkflowState.APPLY_COMPLETE
                            } else {
                                OrganizerWorkflowState.APPLY_PARTIAL_FAILURE
                            },
                            statusTitle = if (outcome.failedOperations.isEmpty()) {
                                "Operation complete"
                            } else {
                                "Operation partly complete"
                            },
                            statusMessage = "Changed ${outcome.completedCount} of ${outcome.plan.readyCount} ready item(s).",
                            failures = outcome.failures,
                            canUndo = repository.canUndo(),
                        )
                    }
                },
                onFailure = { showFailure(it, OrganizerWorkflowState.APPLY_PARTIAL_FAILURE) },
            )
        }
    }

    override fun undo() {
        if (_uiState.value.isWorking || !repository.canUndo()) return
        viewModelScope.launch {
            mutate {
                copy(
                    isWorking = true,
                    workflowState = OrganizerWorkflowState.UNDOING,
                    progressText = "Restoring the previous operation…",
                    statusTitle = null,
                    statusMessage = null,
                )
            }
            repository.undoLast().fold(
                onSuccess = { result ->
                    mutate {
                        copy(
                            isWorking = false,
                            progressText = "",
                            activePlan = null,
                            workflowState = OrganizerWorkflowState.UNDO_COMPLETE,
                            statusTitle = if (result.failures.isEmpty()) "Undo complete" else "Undo partly complete",
                            statusMessage = "Restored ${result.completedCount} item(s).",
                            failures = result.failures,
                            canUndo = repository.canUndo(),
                        )
                    }
                },
                onFailure = { showFailure(it, OrganizerWorkflowState.UNDO_COMPLETE) },
            )
        }
    }

    override fun dismissStatus() = mutate { copy(statusTitle = null, statusMessage = null, failures = emptyList()) }

    private fun showFailure(error: Throwable, workflowState: OrganizerWorkflowState) = mutate {
        copy(
            isWorking = false,
            progressText = "",
            workflowState = workflowState,
            statusTitle = "Couldn’t complete the operation",
            statusMessage = error.message ?: "Check that the selected folder still allows changes.",
        )
    }

    private fun FileManagerUiState.toRequest() = OperationRequest(
        mode = mode,
        sourceTreeUri = checkNotNull(sourceUri),
        destinationTreeUri = destinationUri,
        maximumScanDepth = maximumScanDepth,
        movieFolderName = movieFolderName,
        tvFolderName = tvFolderName,
        extensionFilter = extensionFilter,
        prefix = prefix,
        suffix = suffix,
        startIndex = startIndex,
        zeroPadding = zeroPadding,
        replacementExtension = replacementExtension.takeIf(String::isNotBlank),
        keyword = keyword,
        replacement = replacement,
    )

    private inline fun invalidatePlan(transform: FileManagerUiState.() -> FileManagerUiState) {
        mutate {
            transform().copy(
                activePlan = null,
                statusTitle = null,
                statusMessage = null,
                failures = emptyList(),
            )
        }
    }

    private inline fun resolvePlan(transform: com.kp.filemanager.domain.OperationPlan.() -> com.kp.filemanager.domain.OperationPlan) {
        val currentPlan = _uiState.value.activePlan ?: return
        val updatedPlan = currentPlan.transform()
        mutate {
            copy(
                activePlan = updatedPlan,
                workflowState = if (updatedPlan.hasBlockingConflicts) {
                    OrganizerWorkflowState.RESOLVING_CONFLICTS
                } else {
                    OrganizerWorkflowState.REVIEWING
                },
                statusTitle = null,
                statusMessage = null,
            )
        }
    }

    private inline fun mutate(transform: FileManagerUiState.() -> FileManagerUiState) {
        _uiState.update(transform)
    }

    class Factory(
        private val repository: FileOperationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FileManagerViewModel(repository) as T
    }
}
