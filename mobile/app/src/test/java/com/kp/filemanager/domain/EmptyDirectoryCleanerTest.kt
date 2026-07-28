/** mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt: Verifies bottom-up empty-folder cleanup exclusions and failure gating; added for media workflow optimization. */
package com.kp.filemanager.domain

import com.kp.filemanager.data.DocumentMutation
import com.kp.filemanager.data.DocumentMutationGateway
import com.kp.filemanager.data.PreparedDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyDirectoryCleanerTest {
    @Test
    fun `cleanup deletes indexed empty directories deepest first but never root or destination`() {
        val gateway = CleanerGateway()
        val index = MediaIndex(
            root = DocumentEntry("root", "", "Downloads", true, 0),
            directories = listOf(
                IndexedDirectory("parent", "root", "Parent", "Parent", 1),
                IndexedDirectory("child", "parent", "Parent/Child", "Child", 2),
                IndexedDirectory("destination", "root", "Movies", "Movies", 1),
                IndexedDirectory("non-empty", "root", "Keep", "Keep", 1),
            ),
            items = emptyList(),
        )

        val result = EmptyDirectoryCleaner(gateway).clean(
            index = index,
            selectedRootUri = "root",
            protectedDirectoryUris = setOf("destination"),
            relocationFailures = 0,
        )

        assertEquals(listOf("child", "parent", "non-empty"), gateway.deleteAttempts)
        assertEquals(listOf("child", "parent"), gateway.deletedDirectories)
        assertEquals(2, result.deletedCount)
    }

    @Test
    fun `cleanup is skipped after any relocation failure`() {
        val gateway = CleanerGateway()
        val index = MediaIndex(
            root = DocumentEntry("root", "", "Downloads", true, 0),
            directories = listOf(IndexedDirectory("child", "root", "Child", "Child", 1)),
            items = emptyList(),
        )

        val result = EmptyDirectoryCleaner(gateway).clean(
            index = index,
            selectedRootUri = "root",
            protectedDirectoryUris = emptySet(),
            relocationFailures = 1,
        )

        assertTrue(result.skipped)
        assertTrue(gateway.deleteAttempts.isEmpty())
    }

    private class CleanerGateway : DocumentMutationGateway {
        val deleteAttempts = mutableListOf<String>()
        val deletedDirectories = mutableListOf<String>()

        override fun documentExists(uri: String) = true
        override fun documentExistsInParent(uri: String, parentUri: String) = true
        override fun destinationExists(treeUri: String, relativePath: String, excludingUri: String?) = false
        override fun ensureDirectoryPath(treeUri: String, relativePath: String) = PreparedDirectory(treeUri, emptyList())
        override fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation =
            error("Not used")
        override fun undo(mutation: DocumentMutation) = Unit
        override fun deleteDirectoryIfEmpty(uri: String): Boolean {
            deleteAttempts += uri
            if (uri == "non-empty") return false
            deletedDirectories += uri
            return true
        }
    }
}
