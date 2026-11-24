/**
 * Path: mobile/app/src/test/java/com/kp/filemanager/export/ProtectedExportPlannerTest.kt
 * Purpose: Regression tests for cross-folder subtitle matching, KP output names and fail-closed conflicts.
 * Last changed: 2026-11-24, test occupied-target application without reparsing media.
 */
package com.kp.filemanager.export

import com.kp.filemanager.domain.ScannedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedExportPlannerTest {
    private val videoRoot = ProtectedExportPaths.VIDEO_SOURCE
    private val subtitleRoot = ProtectedExportPaths.SUBTITLE_SOURCE

    private fun video(name: String, bytes: Long = 100L) = source(videoRoot, "d", name, bytes)
    private fun subtitle(name: String, bytes: Long = 10L) = source(subtitleRoot, "subtitle", name, bytes)
    private fun source(root: String, parent: String, name: String, bytes: Long) = ScannedFile.from(
        sourceUri = "$root/$name", parentUri = root, parentPath = parent,
        fileName = name, size = bytes, depth = 0,
    )

    @Test fun groupsVideoAndLanguagesAcrossSeparateProtectedDirectories() {
        val preview = ProtectedExportPlanner().build(listOf(
            video("The.Bear.S02E04.mp4", 1000),
            subtitle("The.Bear.S02E04.eng.srt", 30),
            subtitle("The.Bear.S02E04.spa.srt", 40),
            video("Arrival.2016.mkv", 2000),
            subtitle("Arrival.2016.eng.srt", 50),
            subtitle("Unrelated.srt"),
        ))
        assertEquals(2, preview.bundles.size)
        assertEquals(1, preview.unmatched.size)
        assertEquals("Unrelated.srt", preview.unmatched.single().originalName)
        assertEquals(3, preview.bundles.sumOf { it.subtitleCount })
        assertEquals(3120L, preview.eligible.sumOf { it.totalBytes })
        assertTrue(preview.bundles.none { it.blocked })
        val paths = preview.bundles.flatMap { it.operations }.map { it.destinationPath }.toSet()
        assertTrue("Movies/Arrival (2016)/Arrival (2016).mkv" in paths)
        assertTrue("Movies/Arrival (2016)/Arrival (2016).eng.srt" in paths)
        assertTrue("TV Shows/The Bear/Season 02/The Bear - S02E04.mp4" in paths)
        assertTrue("TV Shows/The Bear/Season 02/The Bear - S02E04.eng.srt" in paths)
        assertTrue("TV Shows/The Bear/Season 02/The Bear - S02E04.spa.srt" in paths)
    }

    @Test fun neverSelectsAnOccupiedOrConflictingDestination() {
        val files = listOf(video("Arrival.2016.mkv"), subtitle("Arrival.2016.eng.srt"))
        val occupied = "${ProtectedExportPaths.LIBRARY_ID}\u0000Movies/Arrival (2016)/Arrival (2016).mkv"
        val preview = ProtectedExportPlanner().build(files, setOf(occupied))
        assertTrue(preview.bundles.single().blocked)
        assertTrue(preview.eligible.isEmpty())
    }

    @Test fun conflictingSubtitleNamesBlockTheWholeMovieInsteadOfDroppingACaption() {
        val preview = ProtectedExportPlanner().build(listOf(
            video("Arrival.2016.mkv"), subtitle("Arrival.2016.eng.srt"),
            ScannedFile.from(
                sourceUri = "$subtitleRoot/alternate/Arrival.2016.eng.srt",
                parentUri = "$subtitleRoot/alternate", parentPath = "subtitle/alternate",
                fileName = "Arrival.2016.eng.srt", size = 10, depth = 1,
            ),
        ))
        assertEquals(1, preview.bundles.size)
        assertTrue(preview.bundles.single().blocked)
    }

    @Test fun missingCompanionsNeverPreventStandaloneVideoExport() {
        val preview = ProtectedExportPlanner().build(listOf(video("Arrival.2016.mkv")))
        assertEquals(1, preview.eligible.size)
        assertEquals(0, preview.eligible.single().subtitleCount)
    }

    @Test fun protectedPathsAndDestinationFoldersAreRestricted() {
        assertTrue(ProtectedExportPaths.inProtectedSource("$videoRoot/movie.mp4"))
        assertFalse(ProtectedExportPaths.inProtectedSource("$videoRoot/../other/video.mp4"))
        assertFalse(ProtectedExportPaths.inProtectedSource("/storage/emulated/0/DCIM/movie.mp4"))
        assertEquals("/storage/emulated/0/Movies/A (2026)/A (2026).mkv",
            ProtectedExportPaths.destination("Movies/A (2026)/A (2026).mkv"))
        assertTrue(runCatching { ProtectedExportPaths.destination("Movies/../trick.mkv") }.isFailure)
        assertTrue(runCatching { ProtectedExportPaths.destination("Downloads/Movies/Title.mkv") }.isFailure)
        assertTrue(runCatching { ProtectedExportPaths.destination("TV Shows/Show/../../bad") }.isFailure)
    }
    @Test fun reusedPreviewMatchesFullCollisionPlan() {
        val files = listOf(video("Arrival.2016.mkv"), subtitle("Arrival.2016.eng.srt"))
        val planner = ProtectedExportPlanner()
        val occupied = "${ProtectedExportPaths.LIBRARY_ID}\u0000Movies/Arrival (2016)/Arrival (2016).mkv"
        val preliminary = planner.build(files)
        val reused = planner.withOccupiedTargets(preliminary, setOf(occupied))
        val rebuilt = planner.build(files, setOf(occupied))
        assertEquals(rebuilt.plan.operations, reused.plan.operations)
        assertEquals(rebuilt.bundles, reused.bundles)
        assertTrue(reused.bundles.single().blocked)
    }
}
