/** mobile/app/src/main/java/com/kp/filemanager/data/OperationExecutor.kt: Revalidates and applies approved plans while maintaining the latest in-memory reverse-order undo journal; created for the media refactor. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.OperationFailure
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationResult
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import kotlinx.coroutines.CancellationException

class OperationExecutor(
    private val gateway: DocumentMutationGateway,
) {
    private var lastJournal: UndoJournal? = null

    fun execute(plan: OperationPlan): OperationResult {
        val completed = mutableListOf<PlannedFileOperation>()
        val failures = mutableListOf<OperationFailure>()
        val skipped = plan.operations.filter { it.status != OperationStatus.READY }
        val mutations = mutableListOf<DocumentMutation>()

        plan.operations.filter { it.status == OperationStatus.READY }.forEach { operation ->
            val validationFailure = validate(operation)
            if (validationFailure != null) {
                failures += OperationFailure(operation, validationFailure)
                return@forEach
            }
            var prepared: PreparedDirectory? = null
            try {
                prepared = gateway.ensureDirectoryPath(
                    treeUri = operation.destinationTreeUri,
                    relativePath = operation.destinationDirectoryPath,
                )
                val mutation = gateway.apply(operation, prepared.uri)
                    .copy(createdDirectoryUris = prepared.createdDirectoryUris)
                mutations += mutation
                completed += operation.copy(status = OperationStatus.COMPLETED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider denied this change.")
            } catch (error: IllegalStateException) {
                cleanup(prepared)
                failures += OperationFailure(operation, safeMessage(error))
            } catch (error: IllegalArgumentException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider rejected this change.")
            } catch (error: UnsupportedOperationException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider does not support this change.")
            } catch (error: java.io.IOException) {
                cleanup(prepared)
                failures += OperationFailure(operation, "The document provider could not complete this change.")
            }
        }

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
        return OperationResult(
            plan = resultPlan,
            completedOperations = completed,
            failedOperations = failures,
            skippedOperations = skipped,
            warnings = plan.scanWarnings.map { "${it.path}: ${it.reason}" },
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
                mutation.createdDirectoryUris.asReversed().forEach(gateway::deleteDirectoryIfEmpty)
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
        prepared?.createdDirectoryUris?.asReversed()?.forEach(gateway::deleteDirectoryIfEmpty)
    }

    private fun safeMessage(error: IllegalStateException): String =
        error.message?.take(160) ?: "The filesystem state changed before this operation could run."

    private data class UndoJournal(
        val plan: OperationPlan,
        val mutations: List<DocumentMutation>,
    )
}
