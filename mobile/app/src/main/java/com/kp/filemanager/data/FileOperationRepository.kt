/** mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt: Coordinates previews, execution, logs, and undo while mapping storage errors; created for mobile parity. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationPlanner
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OperationResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Storage(cause: Throwable) : DataError("The selected folder could not be accessed.", cause)
}

interface FileOperationRepository {
    suspend fun preview(request: OperationRequest): Result<OperationResult>
    suspend fun execute(request: OperationRequest): Result<OperationResult>
    suspend fun undoLast(): Result<OperationResult>
    fun folderName(treeUri: String): String
    fun canUndo(): Boolean
}

class DefaultFileOperationRepository(
    private val gateway: DocumentGateway,
    private val planner: OperationPlanner,
    private val logStore: OperationLogStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileOperationRepository {
    private var lastMutations: List<DocumentMutation> = emptyList()

    override suspend fun preview(request: OperationRequest): Result<OperationResult> = storageResult {
        val changes = createPlan(request)
        logStore.replace("PREVIEW", changes.map { "${it.sourceName} -> ${it.destinationPath}" })
        OperationResult(changes = changes)
    }

    override suspend fun execute(request: OperationRequest): Result<OperationResult> = storageResult {
        val changes = createPlan(request)
        val mutations = mutableListOf<DocumentMutation>()
        val failures = mutableListOf<String>()

        if (request.mode == OperationMode.SORT) {
            val destinationTree = request.destinationTreeUri ?: request.sourceTreeUri
            changes.forEach { change ->
                try {
                    val targetParent = gateway.ensureDirectory(destinationTree, checkNotNull(change.destinationFolder))
                    mutations += gateway.move(change, targetParent)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failures += "${change.sourceName}: ${safeReason(error)}"
                }
            }
        } else {
            val (completed, renameFailures) = gateway.renameBatch(changes)
            mutations += completed
            failures += renameFailures
        }

        lastMutations = mutations
        val lines = changes.map { "${it.sourceName} -> ${it.destinationPath}" } + failures.map { "FAILED: $it" }
        logStore.replace("RUN", lines)
        OperationResult(changes = changes, completedCount = mutations.size, failures = failures)
    }

    override suspend fun undoLast(): Result<OperationResult> = storageResult {
        val failures = mutableListOf<String>()
        val remaining = mutableListOf<DocumentMutation>()
        var completed = 0
        lastMutations.asReversed().forEach { mutation ->
            try {
                gateway.undo(mutation)
                completed += 1
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                remaining += mutation
                failures += "${mutation.originalName}: ${safeReason(error)}"
            }
        }
        lastMutations = remaining.asReversed()
        logStore.replace("UNDO", listOf("Restored $completed item(s)") + failures.map { "FAILED: $it" })
        OperationResult(changes = emptyList(), completedCount = completed, failures = failures)
    }

    override fun folderName(treeUri: String): String = gateway.displayName(treeUri)

    override fun canUndo(): Boolean = lastMutations.isNotEmpty()

    private fun createPlan(request: OperationRequest) = gateway
        .listCandidates(request.sourceTreeUri, includeAudioMetadata = request.mode == OperationMode.MUSIC_RENAME)
        .let { candidates ->
            when (request.mode) {
                OperationMode.SORT -> planner.planSort(candidates, request.seriesMode)
                OperationMode.MASS_RENAME -> planner.planMassRename(
                    candidates = candidates,
                    extensionFilter = request.extensionFilter,
                    prefix = request.prefix,
                    startIndex = request.startIndex,
                    zeroPadding = request.zeroPadding,
                    scramble = request.scramble,
                    scrambleMultiplier = request.scrambleMultiplier,
                )
                OperationMode.MUSIC_RENAME -> planner.planMusicRename(candidates)
                OperationMode.KEYWORD_RENAME -> planner.planKeywordRename(
                    candidates = candidates,
                    extensionFilter = request.extensionFilter,
                    keyword = request.keyword,
                    replacement = request.replacement,
                )
            }
        }

    private suspend fun <T> storageResult(block: () -> T): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.failure(DataError.Storage(error))
        } catch (error: SecurityException) {
            Result.failure(DataError.Storage(error))
        } catch (error: IllegalArgumentException) {
            Result.failure(DataError.Storage(error))
        }
    }

    private fun safeReason(error: Exception): String = when (error) {
        is SecurityException -> "The folder provider denied this change"
        is IOException -> "The provider could not complete this change"
        else -> error.message?.take(120) ?: "Unknown storage error"
    }
}
