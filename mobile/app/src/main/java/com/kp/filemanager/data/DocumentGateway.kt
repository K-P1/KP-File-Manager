/** mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt: Defines read-only tree discovery and exact, overwrite-safe document mutations behind testable boundaries; refactored for media organization. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.DocumentTreeSource
import com.kp.filemanager.domain.DirectoryCleanupGateway
import com.kp.filemanager.domain.ProviderOperationCounters
import com.kp.filemanager.domain.PlannedFileOperation

data class PreparedDirectory(
    val uri: String,
    val createdDirectoryUris: List<String>,
)

data class DocumentMutation(
    val beforeUri: String,
    val beforeParentUri: String,
    val beforeName: String,
    val afterUri: String,
    val afterParentUri: String,
    val afterName: String,
    val createdDirectoryUris: List<String>,
    val operation: PlannedFileOperation,
    val beforeTreeUri: String = "",
    val beforeParentPath: String = "",
)

interface DocumentMutationGateway : DirectoryCleanupGateway {
    fun documentExists(uri: String): Boolean
    fun documentExistsInParent(uri: String, parentUri: String): Boolean
    fun destinationExists(treeUri: String, relativePath: String, excludingUri: String? = null): Boolean
    fun ensureDirectoryPath(treeUri: String, relativePath: String): PreparedDirectory
    fun apply(operation: PlannedFileOperation, targetParentUri: String): DocumentMutation
    fun undo(mutation: DocumentMutation)
    fun operationCounters(): ProviderOperationCounters = ProviderOperationCounters()
    fun resetOperationCounters() = Unit
}

interface DocumentGateway : DocumentTreeSource, DocumentMutationGateway {
    fun displayName(treeUri: String): String
}
