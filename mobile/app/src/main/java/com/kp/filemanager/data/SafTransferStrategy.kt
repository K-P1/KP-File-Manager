/** mobile/app/src/main/java/com/kp/filemanager/data/SafTransferStrategy.kt: Selects same-provider native moves and provides a no-temporary-file verified copy-delete fallback; added for SAF optimization. */
package com.kp.filemanager.data

import java.io.IOException
import java.net.URI

class ProviderMovePolicy {
    private val unsupportedAuthorities = mutableSetOf<String>()

    fun shouldAttemptDirectMove(
        sourceUri: String,
        targetParentUri: String,
        apiLevel: Int,
    ): Boolean {
        if (apiLevel < MINIMUM_MOVE_API) return false
        val sourceAuthority = authority(sourceUri) ?: return false
        return sourceAuthority == authority(targetParentUri) && sourceAuthority !in unsupportedAuthorities
    }

    fun recordUnsupported(authority: String?) {
        authority?.takeIf(String::isNotBlank)?.let(unsupportedAuthorities::add)
    }

    fun authority(uri: String): String? = runCatching { URI(uri).authority }.getOrNull()

    private companion object {
        const val MINIMUM_MOVE_API = 24
    }
}

data class TransferMetadata(
    val name: String,
    val mimeType: String,
    val size: Long?,
)

interface DocumentTransferBackend {
    fun metadata(uri: String): TransferMetadata
    fun createFinalDocument(parentUri: String, mimeType: String, name: String): String
    fun copy(sourceUri: String, targetUri: String)
    fun exists(uri: String): Boolean
    fun size(uri: String): Long?
    fun delete(uri: String): Boolean
}

class VerifiedCopyDelete(
    private val backend: DocumentTransferBackend,
) {
    fun transfer(
        sourceUri: String,
        targetParentUri: String,
        targetName: String,
    ): String {
        val source = backend.metadata(sourceUri)
        val target = backend.createFinalDocument(
            parentUri = targetParentUri,
            mimeType = source.mimeType.ifBlank { DEFAULT_MIME_TYPE },
            name = targetName,
        )
        var destinationVerified = false
        try {
            backend.copy(sourceUri, target)
            check(backend.exists(target)) { "The copied destination could not be verified." }
            val destinationSize = backend.size(target)
            if (source.size != null && source.size >= 0 && destinationSize != source.size) {
                throw IOException("The copied destination size does not match the source.")
            }
            destinationVerified = true
            if (!backend.delete(sourceUri)) {
                throw IOException("The source could not be deleted after the verified copy.")
            }
        } catch (error: Exception) {
            if (!destinationVerified || backend.exists(sourceUri)) {
                runCatching { backend.delete(target) }
            } else {
                return target
            }
            throw error
        }
        return target
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
