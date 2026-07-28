/** mobile/app/src/test/java/com/kp/filemanager/domain/EmptyDirectoryCleanerTest.kt: Verifies exhaustive bottom-up cleanup and exclusions; last changed to cover parent-aware ancestor deletion. */
package com.kp.filemanager.domain

import com.kp.filemanager.data.DocumentMutation
import com.kp.filemanager.data.DocumentMutationGateway
import com.kp.filemanager.data.PreparedDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )

        assertEquals(listOf("child", "parent", "non-empty"), gateway.deleteAttempts)
        assertEquals(listOf("child", "parent"), gateway.deletedDirectories)
        assertEquals(2, result.deletedCount)
    }

    @Test
    fun `cleanup always removes verified empty directories when invoked`() {
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
        )

        assertFalse(result.skipped)
        assertEquals(listOf("child"), gateway.deleteAttempts)
        assertEquals(listOf("child"), gateway.deletedDirectories)
    }

    private class CleanerGateway : DocumentMutationGateway {
        val deleteAttempts = mutableListOf<String>()
        val deletedDirectories = mutableListOf<String>()
        private val children = mutableMapOf(
            "parent" to mutableSetOf("child"),
            "non-empty" to mutableSetOf("unrelated-file"),
        )

        override fun documentExists(uri: String) = true
        override fun documentExistsInParent(uri: String, parentUri: String) = true
        override fun destinationExists(treeUri: String, relativePath: String, excludingUri: String?) = false
        override fun ensureDirectoryPath(treeUri: String, relativePath: String) = PreparedDirectory(treeUri, emptyList())
        override fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation =
            error("Not used")
        override fun undo(mutation: DocumentMutation) = Unit
        override fun deleteDirectoryIfEmpty(uri: String, parentUri: String?): Boolean {
            deleteAttempts += uri
            if (children[uri].orEmpty().isNotEmpty()) return false
            deletedDirectories += uri
            parentUri?.let { children[it]?.remove(uri) }
            return true
        }
    }
}
