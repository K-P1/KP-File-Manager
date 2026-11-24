/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportViewModel.kt
 * Purpose: Lifecycle-safe selection, permission, scan, transfer, cancel, restore and progress state.
 * Last changed: 2026-11-24, align permission guidance with the authorization-only check.
 */
package com.kp.filemanager.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class ProtectedExportUiState(
    val shizukuRunning: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val isWorking: Boolean = false,
    val preview: ExportPreview? = null,
    val selectedIds: Set<String> = emptySet(),
    val progress: ExportProgress = ExportProgress(),
    val message: String? = null,
    val error: String? = null,
    val result: ExportOutcome? = null,
    val canUndo: Boolean = false,
) {
    val selectedBundles: List<ExportBundle>
        get() = preview?.bundles.orEmpty().filter { it.id in selectedIds && !it.blocked }
    val selectedFiles: Int get() = selectedBundles.sumOf { it.operations.size }
    val selectedBytes: Long get() = selectedBundles.sumOf { it.totalBytes }
    val canExport: Boolean get() = shizukuAuthorized && !isWorking && selectedBundles.isNotEmpty()
}

class ProtectedExportViewModel(private val repository: ProtectedExportRepository) : ViewModel() {
    private val state = MutableStateFlow(ProtectedExportUiState())
    val uiState: StateFlow<ProtectedExportUiState> = state.asStateFlow()
    private var task: Job? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PERMISSION_REQUEST_CODE) refreshAccess()
    }
    private val receivedListener = Shizuku.OnBinderReceivedListener { refreshAccess() }
    private val deadListener = Shizuku.OnBinderDeadListener { refreshAccess() }

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(receivedListener)
            Shizuku.addBinderDeadListener(deadListener)
        }
        refreshAccess()
    }

    fun refreshAccess() {
        val running = repository.shell.isRunning()
        val authorized = running && repository.shell.isAuthorized()
        state.update { it.copy(shizukuRunning = running, shizukuAuthorized = authorized,
            canUndo = runCatching { repository.hasUndo() }.getOrDefault(false)) }
    }

    fun requestAccess() {
        refreshAccess()
        if (!state.value.shizukuRunning) {
            state.update { it.copy(error = "Start Shizuku with wireless debugging, then return and tap Check authorization.") }
            return
        }
        try {
            repository.shell.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (error: Exception) {
            state.update { it.copy(error = error.message ?: "Could not request Shizuku access") }
        }
    }

    fun scan() {
        refreshAccess()
        if (task?.isActive == true || !state.value.shizukuAuthorized) return
        task = viewModelScope.launch {
            state.update { it.copy(isWorking = true, error = null, message = null, result = null,
                preview = null, selectedIds = emptySet(), progress = ExportProgress(ExportStage.SCANNING)) }
            try {
                val preview = repository.scan { progress -> state.update { it.copy(progress = progress) } }
                state.update { it.copy(
                    preview = preview,
                    selectedIds = preview.eligible.mapTo(mutableSetOf()) { bundle -> bundle.id },
                    message = if (preview.bundles.isEmpty()) "No supported videos found in the protected MovieBox folder." else
                        "${preview.bundles.size} media groups found. Review the destinations before exporting.",
                    progress = ExportProgress(ExportStage.IDLE),
                ) }
            } catch (error: CancellationException) {
                state.update { it.copy(message = "Scan stopped. No files were changed.") }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "Cannot scan the protected source") }
            } finally {
                state.update { it.copy(isWorking = false) }
                refreshAccess()
            }
        }
    }

    fun setSelected(bundleId: String, selected: Boolean) {
        if (state.value.isWorking) return
        state.update { current ->
            val bundle = current.preview?.bundles?.firstOrNull { it.id == bundleId }
            if (bundle == null || bundle.blocked) current
            else current.copy(selectedIds = if (selected) current.selectedIds + bundleId else current.selectedIds - bundleId,
                result = null, message = null)
        }
    }

    fun selectAll() {
        if (state.value.isWorking) return
        state.update { it.copy(selectedIds = it.preview?.eligible.orEmpty().mapTo(mutableSetOf()) { bundle -> bundle.id }) }
    }

    fun clearSelection() {
        if (state.value.isWorking) return
        state.update { it.copy(selectedIds = emptySet()) }
    }

    fun export() {
        val snapshot = state.value
        val preview = snapshot.preview ?: return
        if (!snapshot.canExport || task?.isActive == true) return
        task = viewModelScope.launch {
            state.update { it.copy(isWorking = true, result = null, message = null, error = null,
                progress = ExportProgress(ExportStage.CHECKING, message = "Validating destinations")) }
            try {
                runCatching { repository.startForegroundTransfer() }
                val outcome = repository.export(preview, snapshot.selectedIds) { progress ->
                    state.update { it.copy(progress = progress) }
                    runCatching { repository.updateForegroundTransfer(progress) }
                }
                state.update { it.copy(
                    result = outcome,
                    message = if (outcome.succeeded) "Export complete. The media library is ready." else
                        "Export finished with items that need attention. Originals were preserved where needed.",
                    preview = null, selectedIds = emptySet(),
                ) }
            } catch (error: CancellationException) {
                state.update { it.copy(message = "Export stopped. Verified exports remain recoverable; scan again to refresh.") }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "Export failed. Original files were not intentionally replaced.") }
            } finally {
                runCatching { repository.stopForegroundTransfer() }
                state.update { it.copy(isWorking = false) }
                refreshAccess()
            }
        }
    }

    fun restore() {
        if (state.value.isWorking || task?.isActive == true || !state.value.canUndo) return
        task = viewModelScope.launch {
            state.update { it.copy(isWorking = true, message = null, error = null, result = null,
                progress = ExportProgress(ExportStage.RESTORING, message = "Restoring verified original files")) }
            try {
                runCatching { repository.startForegroundTransfer() }
                val outcome = repository.undo { progress ->
                    state.update { it.copy(progress = progress) }
                    runCatching { repository.updateForegroundTransfer(progress) }
                }
                state.update { it.copy(result = outcome, message = if (outcome.failedBundles.isEmpty())
                    "Originals restored successfully." else "Some originals could not be restored. Published copies were kept safely.",
                    preview = null, selectedIds = emptySet()) }
            } catch (error: CancellationException) {
                state.update { it.copy(message = "Restore stopped. Remaining copies stay available for retry.") }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "Restore failed") }
            } finally {
                runCatching { repository.stopForegroundTransfer() }
                state.update { it.copy(isWorking = false) }
                refreshAccess()
            }
        }
    }

    fun cancel() {
        task?.cancel()
    }

    fun clearMessage() = state.update { it.copy(message = null, error = null, result = null) }

    override fun onCleared() {
        repository.shell.close()
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(receivedListener)
            Shizuku.removeBinderDeadListener(deadListener)
        }
        super.onCleared()
    }

    class Factory(private val repository: ProtectedExportRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProtectedExportViewModel::class.java))
            return ProtectedExportViewModel(repository) as T
        }
    }

    private companion object { const val PERMISSION_REQUEST_CODE = 2431 }
}
