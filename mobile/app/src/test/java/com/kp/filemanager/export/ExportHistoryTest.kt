/**
 * Path: mobile/app/src/test/java/com/kp/filemanager/export/ExportHistoryTest.kt
 * Purpose: Regression coverage for reversible session state and completed-file progress.
 * Last changed: 2026-11-24, cover direct path-based undo and pending restore retries.
 */
package com.kp.filemanager.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportHistoryTest {
    private val original = "${ProtectedExportPaths.VIDEO_SOURCE}/Movie.mp4"
    private val destination = "${ProtectedExportPaths.LIBRARY_ROOT}/Movies/Movie/Movie.mp4"
    private val record = ExportRecord(original, destination, 500)

    @Test fun restoreDirectionReversesPathsWithoutLosingMapping() {
        val restoring = record.copy(status = ExportRecordStatus.RESTORING)
        assertEquals(destination, restoring.origin)
        assertEquals(original, restoring.target)
        val restored = restoring.copy(status = ExportRecordStatus.RESTORED, restoredAt = 123)
        assertEquals(original, restored.source)
        assertEquals(destination, restored.destination)
    }

    @Test fun onlyExportedFilesAreUndoableAndHistoryIsRetained() {
        val restored = record.copy(id = "restored", status = ExportRecordStatus.RESTORED)
        val pending = record.copy(id = "pending", status = ExportRecordStatus.EXPORTING)
        val failed = record.copy(id = "failed", status = ExportRecordStatus.NOT_MOVED)
        val session = ExportSession("session", 100, listOf(record, restored, pending, failed))
        assertEquals(listOf(record), session.undoable)
        assertEquals(4, session.records.size)
        assertTrue(session.hasPending)
        assertFalse(session.copy(records = listOf(restored, failed)).hasPending)
        assertTrue(session.copy(records = listOf(restored), legacyTemporary = listOf("legacy partial")).hasPending)
    }

    @Test fun fullyCopiedFileDoesNotMeanFullyTransferred() {
        val copying = ExportProgress(ExportStage.COPYING, totalFiles = 1, totalBytes = 500,
            currentCopiedBytes = 500, currentFileBytes = 500)
        assertEquals(0, copying.percent)
        assertEquals(0, copying.copy(stage = ExportStage.VERIFYING).percent)
        assertEquals(100, copying.copy(stage = ExportStage.COMPLETE, completedFiles = 1,
            completedBytes = 500, currentCopiedBytes = 0).percent)
    }

    @Test fun sameSizeDifferentModificationTimeIsNotTheSameIdentity() {
        val first = ExportFileIdentity("12:34:500:2026-11-24 01:00:00.000000000 +0000")
        val second = ExportFileIdentity("12:34:500:2026-11-24 01:00:01.000000000 +0000")
        assertEquals(500L, first.size)
        assertFalse(first == second)
    }

    @Test fun pathGuardDoesNotRequireExecutePermissionProbe() {
        val parent = original.substringBeforeLast('/')
        val command = transferPathGuard(original, listOf(parent))
        assertFalse(command.contains("[ -x "))
        assertTrue(command.contains("[ ! -L ${shellQuote(parent)} ]"))
        assertTrue(command.contains("[ ! -d ${shellQuote(parent)} ]"))
        assertTrue(command.contains("[ ! -L ${shellQuote(original)} ]"))
        assertTrue(command.contains("Transfer parent is not a directory: $parent"))
        assertTrue(command.contains("exit 30"))
    }

    @Test fun pathGuardQuotesApostrophesInPathsAndDiagnostics() {
        val path = "$destination's subtitle"
        val command = transferPathGuard(path, emptyList())
        assertTrue(command.contains("[ ! -L ${shellQuote(path)} ]"))
        assertTrue(command.contains(shellQuote("Symbolic link at transfer path: $path")))
    }
    private val movies = LegacyMovieRecoveryMapping.MOVIES

    @Test fun legacyRecoveryVideoMappingRemainsValid() {
        assertEquals("${ProtectedExportPaths.VIDEO_SOURCE}/Movie (2026).MKV",
            LegacyMovieRecoveryMapping.target("$movies/Movie (2026)/Movie (2026).MKV"))
        assertEquals("${ProtectedExportPaths.VIDEO_SOURCE}/Movie.mp4", LegacyMovieRecoveryMapping.target("$movies/Movie.mp4"))
    }

    @Test fun legacyRecoverySubtitleMappingRemainsValid() {
        assertEquals("${ProtectedExportPaths.SUBTITLE_SOURCE}/Film.eng.srt",
            LegacyMovieRecoveryMapping.target("$movies/Film/Film.eng.srt"))
    }

    @Test fun legacyRecoveryMappingRejectsUnsafePaths() {
        listOf("$movies/../escape.mp4", "$movies/Film/.partial.mp4", "$movies/Film/cover.jpg",
            "$movies/Film/a\\b.mp4", "$movies/Film/a\nb.mp4", "$movies//Film.mp4",
            "${ProtectedExportPaths.LIBRARY_ROOT}/TV Shows/Film.mp4", "$movies-other/Film.mp4").forEach {
            assertEquals(null, LegacyMovieRecoveryMapping.target(it))
        }
    }

    @Test fun metadataProbeUsesStatRatherThanAccessPredicates() {
        val command = transferMetadataCommand(destination)
        assertTrue(command.contains("LC_ALL=C TZ=UTC stat"))
        assertFalse(command.contains("[ -f "))
        assertFalse(command.contains("[ -e "))
        assertTrue(command.contains(": No such file or directory"))
        assertTrue(command.contains("exit 32"))
        assertTrue(command.contains(shellQuote("Cannot stat $destination")))
    }

    @Test fun metadataProbeQuotesPathsAndRejectsNonRegularFiles() {
        val path = "$destination's subtitle"
        val command = transferMetadataCommand(path)
        assertTrue(command.contains(shellQuote(path)))
        assertTrue(command.contains("regular file|"))
        assertTrue(command.contains("regular empty file|"))
        assertTrue(command.contains("printf '%s\\n' \"\$meta\""))
        assertFalse(command.contains("\${meta#*|}"))
        assertTrue(command.contains(shellQuote("Not a regular file: $path")))
    }
    @Test fun metadataParserPreservesSignedHexAndPaddedFields() {
        val raw = "-123:0xABC: 500 :2026-11-24 01:00:00.000000000 +0000"
        val parsed = requireNotNull(decodeTransferIdentity(raw, destination))
        assertEquals(raw, parsed.value)
        assertEquals(500L, parsed.size)
        assertEquals(null, decodeTransferIdentity("ABSENT", destination))
        assertEquals(500L, decodeTransferIdentity("12:18446744073709551615:500:2026-11-24 01:00:00 +0000", destination)?.size)
    }

    @Test fun deviceMetadataWithFileTypeUsesExistingJournalIdentity() {
        val payload = "185:370708:360916857:2026-11-24 01:05:17.263565939 +0000"
        val actual = "regular file|$payload"
        val parsed = requireNotNull(decodeTransferIdentity(actual, destination))
        assertEquals(360916857L, parsed.size)
        assertEquals(ExportFileIdentity(payload), parsed)
        assertEquals(parsed, decodeTransferIdentity(payload, destination))
        val assertion = transferIdentityAssertion(parsed)
        assertTrue(assertion.contains(shellQuote(actual)))
        assertTrue(assertion.contains("exit 33"))
    }

    @Test fun typedEmptyFilesAndInvalidTypesAreHandledSafely() {
        val payload = "185:370708:0:2026-11-24 01:05:17.263565939 +0000"
        val parsed = requireNotNull(decodeTransferIdentity("regular empty file|$payload", destination))
        assertEquals(0L, parsed.size)
        assertEquals(ExportFileIdentity(payload), parsed)
        assertTrue(transferIdentityAssertion(parsed).contains(shellQuote("regular empty file|$payload")))
        listOf("directory|$payload", "symbolic link|$payload", "regular file|ABSENT",
            "regular file|regular file|$payload").forEach { raw ->
            assertTrue(runCatching { decodeTransferIdentity(raw, destination) }.isFailure)
        }
    }

    @Test fun malformedMetadataReportsPathAndPayload() {
        listOf("12:34:?:time", "12:34:-1:time", "12:34:500:?", "invalid").forEach { raw ->
            val error = runCatching { decodeTransferIdentity(raw, destination) }.exceptionOrNull()
            assertTrue(error?.message?.contains(destination) == true)
            assertTrue(error?.message?.contains(raw) == true)
        }
    }

    @Test fun renamedFileMatchingUsesBothJournalNamesInTheRecordedFolder() {
        val movie = ExportRecord("${ProtectedExportPaths.VIDEO_SOURCE}/13_Hours_1080P.mp4",
            "${ProtectedExportPaths.LIBRARY_ROOT}/Movies/13 Hours/13 Hours.mp4", 500)
        val folder = movie.destination.substringBeforeLast('/')
        assertTrue(matchesRecordedExportName(movie, "$folder/13_HOURS.MP4"))
        assertTrue(matchesRecordedExportName(movie, "$folder/13_Hours_1080P.mp4"))
        assertFalse(matchesRecordedExportName(movie, "$folder/13 Hours.srt"))
        assertFalse(matchesRecordedExportName(movie, "$folder/14 Hours.mp4"))
        assertFalse(matchesRecordedExportName(movie, "$folder/subfolder/13 Hours.mp4"))
        assertFalse(matchesRecordedExportName(movie, "$folder/.13 Hours.mp4"))
    }
    @Test fun interruptedRestoresRemainSelectable() {
        val pending = record.copy(status = ExportRecordStatus.RESTORING)
        val exporting = record.copy(id = "exporting", status = ExportRecordStatus.EXPORTING)
        assertEquals(listOf(pending), ExportSession("session", 0, listOf(pending, exporting)).undoable)
    }

    @Test fun directUndoUsesOriginalFilenameWithoutMetadataOrComparison() {
        val target = "${ProtectedExportPaths.VIDEO_SOURCE}/Movie's_1080P.mp4"
        val command = directRestoreMoveCommand(destination, target, target.substringBeforeLast('/'))
        assertTrue(command.contains("mv -n -T ${shellQuote(destination)} ${shellQuote(target)}"))
        assertFalse(command.contains("stat "))
        assertFalse(command.contains("cmp "))
        assertFalse(command.contains("cp "))
        assertFalse(command.contains("rm "))
        assertTrue(command.contains("[ ! -e ${shellQuote(target)} ]"))
        assertTrue(command.contains("[ -f ${shellQuote(target)} ]"))
    }

    @Test fun directRestoreCompletionRetainsMappingAndClearsPendingFields() {
        val pending = record.copy(status = ExportRecordStatus.RESTORING,
            originIdentity = ExportFileIdentity("1:2:500:time"), temporary = original + ".partial")
        val completed = completeDirectRestore(pending, 123)
        assertEquals(ExportRecordStatus.RESTORED, completed.status)
        assertEquals(original, completed.source)
        assertEquals(destination, completed.destination)
        assertEquals(123L, completed.restoredAt)
        assertEquals(null, completed.temporary)
        assertEquals(null, completed.originIdentity)
    }
}
