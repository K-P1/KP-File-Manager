/** mobile/app/src/test/java/com/kp/filemanager/data/SafTransferStrategyTest.kt: Verifies same-provider move selection and safe verified copy-delete fallback behavior; added for media workflow optimization. */
package com.kp.filemanager.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafTransferStrategyTest {
    @Test
    fun `same provider direct move is attempted until authority is known unsupported`() {
        val policy = ProviderMovePolicy()
        val source = "content://provider/doc/source"
        val destination = "content://provider/doc/destination"

        assertTrue(policy.shouldAttemptDirectMove(source, destination, apiLevel = 36))
        policy.recordUnsupported("provider")
        assertFalse(policy.shouldAttemptDirectMove(source, destination, apiLevel = 36))
        assertFalse(
            policy.shouldAttemptDirectMove(
                source,
                "content://another/doc/destination",
                apiLevel = 36,
            ),
        )
    }

    @Test
    fun `unsupported move fallback copies to final destination verifies size then deletes source`() {
        val backend = FakeTransferBackend()
        val transfer = VerifiedCopyDelete(backend)

        val target = transfer.transfer(
            sourceUri = "source",
            targetParentUri = "destination",
            targetName = "Movie.mkv",
        )

        assertEquals("destination/Movie.mkv", target)
        assertFalse("source" in backend.documents)
        assertEquals(10L, backend.documents[target])
        assertEquals(listOf(target), backend.createdTargets)
    }

    @Test
    fun `fallback retains source and removes incomplete destination when copy fails`() {
        val backend = FakeTransferBackend(failCopy = true)
        val transfer = VerifiedCopyDelete(backend)

        try {
            transfer.transfer("source", "destination", "Movie.mkv")
        } catch (_: IOException) {
            // Expected.
        }

        assertTrue("source" in backend.documents)
        assertFalse("destination/Movie.mkv" in backend.documents)
    }

    private class FakeTransferBackend(
        private val failCopy: Boolean = false,
    ) : DocumentTransferBackend {
        val documents = mutableMapOf("source" to 10L)
        val createdTargets = mutableListOf<String>()

        override fun metadata(uri: String) = TransferMetadata(
            name = uri.substringAfterLast('/'),
            mimeType = "video/x-matroska",
            size = documents[uri],
        )

        override fun createFinalDocument(parentUri: String, mimeType: String, name: String): String {
            val target = "$parentUri/$name"
            documents[target] = 0
            createdTargets += target
            return target
        }

        override fun copy(sourceUri: String, targetUri: String) {
            if (failCopy) throw IOException("copy failed")
            documents[targetUri] = documents.getValue(sourceUri)
        }

        override fun exists(uri: String) = uri in documents
        override fun size(uri: String) = documents[uri]
        override fun delete(uri: String) = documents.remove(uri) != null
    }
}
