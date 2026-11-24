/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportRepository.kt
 * Purpose: Transactional Shizuku export with byte progress, verified copies, non-clobber publishing and recovery.
 * Last changed: 2026-11-23, add protected source to KP organizer pipeline.
 */
package com.kp.filemanager.export

import android.content.Context
import android.media.MediaScannerConnection
import com.kp.filemanager.domain.MediaFileClassifier
import com.kp.filemanager.domain.MediaFileType
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.domain.ScannedFile
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ProtectedExportRepository(
    private val context: Context,
    val shell: ShizukuShell = ShizukuShell(context),
    private val planner: ProtectedExportPlanner = ProtectedExportPlanner(),
) {
    private val journal = ExportJournal(context)

    fun hasUndo(): Boolean = journal.entries().isNotEmpty()

    fun startForegroundTransfer() = ExportForegroundService.start(context)
    fun updateForegroundTransfer(progress: ExportProgress) = ExportForegroundService.progress(context, progress)
    fun stopForegroundTransfer() = ExportForegroundService.stop(context)

    suspend fun scan(onProgress: (ExportProgress) -> Unit): ExportPreview = withContext(Dispatchers.IO) {
        require(shell.isAuthorized()) { "Grant KP File Manager Shizuku access first." }
        onProgress(ExportProgress(ExportStage.CHECKING, message = "Checking MovieBox protected folders"))
        recoverAbandonedTemporaryFiles()
        val roots = listOf(
            ProtectedExportPaths.VIDEO_SOURCE to MediaFileType.VIDEO,
            ProtectedExportPaths.SUBTITLE_SOURCE to MediaFileType.SUBTITLE,
        )
        val discovered = mutableListOf<Pair<String, MediaFileType>>()
        roots.forEach { (root, type) ->
            onProgress(ExportProgress(ExportStage.SCANNING, message = "Reading ${if (type == MediaFileType.VIDEO) "videos" else "subtitles"} from protected storage"))
            if (!existsDirectory(root)) {
                if (type == MediaFileType.VIDEO) {
                    throw IOException("The MovieBox video directory is missing or not readable with Shizuku. Download a video in MovieBox and confirm Shizuku has protected storage access.")
                }
                return@forEach // The subtitle directory may not exist until a subtitle is downloaded.
            }
            val depth = if (type == MediaFileType.VIDEO) "-maxdepth 1 " else ""
            val data = shell.output("find ${shellQuote(root)} ${depth}-type f -print0")
            splitNull(data).forEach { path ->
                if (ProtectedExportPaths.inProtectedSource(path) &&
                    MediaFileClassifier.classify(path.substringAfterLast('/')) == type
                ) discovered.add(path to type)
            }
        }
        if (discovered.isEmpty()) {
            return@withContext planner.build(emptyList())
        }
        val sizes = linkedMapOf<String, Long>()
        discovered.map { it.first }.chunked(60).forEach { chunk ->
            // One Shizuku process per batch rather than one process per discovered file.
            val command = chunk.joinToString("\n") { "stat -c '%s' ${shellQuote(it)} || echo -1" }
            val result = shell.check(command).lines()
            chunk.forEachIndexed { index, path ->
                val size = result.getOrNull(index)?.trim()?.toLongOrNull() ?: -1L
                if (size >= 0) sizes[path] = size
            }
            onProgress(ExportProgress(ExportStage.SCANNING, message = "Indexed ${sizes.size} of ${discovered.size} files"))
        }
        val files = discovered.mapNotNull { (path, type) ->
            val size = sizes[path] ?: return@mapNotNull null
            val sourceRoot = if (type == MediaFileType.VIDEO) ProtectedExportPaths.VIDEO_SOURCE else ProtectedExportPaths.SUBTITLE_SOURCE
            val relativeParent = path.removePrefix("$sourceRoot/").substringBeforeLast('/', "")
            val parent = listOf(if (type == MediaFileType.VIDEO) "d" else "subtitle", relativeParent)
                .filter(String::isNotBlank).joinToString("/")
            ScannedFile.from(
                sourceUri = path,
                parentUri = path.substringBeforeLast('/'),
                parentPath = parent,
                fileName = path.substringAfterLast('/'),
                depth = relativeParent.count { it == '/' } + 1,
                size = size,
                fileType = type,
            )
        }
        onProgress(ExportProgress(ExportStage.PLANNING, message = "Matching subtitles and standardizing filenames"))
        val preliminary = planner.build(files)
        val alreadyExisting = findOccupiedTargets(preliminary.plan.operations)
        planner.build(files, alreadyExisting)
    }

    suspend fun export(
        preview: ExportPreview,
        selected: Set<String>,
        onProgress: (ExportProgress) -> Unit,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        require(shell.isAuthorized()) { "Shizuku access is no longer available." }
        val bundles = preview.bundles.filter { it.id in selected }
        require(bundles.isNotEmpty()) { "Select at least one movie or episode." }
        require(bundles.none { it.blocked }) { "Resolve conflicts or deselect blocked items before exporting." }
        val allOperations = bundles.flatMap { it.operations }
        check(allOperations.all { ProtectedExportPaths.inProtectedSource(it.sourceUri) && it.status == OperationStatus.READY }) {
            "The export plan contains an invalid or blocked source."
        }
        val totalBytes = allOperations.sumOf { preview.sizes[it.sourceUri] ?: 0L }
        val totalFiles = allOperations.size
        var verifiedBytes = 0L
        var completedFiles = 0
        var exported = 0
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val destinations = mutableListOf<String>()
        val callback: (ExportStage, String, String, Long) -> Unit = { stage, filename, message, copied ->
            onProgress(ExportProgress(stage, filename, completedFiles, totalFiles, verifiedBytes, totalBytes, copied, message))
        }
        // Revalidate all selected targets just before executing, not only at preview time.
        val collisions = findOccupiedTargets(allOperations)
        if (collisions.isNotEmpty()) {
            throw IOException("A destination was created after preview. Rescan to avoid overwriting files.")
        }
        for (bundle in bundles) {
            val staged = mutableListOf<Pair<PlannedFileOperation, String>>()
            val promoted = mutableListOf<Pair<PlannedFileOperation, String>>()
            var journalCommitted = false
            try {
                bundle.operations.forEach { operation ->
                    val source = operation.sourceUri
                    val destination = ProtectedExportPaths.destination(operation.destinationPath)
                    val expected = preview.sizes[source] ?: throw IOException("Unknown source size")
                    if (fileSize(source) != expected) throw IOException("Source changed since preview: ${operation.originalName}. Rescan before exporting.")
                    prepareDirectory(destination.substringBeforeLast('/'))
                    require(!pathExists(destination)) { "Destination now exists: $destination" }
                    val temp = ProtectedExportPaths.stagingPath(destination, UUID.randomUUID().toString())
                    journal.addTemporary(temp) // Must persist before starting a copy for crash recovery.
                    staged += operation to temp
                    callback(ExportStage.COPYING, operation.originalName, "Copying to a safe temporary file", 0)
                    copyWithProgress(source, temp) { copied ->
                        callback(ExportStage.COPYING, operation.originalName, "Copying ${operation.originalName}", copied.coerceAtMost(expected))
                    }
                    callback(ExportStage.VERIFYING, operation.originalName, "Comparing source and copied file", expected)
                    verifySame(source, temp, expected)
                    verifiedBytes += expected
                }
                // All companion files have been copied and verified before any source is removed.
                staged.forEach { (operation, temp) ->
                    val destination = ProtectedExportPaths.destination(operation.destinationPath)
                    callback(ExportStage.PUBLISHING, operation.originalName, "Publishing media and subtitles together", 0)
                    publishNonClobber(temp, destination)
                    promoted += operation to destination
                    journal.removeTemporary(temp)
                }
                val records = promoted.map { (operation, dest) ->
                    ExportRecord(operation.sourceUri, dest, preview.sizes.getValue(operation.sourceUri))
                }
                journal.addCompleted(records) // Durable record precedes source removal.
                journalCommitted = true
                promoted.forEach { (operation, destination) ->
                    val size = preview.sizes.getValue(operation.sourceUri)
                    callback(ExportStage.CLEANING, operation.originalName, "Removing verified source copy", 0)
                    if (!sameBytes(operation.sourceUri, destination, size)) {
                        warnings += "Original kept because it changed during export: ${operation.originalName}"
                    } else {
                        try {
                            shell.check("rm ${shellQuote(operation.sourceUri)}")
                        } catch (error: Exception) {
                            warnings += "Original was kept because cleanup failed: ${operation.originalName} (${error.message})"
                        }
                    }
                    completedFiles += 1
                    callback(ExportStage.CLEANING, operation.originalName, "Verified and indexed ${completedFiles} of $totalFiles", 0)
                    destinations += destination
                }
                exported += 1
            } catch (error: CancellationException) {
                if (!journalCommitted) withContext(NonCancellable) { rollbackUncommitted(staged, promoted) }
                throw error
            } catch (error: Exception) {
                if (!journalCommitted) rollbackUncommitted(staged, promoted)
                errors += "${bundle.label}: ${error.message ?: "Export could not finish"}"
                if (!shell.isAuthorized()) break // No further actions can be performed without Shizuku.
            }
        }
        if (destinations.isNotEmpty()) MediaScannerConnection.scanFile(context, destinations.toTypedArray(), null, null)
        onProgress(ExportProgress(ExportStage.COMPLETE, completedFiles = completedFiles, totalFiles = totalFiles,
            completedBytes = verifiedBytes, totalBytes = totalBytes, message = "Finished $exported of ${bundles.size} selected media groups"))
        ExportOutcome(exported, errors, warnings, destinations)
    }

    suspend fun undo(onProgress: (ExportProgress) -> Unit): ExportOutcome = withContext(Dispatchers.IO) {
        require(shell.isAuthorized()) { "Reconnect Shizuku before restoring originals." }
        val records = journal.entries().asReversed()
        val failures = mutableListOf<String>()
        val restored = mutableListOf<String>()
        val total = records.size
        records.forEachIndexed { index, record ->
            try {
                require(ProtectedExportPaths.inProtectedSource(record.source)) { "Invalid journal source" }
                val restoredRelative = record.destination.removePrefix("${ProtectedExportPaths.LIBRARY_ROOT}/")
                require(ProtectedExportPaths.destination(restoredRelative) == record.destination) {
                    "Invalid journal destination"
                }
                onProgress(ExportProgress(ExportStage.RESTORING, record.source.substringAfterLast('/'), index,
                    total, message = "Restoring ${index + 1} of $total"))
                check(fileSize(record.destination) == record.size) { "Destination was changed or removed; it was not deleted" }
                if (!pathExists(record.source)) {
                    prepareDirectory(record.source.substringBeforeLast('/'), protectedSource = true)
                    val temp = record.source + ".kp-restore-${UUID.randomUUID()}.partial"
                    // Restoration is also copy-first; keep the published file until verified.
                    journal.addTemporary(temp)
                    try {
                        copyWithProgress(record.destination, temp) { bytes ->
                            onProgress(ExportProgress(ExportStage.RESTORING, record.source.substringAfterLast('/'), index,
                                total, currentCopiedBytes = bytes, totalBytes = record.size,
                                message = "Copying original back into protected storage"))
                        }
                        verifySame(record.destination, temp, record.size)
                        publishNonClobber(temp, record.source)
                    } finally {
                        if (pathExists(temp)) runCatching { removeFile(temp) }
                        journal.removeTemporary(temp)
                    }
                }
                verifySame(record.source, record.destination, record.size)
                removeFile(record.destination)
                journal.removeCompleted(record)
                restored += record.source
                pruneEmptyDestinationParents(record.destination)
            } catch (error: Exception) {
                failures += "${record.source.substringAfterLast('/')}: ${error.message}"
            }
        }
        if (restored.isNotEmpty()) MediaScannerConnection.scanFile(context, restored.toTypedArray(), null, null)
        onProgress(ExportProgress(ExportStage.COMPLETE, completedFiles = restored.size, totalFiles = total,
            message = "Restored ${restored.size} of $total files"))
        ExportOutcome(restored.size, failures, emptyList(), restored)
    }

    private fun existsDirectory(path: String): Boolean =
        shell.check("if [ -d ${shellQuote(path)} ] && [ -r ${shellQuote(path)} ]; then echo yes; fi") == "yes"

    private fun pathExists(path: String): Boolean =
        shell.check("if [ -e ${shellQuote(path)} ] || [ -L ${shellQuote(path)} ]; then echo yes; fi") == "yes"

    private fun fileSize(path: String): Long =
        shell.check("stat -c '%s' ${shellQuote(path)}").toLongOrNull()
            ?: throw IOException("Cannot read file size: ${path.substringAfterLast('/')}")

    private fun findOccupiedTargets(operations: List<PlannedFileOperation>): Set<String> {
        val paths = operations.filter { it.status == OperationStatus.READY }.map { it.destinationPath }.distinct()
        val taken = mutableSetOf<String>()
        paths.chunked(60).forEach { chunk ->
            val command = chunk.joinToString("\n") { relative ->
                val dest = ProtectedExportPaths.destination(relative)
                "if [ -e ${shellQuote(dest)} ] || [ -L ${shellQuote(dest)} ]; then printf '%s\\n' ${shellQuote(relative)}; fi"
            }
            shell.check(command).lineSequence().filter(String::isNotBlank).forEach { taken += "${ProtectedExportPaths.LIBRARY_ID}\u0000$it" }
        }
        return taken
    }

    private fun prepareDirectory(path: String, protectedSource: Boolean = false) {
        val root = if (protectedSource) {
            require(ProtectedExportPaths.inProtectedSource("$path/dummy")) { "Invalid protected parent" }
            if (path.startsWith(ProtectedExportPaths.VIDEO_SOURCE)) ProtectedExportPaths.VIDEO_SOURCE else ProtectedExportPaths.SUBTITLE_SOURCE
        } else {
            require(path.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/Movies/") ||
                path.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/TV Shows/")) { "Invalid destination directory" }
            ProtectedExportPaths.LIBRARY_ROOT
        }
        val components = path.removePrefix(root).trim('/').split('/').filter(String::isNotBlank)
        var current = root
        for (segment in components) {
            current += "/$segment"
            require(segment != "." && segment != ".." && !segment.contains('/'))
            shell.check("if [ -L ${shellQuote(current)} ]; then exit 30; fi; mkdir -p ${shellQuote(current)}")
        }
    }

    private suspend fun copyWithProgress(source: String, destination: String, onBytes: (Long) -> Unit) = coroutineScope {
        val process = shell.start("cp ${shellQuote(source)} ${shellQuote(destination)}")
        val copyJob = async(Dispatchers.IO) { shell.collect(process) }
        try {
            while (!copyJob.isCompleted) {
                delay(450)
                val size = runCatching { fileSize(destination) }.getOrDefault(0L)
                onBytes(size)
            }
            copyJob.await()
            onBytes(fileSize(destination))
        } catch (error: CancellationException) {
            process.destroy()
            throw error
        } finally {
            if (!copyJob.isCompleted) process.destroy()
        }
    }

    private fun verifySame(source: String, destination: String, expected: Long) {
        if (!sameBytes(source, destination, expected)) throw IOException("Verification failed; the original was preserved")
    }

    private fun sameBytes(source: String, destination: String, expected: Long): Boolean {
        if (runCatching { fileSize(source) }.getOrDefault(-1) != expected ||
            runCatching { fileSize(destination) }.getOrDefault(-1) != expected) return false
        return runCatching { shell.check("cmp -s ${shellQuote(source)} ${shellQuote(destination)}") }.isSuccess
    }

    private fun publishNonClobber(temp: String, destination: String) {
        // mv -n must be followed by verifying that the temporary file disappeared:
        // some mv implementations return exit 0 even when they skip an occupied target.
        shell.check("[ ! -e ${shellQuote(destination)} ] && [ ! -L ${shellQuote(destination)} ] && " +
            "mv -n ${shellQuote(temp)} ${shellQuote(destination)} && " +
            "[ ! -e ${shellQuote(temp)} ] && [ -f ${shellQuote(destination)} ]")
    }

    private fun removeFile(path: String) {
        shell.check("rm ${shellQuote(path)}")
    }

    private fun rollbackUncommitted(
        staged: List<Pair<PlannedFileOperation, String>>,
        promoted: List<Pair<PlannedFileOperation, String>>,
    ) {
        staged.forEach { (_, temporary) ->
            if (runCatching { pathExists(temporary) }.getOrDefault(false)) {
                runCatching { removeFile(temporary) }
            }
            // Retain journal entry when an orphaned temporary could not be removed.
            if (runCatching { !pathExists(temporary) }.getOrDefault(false)) journal.removeTemporary(temporary)
        }
        promoted.forEach { (operation, destination) ->
            // Original still exists because source deletion happens only after journal commit.
            val expected = runCatching { fileSize(operation.sourceUri) }.getOrDefault(-1)
            if (expected >= 0 && sameBytes(operation.sourceUri, destination, expected)) {
                runCatching { removeFile(destination) }
                pruneEmptyDestinationParents(destination)
            }
        }
        staged.forEach { (operation, _) ->
            pruneEmptyDestinationParents(ProtectedExportPaths.destination(operation.destinationPath))
        }
    }

    private fun pruneEmptyDestinationParents(path: String) {
        var parent = path.substringBeforeLast('/')
        while (parent.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/") &&
            parent != ProtectedExportPaths.LIBRARY_ROOT && parent != "${ProtectedExportPaths.LIBRARY_ROOT}/Movies" &&
            parent != "${ProtectedExportPaths.LIBRARY_ROOT}/TV Shows") {
            runCatching { shell.check("rmdir ${shellQuote(parent)}") } // Never remove nonempty folders.
            parent = parent.substringBeforeLast('/')
        }
    }

    private fun recoverAbandonedTemporaryFiles() {
        journal.temporaryFiles().forEach { path ->
            // Delete only our own journaled temporary names inside explicitly approved locations.
            val exportTemp = path.contains(Regex("/\\.kp-export-[a-f0-9-]{36}\\.partial$")) &&
                (path.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/Movies/") ||
                    path.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/TV Shows/"))
            val restoreTemp = path.contains(Regex("\\.kp-restore-[a-f0-9-]{36}\\.partial$")) &&
                ProtectedExportPaths.inProtectedSource(path)
            val safe = exportTemp || restoreTemp
            if (safe && pathExists(path)) removeFile(path)
            if (!pathExists(path)) journal.removeTemporary(path)
        }
    }

    private fun splitNull(data: ByteArray): List<String> {
        val values = mutableListOf<String>()
        var start = 0
        data.indices.forEach { index ->
            if (data[index] == 0.toByte()) {
                if (index > start) values += String(data, start, index - start, Charsets.UTF_8)
                start = index + 1
            }
        }
        if (start != data.size) throw IOException("Incomplete protected folder listing")
        return values
    }
}
