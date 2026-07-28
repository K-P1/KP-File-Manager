/** mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt: Executes plans, requires exhaustive old-folder cleanup for completion, and maintains session undo; last changed for reliable ancestor deletion. */
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
        val preparedDirectories = mutableMapOf<String, PreparedDirectory>()
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
                prepared = preparedDirectories[operation.destinationDirectoryPath]
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
                completed += operation.copy(status = OperationStatus.COMPLETED)
                preparedDirectories[operation.destinationDirectoryPath] = prepared.copy(
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

        if (mutations.isNotEmpty()) lastJournal = UndoJournal(plan, mutations)
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
        val remaining = mutableListOf<DocumentMutation>()

        journal.mutations.asReversed().forEach { mutation ->
            try {
                gateway.undo(mutation)
                mutation.createdDirectoryUris.asReversed().forEach {
                    gateway.deleteDirectoryIfEmpty(it, null)
                }
                restored += mutation.operation.copy(status = OperationStatus.COMPLETED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                remaining += mutation
                failures += OperationFailure(mutation.operation, "The document provider denied this restore.")
            } catch (error: IllegalStateException) {
                remaining += mutation
                failures += OperationFailure(mutation.operation, safeMessage(error))
            } catch (error: IllegalArgumentException) {
                remaining += mutation
                failures += OperationFailure(mutation.operation, "The document provider rejected this restore.")
            } catch (error: UnsupportedOperationException) {
                remaining += mutation
                failures += OperationFailure(mutation.operation, "The document provider does not support this restore.")
            } catch (error: java.io.IOException) {
                remaining += mutation
                failures += OperationFailure(mutation.operation, "The document provider could not restore this file.")
            }
        }

        lastJournal = remaining.asReversed()
            .takeIf(List<DocumentMutation>::isNotEmpty)
            ?.let { UndoJournal(journal.plan, it) }
        return OperationResult(
            plan = journal.plan,
            completedOperations = restored,
            failedOperations = failures,
        )
    }

    fun canUndo(): Boolean = lastJournal?.mutations?.isNotEmpty() == true

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

    private data class UndoJournal(
        val plan: OperationPlan,
        val mutations: List<DocumentMutation>,
    )

    private fun nanosToMillis(value: Long): Long = value.coerceAtLeast(0) / 1_000_000L
}
