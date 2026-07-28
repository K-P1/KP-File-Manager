/** mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt: Deletes indexed source directories bottom-up only after successful relocation while preserving protected roots and destinations; added for safe cleanup. */
package com.kp.filemanager.domain

import java.io.IOException

data class CleanupResult(
    val deletedCount: Int = 0,
    val skipped: Boolean = false,
    val failures: List<String> = emptyList(),
)

class EmptyDirectoryCleaner(
    private val gateway: DirectoryCleanupGateway,
) {
    fun clean(
        index: MediaIndex,
        selectedRootUri: String,
        protectedDirectoryUris: Set<String>,
        relocationFailures: Int,
        onProcessed: (IndexedDirectory, Boolean) -> Unit = { _, _ -> },
    ): CleanupResult {
        if (relocationFailures > 0) {
            return CleanupResult(
                skipped = true,
                failures = listOf("Empty-folder cleanup was skipped because one or more files could not be relocated."),
            )
        }

        var deleted = 0
        val failures = mutableListOf<String>()
        index.directories
            .asSequence()
            .filter { it.uri != selectedRootUri && it.uri !in protectedDirectoryUris }
            .sortedWith(compareByDescending<IndexedDirectory> { it.depth }.thenByDescending { it.path })
            .forEach { directory ->
                try {
                    val wasDeleted = gateway.deleteDirectoryIfEmpty(directory.uri)
                    if (wasDeleted) {
                        deleted += 1
                    }
                    onProcessed(directory, wasDeleted)
                } catch (error: SecurityException) {
                    failures += "${directory.path}: access denied during cleanup."
                    onProcessed(directory, false)
                } catch (error: IllegalArgumentException) {
                    failures += "${directory.path}: the provider rejected cleanup."
                    onProcessed(directory, false)
                } catch (error: IllegalStateException) {
                    failures += "${directory.path}: the folder state changed before cleanup."
                    onProcessed(directory, false)
                } catch (error: IOException) {
                    failures += "${directory.path}: the provider could not complete cleanup."
                    onProcessed(directory, false)
                }
            }
        return CleanupResult(deletedCount = deleted, failures = failures)
    }
}
