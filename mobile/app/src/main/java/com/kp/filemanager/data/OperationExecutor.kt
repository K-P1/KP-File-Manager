/** mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt: Executes plans and performs retryable two-phase undo with cleanup of app-created output folders. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.OperationFailure
import com.kp.filemanager.domain.EmptyDirectoryCleaner
import com.kp.filemanager.domain.OrganizationPhase
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.PerformanceMetrics
import com.kp.filemanager.domain.ProgressReporter
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import kotlinx.coroutines.CancellationException

class OperationExecutor(
    private val gateway: DocumentMutationGateway,
    private val cleaner: EmptyDirectoryCleaner = EmptyDirectoryCleaner(gateway),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var lastJournal: UndoJournal? = null

    fun execute(
        plan: OperationPlan,
        progressReporter: ProgressReporter? = null,
    ): OperationResult {
        val completed = mutableListOf<PlannedFileOperation>()
        val failures = mutableListOf<OperationFailure>()
        val skipped = plan.operations.filter { it.status != OperationStatus.READY }
        val mutations = mutableListOf<DocumentMutation>()
        val createdDirectoryUris = mutableListOf<String>()
        val preparedDirectories = mutableMapOf<DestinationDirectoryKey, PreparedDirectory>()
        progressReporter?.begin(OrganizationPhase.ORGANIZING_FILES, plan.operations.size)
        skipped.forEach { operation ->
            progressReporter?.currentItem(operation.originalName)
            progressReporter?.completeItem(operation.operationType, skipped = true)
        }

        val organizationStarted = nanoTime()
        plan.operations.filter { it.status == OperationStatus.READY }.forEach { operation ->
            progressReporter?.currentItem(operation.originalName)
            val validationFailure = validate(operation)
            if (validationFailure != null) {
                failures += OperationFailure(operation, validationFailure)
                progressReporter?.completeItem(operation.operationType, failed = true)
                return@forEach
            }
            var prepared: PreparedDirectory? = null
            try {
                val destinationKey = DestinationDirectoryKey(
                    treeUri = operation.destinationTreeUri,
                    relativePath = operation.destinationDirectoryPath,
                )
                prepared = preparedDirectories[destinationKey]
                    ?: gateway.ensureDirectoryPath(
                        treeUri = operation.destinationTreeUri,
                        relativePath = operation.destinationDirectoryPath,
                    )
                val mutation = gateway.apply(operation, prepared.uri)
                    .copy(
                        createdDirectoryUris = prepared.createdDirectoryUris,
                        beforeTreeUri = plan.mediaIndex?.treeUri.orEmpty(),
                        beforeParentPath = operation.sourcePath.substringBeforeLast('/', ""),
                    )
                mutations += mutation
                prepared.createdDirectoryUris.forEach { uri ->
                    if (uri !in createdDirectoryUris) createdDirectoryUris += uri
                }
                completed += operation.copy(status = OperationStatus.COMPLETED)
                preparedDirectories[destinationKey] = prepared.copy(
                    createdDirectoryUris = emptyList(),
                )
                progressReporter?.completeItem(operation.operationType)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider denied this change.")
                progressReporter?.completeItem(operation.operationType, failed = true)
            } catch (error: IllegalStateException) {
                cleanup(prepared)
                failures += OperationFailure(operation, safeMessage(error))
                progressReporter?.completeItem(operation.operationType, failed = true)
            } catch (error: IllegalArgumentException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider rejected this change.")
                progressReporter?.completeItem(operation.operationType, failed = true)
            } catch (error: UnsupportedOperationException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider does not support this change.")
                progressReporter?.completeItem(operation.operationType, failed = true)
            } catch (error: java.io.IOException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider could not complete this change.")
                progressReporter?.completeItem(operation.operationType, failed = true)
            }
        }
        val organizationDurationMillis = nanosToMillis(nanoTime() - organizationStarted)

        if (mutations.isNotEmpty()) {
            lastJournal = UndoJournal(
                plan = plan,
                mutations = mutations,
                createdDirectoryUris = createdDirectoryUris,
            )
        }
        val completedBySource = completed.associateBy(PlannedFileOperation::sourceUri)
        val failuresBySource = failures.associateBy { it.operation.sourceUri }
        val resultPlan = plan.copy(
            operations = plan.operations.map { operation ->
                completedBySource[operation.sourceUri]
                    ?: failuresBySource[operation.sourceUri]?.let {
                        operation.copy(status = OperationStatus.FAILED, warnings = operation.warnings + it.reason)
                    }
                    ?: operation
            },
        )
        val cleanupStarted = nanoTime()
        val cleanupResult = plan.mediaIndex?.let { index ->
            val candidates = index.directories.count {
                it.uri != index.root.uri && it.uri !in plan.protectedDirectoryUris
            }
            progressReporter?.begin(OrganizationPhase.CLEANING_UP, candidates)
            cleaner.clean(
                index = index,
                selectedRootUri = index.root.uri,
                protectedDirectoryUris = plan.protectedDirectoryUris,
            ) { directory, deleted ->
                progressReporter?.completeCleanupCandidate(directory.path, deleted)
            }
        }
        val cleanupDurationMillis = nanosToMillis(nanoTime() - cleanupStarted)
        progressReporter?.complete()
        return OperationResult(
            plan = resultPlan,
            completedOperations = completed,
            failedOperations = failures,
            skippedOperations = skipped,
            warnings = buildList {
                addAll(plan.scanWarnings.map { "${it.path}: ${it.reason}" })
                addAll(cleanupResult?.failures.orEmpty())
            },
            cleanupFailures = cleanupResult?.failures.orEmpty(),
            cleanedDirectoryCount = cleanupResult?.deletedCount ?: 0,
            performanceMetrics = PerformanceMetrics(
                scanDurationMillis = plan.performanceMetrics.scanDurationMillis,
                matchingPlanningDurationMillis = plan.performanceMetrics.matchingPlanningDurationMillis,
                organizationDurationMillis = organizationDurationMillis,
                cleanupDurationMillis = cleanupDurationMillis,
                providerOperations = gateway.operationCounters(),
                failedOperations = failures.size,
                skippedOperations = skipped.size,
            ),
        )
    }

    fun undoLast(): OperationResult {
        val journal = lastJournal
            ?: return OperationResult(OperationPlan("", emptyList()))
        val restored = mutableListOf<PlannedFileOperation>()
        val failures = mutableListOf<OperationFailure>()
        val remainingMutations = mutableListOf<DocumentMutation>()

        journal.mutations.asReversed().forEach { mutation ->
            try {
                gateway.undo(mutation)
                restored += mutation.operation.copy(status = OperationStatus.COMPLETED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                remainingMutations += mutation
                failures += OperationFailure(mutation.operation, "The document provider denied this restore.")
            } catch (error: IllegalStateException) {
                remainingMutations += mutation
                failures += OperationFailure(mutation.operation, safeMessage(error))
            } catch (error: IllegalArgumentException) {
                remainingMutations += mutation
                failures += OperationFailure(mutation.operation, "The document provider rejected this restore.")
            } catch (error: UnsupportedOperationException) {
                remainingMutations += mutation
                failures += OperationFailure(mutation.operation, "The document provider does not support this restore.")
            } catch (error: java.io.IOException) {
                remainingMutations += mutation
                failures += OperationFailure(mutation.operation, "The document provider could not restore this file.")
            }
        }

        val cleanupResult = if (remainingMutations.isEmpty()) {
            cleanCreatedDirectories(journal.createdDirectoryUris)
        } else {
            CreatedDirectoryCleanupResult(journal.createdDirectoryUris)
        }
        val pendingMutations = remainingMutations.asReversed()
        lastJournal = if (pendingMutations.isNotEmpty() || cleanupResult.remainingUris.isNotEmpty()) {
            UndoJournal(
                plan = journal.plan,
                mutations = pendingMutations,
                createdDirectoryUris = cleanupResult.remainingUris,
            )
        } else {
            null
        }
        return OperationResult(
            plan = journal.plan,
            completedOperations = restored,
            failedOperations = failures,
            warnings = cleanupResult.failures,
            cleanupFailures = cleanupResult.failures,
            cleanedDirectoryCount = cleanupResult.deletedCount,
        )
    }

    fun canUndo(): Boolean = lastJournal?.let { journal ->
        journal.mutations.isNotEmpty() || journal.createdDirectoryUris.isNotEmpty()
    } == true

    private fun validate(operation: PlannedFileOperation): String? = when {
        operation.operationType == OperationType.SKIP -> "This operation is not executable."
        operation.hasBlockingConflict -> "Resolve the blocking conflict before applying."
        !gateway.documentExistsInParent(operation.sourceUri, operation.sourceParentUri) ->
            "The source file moved or no longer exists."
        gateway.destinationExists(operation.destinationTreeUri, operation.destinationPath, operation.sourceUri) ->
            "The destination now exists."
        else -> null
    }

    private fun cleanup(prepared: PreparedDirectory?) {
        prepared?.createdDirectoryUris?.asReversed()?.forEach {
            gateway.deleteDirectoryIfEmpty(it, null)
        }
    }

    private fun safeMessage(error: IllegalStateException): String =
        error.message?.take(160) ?: "The filesystem state changed before this operation could run."

    private fun cleanCreatedDirectories(createdDirectoryUris: List<String>): CreatedDirectoryCleanupResult {
        val remaining = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var deletedCount = 0
        createdDirectoryUris.asReversed().forEach { uri ->
            try {
                if (gateway.deleteDirectoryIfEmpty(uri, null)) {
                    deletedCount += 1
                } else {
                    remaining += uri
                    failures += "An output folder created by this operation is not empty and was left in place."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                remaining += uri
                failures += "An empty output folder created by this operation could not be removed."
            }
        }
        return CreatedDirectoryCleanupResult(
            remainingUris = remaining.asReversed(),
            failures = failures,
            deletedCount = deletedCount,
        )
    }

    private data class UndoJournal(
        val plan: OperationPlan,
        val mutations: List<DocumentMutation>,
        val createdDirectoryUris: List<String>,
    )

    private data class CreatedDirectoryCleanupResult(
        val remainingUris: List<String>,
        val failures: List<String> = emptyList(),
        val deletedCount: Int = 0,
    )

    private data class DestinationDirectoryKey(
        val treeUri: String,
        val relativePath: String,
    )

    private fun nanosToMillis(value: Long): Long = value.coerceAtLeast(0) / 1_000_000L
}
