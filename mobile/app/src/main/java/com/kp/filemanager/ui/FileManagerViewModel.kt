/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerViewModel.kt: Owns mobile form state and repository operations; created for the Compose screen. */
package com.kp.filemanager.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kp.filemanager.data.FileOperationRepository
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationRequest
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
    fun setSeriesMode(enabled: Boolean)
    fun setExtensionFilter(value: String)
    fun setPrefix(value: String)
    fun setStartIndex(value: Int)
    fun setZeroPadding(value: Int)
    fun setScramble(enabled: Boolean)
    fun setScrambleMultiplier(value: Int)
    fun setKeyword(value: String)
    fun setReplacement(value: String)
    fun preview()
    fun run()
    fun undo()
    fun dismissStatus()
}

class FileManagerViewModel(
    private val repository: FileOperationRepository,
) : ViewModel(), FileManagerActions {
    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    override fun selectMode(mode: OperationMode) = mutate { copy(mode = mode, preview = emptyList()) }

    override fun selectSource(uri: String) {
        val name = runCatching { repository.folderName(uri) }.getOrDefault("Selected folder")
        mutate { copy(sourceUri = uri, sourceName = name, preview = emptyList()) }
    }

    override fun selectDestination(uri: String) {
        val name = runCatching { repository.folderName(uri) }.getOrDefault("Selected folder")
        mutate { copy(destinationUri = uri, destinationName = name, preview = emptyList()) }
    }

    override fun clearDestination() = mutate { copy(destinationUri = null, destinationName = "", preview = emptyList()) }
    override fun setSeriesMode(enabled: Boolean) = mutate { copy(seriesMode = enabled, preview = emptyList()) }
    override fun setExtensionFilter(value: String) = mutate { copy(extensionFilter = value, preview = emptyList()) }
    override fun setPrefix(value: String) = mutate { copy(prefix = value, preview = emptyList()) }
    override fun setStartIndex(value: Int) = mutate { copy(startIndex = value.coerceAtLeast(1), preview = emptyList()) }
    override fun setZeroPadding(value: Int) = mutate { copy(zeroPadding = value.coerceIn(0, 8), preview = emptyList()) }
    override fun setScramble(enabled: Boolean) = mutate { copy(scramble = enabled, preview = emptyList()) }
    override fun setScrambleMultiplier(value: Int) = mutate { copy(scrambleMultiplier = value.coerceIn(1, 10), preview = emptyList()) }
    override fun setKeyword(value: String) = mutate { copy(keyword = value, preview = emptyList()) }
    override fun setReplacement(value: String) = mutate { copy(replacement = value, preview = emptyList()) }

    override fun preview() = perform(previewOnly = true)
    override fun run() = perform(previewOnly = false)

    override fun undo() {
        if (_uiState.value.isWorking || !repository.canUndo()) return
        viewModelScope.launch {
            mutate { copy(isWorking = true, statusTitle = null, statusMessage = null) }
            repository.undoLast().fold(
                onSuccess = { result ->
                    mutate {
                        copy(
                            isWorking = false,
                            preview = emptyList(),
                            statusTitle = if (result.failures.isEmpty()) "Undo complete" else "Undo partly complete",
                            statusMessage = "Restored ${result.completedCount} item(s).",
                            failures = result.failures,
                            canUndo = repository.canUndo(),
                        )
                    }
                },
                onFailure = { showFailure(it) },
            )
        }
    }

    override fun dismissStatus() = mutate { copy(statusTitle = null, statusMessage = null, failures = emptyList()) }

    private fun perform(previewOnly: Boolean) {
        val snapshot = _uiState.value
        if (!snapshot.canRun) return
        val request = snapshot.toRequest()
        viewModelScope.launch {
            mutate { copy(isWorking = true, statusTitle = null, statusMessage = null, failures = emptyList()) }
            val result = if (previewOnly) repository.preview(request) else repository.execute(request)
            result.fold(
                onSuccess = { outcome ->
                    val title = when {
                        previewOnly -> "Preview ready"
                        outcome.failures.isEmpty() -> "Operation complete"
                        else -> "Operation partly complete"
                    }
                    val message = if (previewOnly) {
                        "${outcome.changes.size} change(s) are ready to review."
                    } else {
                        "Changed ${outcome.completedCount} of ${outcome.changes.size} item(s)."
                    }
                    mutate {
                        copy(
                            isWorking = false,
                            preview = outcome.changes,
                            statusTitle = title,
                            statusMessage = message,
                            failures = outcome.failures,
                            canUndo = repository.canUndo(),
                        )
                    }
                },
                onFailure = { showFailure(it) },
            )
        }
    }

    private fun showFailure(error: Throwable) = mutate {
        copy(
            isWorking = false,
            statusTitle = "Couldn’t complete the operation",
            statusMessage = error.message ?: "Check that the selected folder still allows changes.",
        )
    }

    private fun FileManagerUiState.toRequest() = OperationRequest(
        mode = mode,
        sourceTreeUri = checkNotNull(sourceUri),
        destinationTreeUri = destinationUri,
        seriesMode = seriesMode,
        extensionFilter = extensionFilter,
        prefix = prefix,
        startIndex = startIndex,
        zeroPadding = zeroPadding,
        scramble = scramble,
        scrambleMultiplier = scrambleMultiplier,
        keyword = keyword,
        replacement = replacement,
    )

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
