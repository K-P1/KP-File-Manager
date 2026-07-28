/** mobile/app/src/test/java/com/kp/filemanager/data/OperationExecutorTest.kt: Verifies execution, shared-directory caching, failure isolation, and reverse-order undo; updated for pipeline optimization. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.MediaFileType
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationExecutorTest {
    @Test
    fun `executor applies the approved plan without reparsing and isolates failures`() {
        val gateway = FakeMutationGateway(existingSources = mutableSetOf("source-a", "source-b"))
        gateway.failOnApply += "source-b"
        val plan = plan(operation("source-a", "A.mkv"), operation("source-b", "B.mkv"))

        val result = OperationExecutor(gateway).execute(plan)

        assertEquals(listOf("source-a"), gateway.appliedSources)
        assertEquals(1, result.completedCount)
        assertEquals(1, result.failedOperations.size)
        assertEquals(
            listOf(OperationStatus.COMPLETED, OperationStatus.FAILED),
            result.plan.operations.map(PlannedFileOperation::status),
        )
    }

    @Test
    fun `executor blocks a destination that appeared after preview`() {
        val gateway = FakeMutationGateway(existingSources = mutableSetOf("source-a"))
        gateway.destinations += "Movies/A/A.mkv"
        val operation = operation("source-a", "A.mkv")

        val result = OperationExecutor(gateway).execute(plan(operation))

        assertTrue(result.completedOperations.isEmpty())
        assertEquals("The destination now exists.", result.failedOperations.single().reason)
        assertTrue(gateway.appliedSources.isEmpty())
    }

    @Test
    fun `undo restores successful operations in reverse order and removes only empty created folders`() {
        val gateway = FakeMutationGateway(existingSources = mutableSetOf("source-a", "source-b"))
        val executor = OperationExecutor(gateway)
        executor.execute(plan(operation("source-a", "A.mkv"), operation("source-b", "B.mkv")))

        val result = executor.undoLast()

        assertEquals(listOf("source-b", "source-a"), gateway.undoneSources)
        assertEquals(2, result.completedCount)
        assertFalse(executor.canUndo())
        assertTrue(gateway.cleanedDirectories.isNotEmpty())
    }

    @Test
    fun `failed undo entries remain available for retry`() {
        val gateway = FakeMutationGateway(existingSources = mutableSetOf("source-a", "source-b"))
        val executor = OperationExecutor(gateway)
        executor.execute(plan(operation("source-a", "A.mkv"), operation("source-b", "B.mkv")))
        gateway.failOnUndo += "source-a"

        val firstUndo = executor.undoLast()

        assertEquals(1, firstUndo.failedOperations.size)
        assertTrue(executor.canUndo())
        gateway.failOnUndo.clear()
        val retry = executor.undoLast()
        assertEquals(1, retry.completedCount)
        assertFalse(executor.canUndo())
    }

    @Test
    fun `executor resolves a shared destination directory only once`() {
        val gateway = FakeMutationGateway(existingSources = mutableSetOf("source-a", "source-b"))
        val executor = OperationExecutor(gateway)

        executor.execute(plan(operation("source-a", "A.mkv"), operation("source-b", "B.mkv")))

        assertEquals(listOf("Movies/A"), gateway.preparedDirectoryPaths)
    }

    private fun operation(uri: String, newName: String) = PlannedFileOperation(
        sourceUri = uri,
        sourceParentUri = "root",
        sourcePath = "$uri.mkv",
        destinationTreeUri = "destination",
        destinationPath = "Movies/A/$newName",
        originalName = "$uri.mkv",
        newName = newName,
        mediaType = MediaFileType.VIDEO,
        operationType = OperationType.MOVE_AND_RENAME,
        reason = "test",
        status = OperationStatus.READY,
    )

    private fun plan(vararg operations: PlannedFileOperation) =
        OperationPlan("destination", operations.toList())

    private class FakeMutationGateway(
        private val existingSources: MutableSet<String>,
    ) : DocumentMutationGateway {
        val destinations = mutableSetOf<String>()
        val failOnApply = mutableSetOf<String>()
        val failOnUndo = mutableSetOf<String>()
        val appliedSources = mutableListOf<String>()
        val undoneSources = mutableListOf<String>()
        val cleanedDirectories = mutableListOf<String>()
        val preparedDirectoryPaths = mutableListOf<String>()

        override fun documentExists(uri: String) = uri in existingSources

        override fun documentExistsInParent(uri: String, parentUri: String) = uri in existingSources

        override fun destinationExists(treeUri: String, relativePath: String, excludingUri: String?) =
            relativePath in destinations

        override fun ensureDirectoryPath(treeUri: String, relativePath: String): PreparedDirectory {
            preparedDirectoryPaths += relativePath
            return PreparedDirectory("prepared:$relativePath", listOf("created:$relativePath"))
        }

        override fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation {
            if (operation.sourceUri in failOnApply) error("provider failure")
            appliedSources += operation.sourceUri
            existingSources -= operation.sourceUri
            destinations += operation.destinationPath
            return DocumentMutation(
                beforeUri = operation.sourceUri,
                beforeParentUri = operation.sourceParentUri,
                beforeName = operation.originalName,
                afterUri = "after:${operation.sourceUri}",
                afterParentUri = targetParentUri,
                afterName = operation.newName,
                createdDirectoryUris = listOf("created:${operation.destinationDirectoryPath}"),
                operation = operation,
            )
        }

        override fun undo(mutation: DocumentMutation) {
            if (mutation.beforeUri in failOnUndo) error("restore failure")
            undoneSources += mutation.beforeUri
            existingSources += mutation.beforeUri
            destinations -= mutation.operation.destinationPath
        }

        override fun deleteDirectoryIfEmpty(uri: String): Boolean {
            cleanedDirectories += uri
            return true
        }
    }
}
