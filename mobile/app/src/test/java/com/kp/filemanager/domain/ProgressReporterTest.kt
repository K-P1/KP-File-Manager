/** mobile/app/src/test/java/com/kp/filemanager/domain/ProgressReporterTest.kt: Verifies determinate progress counters, failures, remaining work, and stable ETA sampling; added for media workflow optimization. */
package com.kp.filemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReporterTest {
    @Test
    fun `determinate progress reports counters percentage remaining failures and eta`() {
        var now = 0L
        val states = mutableListOf<OrganizationProgress>()
        val reporter = ProgressReporter(
            emit = states::add,
            nanoTime = { now },
            wallClockMillis = { 1_000L + now / 1_000_000L },
        )

        reporter.begin(OrganizationPhase.ORGANIZING_FILES, totalItems = 4)
        repeat(3) { index ->
            reporter.currentItem("File $index")
            now += 1_000_000_000L
            reporter.completeItem(
                operationType = if (index == 0) OperationType.MOVE else OperationType.RENAME,
                failed = index == 2,
            )
        }

        val progress = states.last()
        assertFalse(progress.isIndeterminate)
        assertEquals(3, progress.processedItems)
        assertEquals(4, progress.totalItems)
        assertEquals(1, progress.remainingItems)
        assertEquals(75, progress.percent)
        assertEquals(1, progress.movedCount)
        assertEquals(1, progress.renamedCount)
        assertEquals(1, progress.failedCount)
        assertTrue(progress.etaMillis != null && progress.etaMillis!! > 0)
    }

    @Test
    fun `scanning is indeterminate and eta is hidden until enough samples exist`() {
        val states = mutableListOf<OrganizationProgress>()
        val reporter = ProgressReporter(states::add)

        reporter.scanning(filesDiscovered = 5, directoriesDiscovered = 2, currentItem = "Folder/File.mkv")
        assertTrue(states.last().isIndeterminate)
        assertNull(states.last().percent)

        reporter.begin(OrganizationPhase.PLANNING, totalItems = 2)
        reporter.completeItem(OperationType.MOVE)
        assertNull(states.last().etaMillis)
    }
}
