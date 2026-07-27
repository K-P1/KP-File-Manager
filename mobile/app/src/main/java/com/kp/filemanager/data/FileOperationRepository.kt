/** mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt: Orchestrates read-only scans and plans, approved execution, structured results, logs, and session undo; refactored for media organization. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.BatchRenameRequest
import com.kp.filemanager.domain.DirectoryScanner
import com.kp.filemanager.domain.FileToolPlanner
import com.kp.filemanager.domain.FindAndReplaceRequest
import com.kp.filemanager.domain.MediaOrganizationPlanner
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.ScanProgress
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Storage(cause: Throwable) : DataError("The selected folder could not be accessed.", cause)
    class InvalidPlan(message: String) : DataError(message)
}

interface FileOperationRepository {
    suspend fun preview(
        request: OperationRequest,
        onScanProgress: (ScanProgress) -> Unit = {},
    ): Result<OperationResult>

    suspend fun execute(plan: OperationPlan): Result<OperationResult>
    suspend fun undoLast(): Result<OperationResult>
    fun folderName(treeUri: String): String
    fun canUndo(): Boolean
}

class DefaultFileOperationRepository(
    private val gateway: DocumentGateway,
    private val logStore: OperationLogStore,
    private val scanner: DirectoryScanner = DirectoryScanner(gateway),
    private val mediaPlanner: MediaOrganizationPlanner = MediaOrganizationPlanner(),
    private val toolPlanner: FileToolPlanner = FileToolPlanner(),
    private val executor: OperationExecutor = OperationExecutor(gateway),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileOperationRepository {
    override suspend fun preview(
        request: OperationRequest,
        onScanProgress: (ScanProgress) -> Unit,
    ): Result<OperationResult> = storageResult {
        val sourceScan = scanner.scan(
            treeUri = request.sourceTreeUri,
            maximumDepth = if (request.mode == OperationMode.ORGANIZE_MEDIA) {
                request.maximumScanDepth.coerceIn(0, MAXIMUM_ALLOWED_SCAN_DEPTH)
            } else {
                0
            },
            onProgress = onScanProgress,
        )
        val destinationTree = request.destinationTreeUri ?: request.sourceTreeUri
        val plan = when (request.mode) {
            OperationMode.ORGANIZE_MEDIA -> {
                val sameTree = destinationTree == request.sourceTreeUri
                val existingPaths = if (sameTree) {
                    sourceScan.files.mapTo(mutableSetOf()) { it.sourcePath }
                } else {
                    scanner.scan(
                        treeUri = destinationTree,
                        maximumDepth = request.maximumScanDepth.coerceAtLeast(4)
                            .coerceAtMost(MAXIMUM_ALLOWED_SCAN_DEPTH),
                    ).files.mapTo(mutableSetOf()) { it.sourcePath }
                }
                mediaPlanner.plan(
                    files = sourceScan.files,
                    destinationTreeUri = destinationTree,
                    existingDestinationPaths = existingPaths,
                    sourceAndDestinationAreSame = sameTree,
                    movieFolderName = request.movieFolderName,
                    tvFolderName = request.tvFolderName,
                    scanWarnings = sourceScan.warnings,
                )
            }
            OperationMode.BATCH_RENAME -> OperationPlan(
                destinationTreeUri = request.sourceTreeUri,
                operations = toolPlanner.planBatchRename(
                    files = sourceScan.files,
                    request = BatchRenameRequest(
                        extensionFilter = request.extensionFilter,
                        prefix = request.prefix,
                        suffix = request.suffix,
                        startingNumber = request.startIndex,
                        digitPadding = request.zeroPadding,
                        replacementExtension = request.replacementExtension,
                    ),
                    destinationTreeUri = request.sourceTreeUri,
                ),
                scanWarnings = sourceScan.warnings,
                existingDestinationPaths = sourceScan.files.mapTo(mutableSetOf()) { it.sourcePath },
                sourceAndDestinationAreSame = true,
            )
            OperationMode.FIND_AND_REPLACE -> OperationPlan(
                destinationTreeUri = request.sourceTreeUri,
                operations = toolPlanner.planFindAndReplace(
                    files = sourceScan.files,
                    request = FindAndReplaceRequest(
                        findText = request.keyword,
                        replacementText = request.replacement,
                        extensionFilter = request.extensionFilter,
                    ),
                    destinationTreeUri = request.sourceTreeUri,
                ),
                scanWarnings = sourceScan.warnings,
                existingDestinationPaths = sourceScan.files.mapTo(mutableSetOf()) { it.sourcePath },
                sourceAndDestinationAreSame = true,
            )
        }
        logStore.replace(
            operation = "PREVIEW",
            lines = plan.operations.map {
                "${it.operationType}: ${it.sourcePath} -> ${it.destinationPath} [${it.status}]"
            },
        )
        OperationResult(
            plan = plan,
            skippedOperations = plan.operations.filter {
                it.status != com.kp.filemanager.domain.OperationStatus.READY
            },
            warnings = plan.scanWarnings.map { "${it.path}: ${it.reason}" },
        )
    }

    override suspend fun execute(plan: OperationPlan): Result<OperationResult> = storageResult {
        if (plan.hasBlockingConflicts) {
            throw DataError.InvalidPlan("Resolve all blocking conflicts before applying changes.")
        }
        val result = executor.execute(plan)
        logStore.replace(
            operation = "RUN",
            lines = buildList {
                result.completedOperations.forEach {
                    add("COMPLETED: ${it.sourcePath} -> ${it.destinationPath}")
                }
                result.skippedOperations.forEach { add("SKIPPED: ${it.sourcePath} [${it.status}]") }
                result.failedOperations.forEach { add("FAILED: ${it.operation.sourcePath}: ${it.reason}") }
            },
        )
        result
    }

    override suspend fun undoLast(): Result<OperationResult> = storageResult {
        val result = executor.undoLast()
        logStore.replace(
            operation = "UNDO",
            lines = buildList {
                add("Restored ${result.completedCount} item(s)")
                result.failedOperations.forEach { add("FAILED: ${it.operation.originalName}: ${it.reason}") }
            },
        )
        result
    }

    override fun folderName(treeUri: String): String = gateway.displayName(treeUri)

    override fun canUndo(): Boolean = executor.canUndo()

    private suspend fun <T> storageResult(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: DataError) {
            Result.failure(error)
        } catch (error: IOException) {
            Result.failure(DataError.Storage(error))
        } catch (error: SecurityException) {
            Result.failure(DataError.Storage(error))
        } catch (error: IllegalArgumentException) {
            Result.failure(DataError.Storage(error))
        } catch (error: IllegalStateException) {
            Result.failure(DataError.Storage(error))
        }
    }

    private companion object {
        const val MAXIMUM_ALLOWED_SCAN_DEPTH = 20
    }
}
