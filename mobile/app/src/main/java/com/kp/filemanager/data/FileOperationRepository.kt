/** mobile/app/src/main/java/com/kp/filemanager/data/FileOperationRepository.kt: Orchestrates indexed planning across output roots, conflict checks, execution, cleanup, metrics, and undo. */
package com.kp.filemanager.data

import android.util.Log
import com.kp.filemanager.domain.BatchRenameRequest
import com.kp.filemanager.domain.ConflictDetector
import com.kp.filemanager.domain.DirectoryScanner
import com.kp.filemanager.domain.FileToolPlanner
import com.kp.filemanager.domain.FindAndReplaceRequest
import com.kp.filemanager.domain.LibraryContextResolver
import com.kp.filemanager.domain.LibraryContext
import com.kp.filemanager.domain.LibraryContextType
import com.kp.filemanager.domain.MediaOrganizationPlanner
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationRequest
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OrganizationProgress
import com.kp.filemanager.domain.PerformanceMetrics
import com.kp.filemanager.domain.ProgressReporter
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
        onProgress: (OrganizationProgress) -> Unit = {},
    ): Result<OperationResult>

    suspend fun execute(
        plan: OperationPlan,
        onProgress: (OrganizationProgress) -> Unit = {},
    ): Result<OperationResult>

    suspend fun resolveLibraryContext(
        plan: OperationPlan,
        contextType: LibraryContextType,
    ): Result<OperationResult>

    suspend fun undoLast(): Result<OperationResult>
    suspend fun folderName(treeUri: String): String
    fun canUndo(): Boolean
}

class DefaultFileOperationRepository(
    private val gateway: DocumentGateway,
    private val logStore: OperationLogStore,
    private val scanner: DirectoryScanner = DirectoryScanner(gateway),
    private val mediaPlanner: MediaOrganizationPlanner = MediaOrganizationPlanner(),
    private val contextResolver: LibraryContextResolver = LibraryContextResolver(),
    private val toolPlanner: FileToolPlanner = FileToolPlanner(),
    private val executor: OperationExecutor = OperationExecutor(gateway),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime,
    private val debugLoggingEnabled: Boolean = false,
) : FileOperationRepository {
    override suspend fun preview(
        request: OperationRequest,
        onProgress: (OrganizationProgress) -> Unit,
    ): Result<OperationResult> = storageResult {
        gateway.resetOperationCounters()
        val reporter = ProgressReporter(onProgress)
        val scanStarted = nanoTime()
        val sourceScan = scanner.scan(
            treeUri = request.sourceTreeUri,
            maximumDepth = if (request.mode == OperationMode.ORGANIZE_MEDIA) {
                request.maximumScanDepth.coerceIn(0, MAXIMUM_ALLOWED_SCAN_DEPTH)
            } else {
                0
            },
            onProgress = { progress ->
                reporter.scanning(
                    filesDiscovered = progress.filesScanned,
                    directoriesDiscovered = progress.directoriesScanned,
                    currentItem = progress.currentPath,
                    moviesDetected = progress.moviesDetected,
                    episodesDetected = progress.episodesDetected,
                    subtitlesDetected = progress.subtitlesDetected,
                )
            },
        )
        val scanDurationMillis = nanosToMillis(nanoTime() - scanStarted)
        val destinationTree = request.destinationTreeUri ?: request.sourceTreeUri
        val planningStarted = nanoTime()
        val plan = when (request.mode) {
            OperationMode.ORGANIZE_MEDIA -> {
                val sameTree = destinationTree == request.sourceTreeUri
                val existingPaths = sourceScan.index.files.mapTo(mutableSetOf()) { it.sourcePath }
                val contextIndex = if (sameTree) {
                    sourceScan.index
                } else {
                    sourceScan.index.copy(root = gateway.root(destinationTree))
                }
                val context = contextResolver.resolve(
                    index = contextIndex,
                    movieFolderName = request.movieFolderName,
                    tvFolderName = request.tvFolderName,
                )
                val initialPlan = mediaPlanner.plan(
                    index = sourceScan.index,
                    destinationTreeUri = destinationTree,
                    existingDestinationPaths = existingPaths,
                    sourceAndDestinationAreSame = sameTree,
                    movieFolderName = request.movieFolderName,
                    tvFolderName = request.tvFolderName,
                    scanWarnings = sourceScan.warnings,
                    libraryContext = context,
                    progressReporter = reporter,
                    movieLibraryTreeUri = request.movieLibraryTreeUri,
                    tvLibraryTreeUri = request.tvLibraryTreeUri,
                    sourceTreeUri = request.sourceTreeUri,
                )
                revalidateExternalDestinations(initialPlan)
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
        val metrics = PerformanceMetrics(
            scanDurationMillis = scanDurationMillis,
            matchingPlanningDurationMillis = nanosToMillis(nanoTime() - planningStarted),
            providerOperations = gateway.operationCounters(),
        )
        val measuredPlan = plan.copy(performanceMetrics = metrics)
        logStore.replace(
            operation = "PREVIEW",
            lines = measuredPlan.operations.map {
                "${it.operationType}: ${it.sourcePath} -> ${it.destinationPath} [${it.status}]"
            },
        )
        logMetrics("preview", metrics)
        OperationResult(
            plan = measuredPlan,
            skippedOperations = measuredPlan.operations.filter { it.status != OperationStatus.READY },
            warnings = measuredPlan.scanWarnings.map { "${it.path}: ${it.reason}" },
            performanceMetrics = metrics,
        )
    }

    override suspend fun execute(
        plan: OperationPlan,
        onProgress: (OrganizationProgress) -> Unit,
    ): Result<OperationResult> = storageResult {
        if (plan.hasBlockingConflicts) {
            throw DataError.InvalidPlan("Resolve all blocking conflicts before applying changes.")
        }
        val result = executor.execute(plan, ProgressReporter(onProgress))
        logStore.replace(
            operation = "RUN",
            lines = buildList {
                result.completedOperations.forEach {
                    add("COMPLETED: ${it.sourcePath} -> ${it.destinationPath}")
                }
                result.skippedOperations.forEach { add("SKIPPED: ${it.sourcePath} [${it.status}]") }
                result.failedOperations.forEach { add("FAILED: ${it.operation.sourcePath}: ${it.reason}") }
                add("CLEANED: ${result.cleanedDirectoryCount} empty folder(s)")
            },
        )
        logMetrics("run", result.performanceMetrics)
        result
    }

    override suspend fun resolveLibraryContext(
        plan: OperationPlan,
        contextType: LibraryContextType,
    ): Result<OperationResult> = storageResult {
        require(contextType != LibraryContextType.AMBIGUOUS) { "Choose a concrete library context." }
        val index = requireNotNull(plan.mediaIndex) { "The indexed preview is no longer available." }
        val replanned = mediaPlanner.plan(
            index = index,
            destinationTreeUri = plan.destinationTreeUri,
            existingDestinationPaths = plan.existingDestinationPaths,
            sourceAndDestinationAreSame = plan.sourceAndDestinationAreSame,
            movieFolderName = plan.movieFolderName,
            tvFolderName = plan.tvFolderName,
            scanWarnings = plan.scanWarnings,
            libraryContext = LibraryContext(
                type = contextType,
                selectedRootName = index.root.name,
                selectedRootIsSeason = plan.libraryContext?.selectedRootIsSeason == true,
                reason = "Selected once by the user after ambiguous context detection.",
            ),
            movieLibraryTreeUri = plan.movieLibraryTreeUri,
            tvLibraryTreeUri = plan.tvLibraryTreeUri,
            sourceTreeUri = plan.sourceTreeUri.takeIf(String::isNotBlank),
        ).copy(performanceMetrics = plan.performanceMetrics)
        val resolvedPlan = revalidateExternalDestinations(replanned)
        OperationResult(
            plan = resolvedPlan,
            skippedOperations = resolvedPlan.operations.filter { it.status != OperationStatus.READY },
            warnings = resolvedPlan.scanWarnings.map { "${it.path}: ${it.reason}" },
            performanceMetrics = resolvedPlan.performanceMetrics,
        )
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

    override suspend fun folderName(treeUri: String): String = withContext(ioDispatcher) {
        gateway.displayName(treeUri)
    }

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
        } catch (error: UnsupportedOperationException) {
            Result.failure(DataError.Storage(error))
        }
    }

    private fun logMetrics(operation: String, metrics: PerformanceMetrics) {
        if (!debugLoggingEnabled) return
        Log.d(
            LOG_TAG,
            "$operation scan=${metrics.scanDurationMillis}ms " +
                "planning=${metrics.matchingPlanningDurationMillis}ms " +
                "organization=${metrics.organizationDurationMillis}ms " +
                "cleanup=${metrics.cleanupDurationMillis}ms " +
                "queries=${metrics.providerOperations.queries} moves=${metrics.providerOperations.moves} " +
                "renames=${metrics.providerOperations.renames} fallbacks=${metrics.providerOperations.copyDeleteFallbacks} " +
                "failed=${metrics.failedOperations} skipped=${metrics.skippedOperations}",
        )
    }

    private fun revalidateExternalDestinations(plan: OperationPlan): OperationPlan {
        val occupiedDestinations = plan.operations
            .asSequence()
            .filter { it.status == OperationStatus.READY }
            .filter {
                plan.sourceTreeUri.isBlank() || it.destinationTreeUri != plan.sourceTreeUri
            }
            .filter { gateway.destinationExists(it.destinationTreeUri, it.destinationPath) }
            .map { destinationIdentity(it.destinationTreeUri, it.destinationPath) }
            .toSet()
        val existingDestinations = plan.existingDestinationPaths + occupiedDestinations
        return plan.copy(
            operations = ConflictDetector.detect(
                operations = plan.operations,
                existingDestinationPaths = existingDestinations,
                sourceAndDestinationAreSame = plan.sourceAndDestinationAreSame,
                sourceTreeUri = plan.sourceTreeUri.takeIf(String::isNotBlank),
            ),
            existingDestinationPaths = existingDestinations,
        )
    }

    private fun destinationIdentity(treeUri: String, path: String): String = "$treeUri\u0000$path"

    private fun nanosToMillis(value: Long): Long = value.coerceAtLeast(0) / 1_000_000L

    private companion object {
        const val MAXIMUM_ALLOWED_SCAN_DEPTH = 20
        const val LOG_TAG = "MediaPipeline"
    }
}
