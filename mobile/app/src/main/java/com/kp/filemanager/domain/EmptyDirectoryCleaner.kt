/** mobile/app/src/main/java/com/kp/filemanager/domain/EmptyDirectoryCleaner.kt: Deletes every verified-empty indexed source directory bottom-up while preserving roots and destinations; last changed to clean safely after partial relocation. */
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
        onProcessed: (IndexedDirectory, Boolean) -> Unit = { _, _ -> },
    ): CleanupResult {
        var deleted = 0
        val failures = mutableListOf<String>()
        index.directories
            .asSequence()
            .filter { it.uri != selectedRootUri && it.uri !in protectedDirectoryUris }
            .sortedWith(compareByDescending<IndexedDirectory> { it.depth }.thenByDescending { it.path })
            .forEach { directory ->
                try {
                    val wasDeleted = gateway.deleteDirectoryIfEmpty(directory.uri, directory.parentUri)
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
