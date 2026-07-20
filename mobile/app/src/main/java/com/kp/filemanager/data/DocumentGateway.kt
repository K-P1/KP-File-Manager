/** mobile/app/src/main/java/com/kp/filemanager/data/DocumentGateway.kt: Defines SAF operations behind a testable storage boundary; created for mobile file access. */
package com.kp.filemanager.data

import com.kp.filemanager.domain.FileCandidate
import com.kp.filemanager.domain.PlannedChange

data class DocumentMutation(
    val currentUri: String,
    val currentParentUri: String,
    val originalParentUri: String,
    val originalName: String,
)

interface DocumentGateway {
    fun displayName(treeUri: String): String
    fun listCandidates(treeUri: String, includeAudioMetadata: Boolean): List<FileCandidate>
    fun ensureDirectory(treeUri: String, name: String): String
    fun renameBatch(changes: List<PlannedChange>): Pair<List<DocumentMutation>, List<String>>
    fun move(change: PlannedChange, targetParentUri: String): DocumentMutation
    fun undo(mutation: DocumentMutation): DocumentMutation
}
