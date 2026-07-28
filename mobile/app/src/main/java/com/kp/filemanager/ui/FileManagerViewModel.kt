/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt: Orchestrates the indexed pipeline and throttles one immutable progress state while keeping provider work off the main thread; optimized for media organization. */
package com.kp.filemanager.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kp.filemanager.data.FileOperationRepository
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OrganizationProgress
import com.kp.filemanager.domain.LibraryContextType
import com.kp.filemanager.domain.PlanConflictResolver
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
    fun setMovieFolderName(value: String)
    fun setTvFolderName(value: String)
    fun setExtensionFilter(value: String)
    fun setPrefix(value: String)
    fun setSuffix(value: String)
    fun setReplacementExtension(value: String)
    fun setStartIndex(value: Int)
    fun setZeroPadding(value: Int)
    fun setKeyword(value: String)
    fun setReplacement(value: String)
    fun preview()
    fun cancelScan()
    fun skipConflict(sourceUri: String)
    fun renameConflict(sourceUri: String, alternativeName: String)
    fun resolveLibraryContext(contextType: LibraryContextType)
    fun run()
    fun undo()
    fun done()
    fun discardPreview()
    fun dismissStatus()
}

class FileManagerViewModel(
    private val repository: FileOperationRepository,
    private val conflictResolver: PlanConflictResolver = PlanConflictResolver(),
    organizationDefaults: OrganizationDefaults = OrganizationDefaults(),
    private val onOrganizationDefaultsChanged: (OrganizationDefaults) -> Unit = {},
) : ViewModel(), FileManagerActions {
    private val _uiState = MutableStateFlow(
        FileManagerUiState(
            maximumScanDepth = organizationDefaults.maximumScanDepth.coerceIn(0, 20),
            movieFolderName = organizationDefaults.movieFolderName.ifBlank { "Movies" },
            tvFolderName = organizationDefaults.tvFolderName.ifBlank { "TV Shows" },
        ),
    )
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()
    private var previewJob: Job? = null
    private var lastProgressUpdateNanos = 0L

    override fun selectMode(mode: OperationMode) {
        if (_uiState.value.mode == mode) return
        previewJob?.cancel()
        previewJob = null
        invalidatePlan { copy(mode = mode) }
    }

    override fun selectSource(uri: String) {
        previewJob?.cancel()
        previewJob = null
        invalidatePlan {
            copy(
                sourceUri = uri,
                sourceName = "Selected folder",
                workflowState = OrganizerWorkflowState.FOLDER_SELECTED,
            )
        }
        viewModelScope.launch {
            val name = safeFolderName(uri)
            mutate {
                if (sourceUri == uri) copy(sourceName = name) else this
            }
            if (_uiState.value.sourceUri == uri && _uiState.value.mode == OperationMode.ORGANIZE_MEDIA) {
                preview()
            }
        }
    }

    override fun selectDestination(uri: String) {
        invalidatePlan { copy(destinationUri = uri, destinationName = "Selected folder") }
        viewModelScope.launch {
            val name = safeFolderName(uri)
            mutate {
                if (destinationUri == uri) copy(destinationName = name) else this
            }
        }
    }

    override fun clearDestination() = invalidatePlan { copy(destinationUri = null, destinationName = "") }
    override fun setMaximumScanDepth(value: Int) {
        invalidatePlan { copy(maximumScanDepth = value.coerceIn(0, 20)) }
        persistOrganizationDefaults()
    }

    override fun setMovieFolderName(value: String) {
        invalidatePlan { copy(movieFolderName = value) }
        persistOrganizationDefaults()
    }

    override fun setTvFolderName(value: String) {
        invalidatePlan { copy(tvFolderName = value) }
        persistOrganizationDefaults()
    }
    override fun setExtensionFilter(value: String) = invalidatePlan { copy(extensionFilter = value) }
    override fun setPrefix(value: String) = invalidatePlan { copy(prefix = value) }
    override fun setSuffix(value: String) = invalidatePlan { copy(suffix = value) }
    override fun setReplacementExtension(value: String) =
        invalidatePlan { copy(replacementExtension = value) }
    override fun setStartIndex(value: Int) = invalidatePlan { copy(startIndex = value.coerceAtLeast(1)) }
    override fun setZeroPadding(value: Int) = invalidatePlan { copy(zeroPadding = value.coerceIn(0, 12)) }
    override fun setKeyword(value: String) = invalidatePlan { copy(keyword = value) }
    override fun setReplacement(value: String) = invalidatePlan { copy(replacement = value) }

    override fun preview() {
        val snapshot = _uiState.value
        if (!snapshot.canPreview || previewJob?.isActive == true) return
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
                    organizationProgress = OrganizationProgress(),
                    progressText = if (mode == OperationMode.ORGANIZE_MEDIA) {
                        "Scanning media and building a safe change plan…"
                    } else {
                        "Building a safe filename preview…"
                    },
                    activePlan = null,
                    statusTitle = null,
                    statusMessage = null,
                    failures = emptyList(),
                    hasManualReviewChanges = false,
                )
            }
            try {
                repository.preview(request) { progress ->
                    val now = System.nanoTime()
                    if (
                        progress.processedItems <= 1 ||
                        progress.percent == 100 ||
                        now - lastProgressUpdateNanos >= PROGRESS_UPDATE_INTERVAL_NANOS
                    ) {
                        lastProgressUpdateNanos = now
                        mutate {
                            copy(
                                organizationProgress = progress,
                                progressText = progressText(progress),
                            )
                        }
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
                hasManualReviewChanges = false,
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

    override fun resolveLibraryContext(contextType: LibraryContextType) {
        val plan = _uiState.value.activePlan ?: return
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            mutate { copy(isWorking = true) }
            repository.resolveLibraryContext(plan, contextType).fold(
                onSuccess = { result ->
                    mutate {
                        copy(
                            isWorking = false,
                            activePlan = result.plan,
                            workflowState = if (result.plan.hasBlockingConflicts) {
                                OrganizerWorkflowState.RESOLVING_CONFLICTS
                            } else {
                                OrganizerWorkflowState.REVIEWING
                            },
                            statusTitle = null,
                            statusMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    mutate {
                        copy(
                            isWorking = false,
                            statusTitle = "Couldnâ€™t resolve library context",
                            statusMessage = error.message ?: "Choose another library context.",
                        )
                    }
                },
            )
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
                    hasManualReviewChanges = false,
                )
            }
            repository.execute(plan) { progress ->
                val now = System.nanoTime()
                if (
                    progress.processedItems <= 1 ||
                    progress.percent == 100 ||
                    now - lastProgressUpdateNanos >= PROGRESS_UPDATE_INTERVAL_NANOS
                ) {
                    lastProgressUpdateNanos = now
                    mutate {
                        copy(
                            organizationProgress = progress,
                            progressText = progressText(progress),
                        )
                    }
                }
            }.fold(
                onSuccess = { outcome ->
                    mutate {
                        val progress = organizationProgress
                        val warnings = outcome.warnings.filterNot { warning ->
                            warning in failures
                        }
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
                            statusMessage = buildString {
                                append(
                                    "Moved ${progress.movedCount}, renamed ${progress.renamedCount}, " +
                                        "skipped ${progress.skippedCount}, failed ${progress.failedCount}.",
                                )
                                append(" Cleaned ${outcome.cleanedDirectoryCount} empty folder(s).")
                                if (outcome.failedOperations.isNotEmpty()) {
                                    append(" Empty-folder cleanup was skipped because relocation failures occurred.")
                                }
                            },
                            failures = outcome.failures + warnings,
                            canUndo = repository.canUndo(),
                            organizationProgress = progress.copy(
                                cleanedFolderCount = outcome.cleanedDirectoryCount,
                            ),
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

    override fun done() = mutate {
        copy(
            workflowState = if (sourceUri == null) {
                OrganizerWorkflowState.IDLE
            } else {
                OrganizerWorkflowState.FOLDER_SELECTED
            },
            activePlan = null,
            progressText = "",
            statusTitle = null,
            statusMessage = null,
            failures = emptyList(),
            hasManualReviewChanges = false,
        )
    }

    override fun discardPreview() = mutate {
        copy(
            workflowState = if (sourceUri == null) {
                OrganizerWorkflowState.IDLE
            } else {
                OrganizerWorkflowState.FOLDER_SELECTED
            },
            activePlan = null,
            progressText = "",
            statusTitle = null,
            statusMessage = null,
            failures = emptyList(),
            hasManualReviewChanges = false,
        )
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
            val transformed = transform()
            transformed.copy(
                activePlan = null,
                statusTitle = null,
                statusMessage = null,
                failures = emptyList(),
                workflowState = if (transformed.sourceUri == null) {
                    OrganizerWorkflowState.IDLE
                } else {
                    OrganizerWorkflowState.FOLDER_SELECTED
                },
                hasManualReviewChanges = false,
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
                hasManualReviewChanges = true,
            )
        }
    }

    private inline fun mutate(transform: FileManagerUiState.() -> FileManagerUiState) {
        _uiState.update(transform)
    }

    private fun persistOrganizationDefaults() {
        val state = _uiState.value
        onOrganizationDefaultsChanged(
            OrganizationDefaults(
                maximumScanDepth = state.maximumScanDepth,
                movieFolderName = state.movieFolderName,
                tvFolderName = state.tvFolderName,
            ),
        )
    }

    private fun progressText(progress: OrganizationProgress): String = buildString {
        append(progress.phase.label)
        if (progress.isIndeterminate) {
            append(": ${progress.filesDiscovered} files found")
        } else {
            append(": ${progress.processedItems}/${progress.totalItems}")
        }
        progress.currentItem.takeIf(String::isNotBlank)?.let { append(" â€¢ $it") }
    }

    private suspend fun safeFolderName(uri: String): String = try {
        repository.folderName(uri)
    } catch (error: CancellationException) {
        throw error
    } catch (_: SecurityException) {
        "Selected folder"
    } catch (_: IllegalArgumentException) {
        "Selected folder"
    } catch (_: IllegalStateException) {
        "Selected folder"
    } catch (_: IOException) {
        "Selected folder"
    } catch (_: UnsupportedOperationException) {
        "Selected folder"
    }

    class Factory(
        private val repository: FileOperationRepository,
        private val organizationDefaults: OrganizationDefaults = OrganizationDefaults(),
        private val onOrganizationDefaultsChanged: (OrganizationDefaults) -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FileManagerViewModel(
            repository = repository,
            organizationDefaults = organizationDefaults,
            onOrganizationDefaultsChanged = onOrganizationDefaultsChanged,
        ) as T
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_NANOS = 100_000_000L
    }
}
