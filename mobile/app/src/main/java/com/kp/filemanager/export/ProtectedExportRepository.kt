/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportRepository.kt
 * Purpose: Transactional Shizuku export with byte progress, verified copies, non-clobber publishing and recovery.
 * Last changed: 2026-11-24, restore recorded paths with a direct non-clobber move and path-only recovery.
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ProtectedExportRepository(
    private val context: Context,
    val shell: ShizukuShell = ShizukuShell(context),
    private val planner: ProtectedExportPlanner = ProtectedExportPlanner(),
) {
    private val journal = ExportJournal(context)

    private val transferLock = Mutex()
    fun history(): List<ExportSession> = journal.sessions()
    fun hasUndo(): Boolean = history().any { it.undoable.isNotEmpty() }

    fun startForegroundTransfer() = ExportForegroundService.start(context)
    fun updateForegroundTransfer(progress: ExportProgress) = ExportForegroundService.progress(context, progress)
    fun stopForegroundTransfer() = ExportForegroundService.stop(context)

    suspend fun scan(onProgress: (ExportProgress) -> Unit): ExportPreview = withContext(Dispatchers.IO) { transferLock.withLock {
        require(shell.isAuthorized()) { "Grant KP File Manager Shizuku access first." }
        onProgress(ExportProgress(ExportStage.CHECKING, message = "Checking MovieBox protected folders"))
        reconcilePending(finishVerified = false)
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
            return@withLock planner.build(emptyList())
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
        planner.withOccupiedTargets(preliminary, alreadyExisting)
    } }

    suspend fun export(
        preview: ExportPreview,
        selected: Set<String>,
        onProgress: (ExportProgress) -> Unit,
    ): ExportOutcome = withContext(Dispatchers.IO) { transferLock.withLock {
        require(shell.isAuthorized()) { "Shizuku access is no longer available." }
        reconcilePending(finishVerified = false)
        val bundles = preview.bundles.filter { it.id in selected }
        require(bundles.isNotEmpty() && bundles.none { it.blocked }) { "Select eligible media without conflicts." }
        val operations = bundles.flatMap { it.operations }
        require(operations.all { ProtectedExportPaths.inProtectedSource(it.sourceUri) && it.status == OperationStatus.READY })
        val pendingPaths = journal.sessions().flatMap { it.records }.filter { it.status.pending }
            .flatMap { listOf(it.source, it.destination) }.toSet()
        require(operations.none { it.sourceUri in pendingPaths || ProtectedExportPaths.destination(it.destinationPath) in pendingPaths }) {
            "Recover interrupted transfers before reusing their paths."
        }
        require(findOccupiedTargets(operations).isEmpty()) { "A destination was created after preview. Rescan first." }
        val progress = TransferProgress(operations.size, operations.sumOf { preview.sizes.getValue(it.sourceUri) }, onProgress)
        val errors = mutableListOf<String>()
        val destinations = mutableListOf<String>()
        var completedGroups = 0
        val native = NativeMovePolicy(supportsNativeRename())
        var session = ExportSession(UUID.randomUUID().toString(), System.currentTimeMillis(), emptyList())
        fun save(next: ExportSession) { journal.save(next); session = next }
        fun update(record: ExportRecord) = save(session.replace(record))
        val prepared = mutableSetOf<String>()
        try {
            for (bundle in bundles) {
                currentCoroutineContext().ensureActive()
                var active: ExportRecord? = null
                try {
                    val paths = bundle.operations.map { operation ->
                        operation.sourceUri to ProtectedExportPaths.destination(operation.destinationPath)
                    }
                    val identities = preflight(paths, bundle.operations.map { preview.sizes.getValue(it.sourceUri) })
                    val records = paths.mapIndexed { index, (source, destination) ->
                        pendingRecord(ExportRecord(source, destination, identities[index].size,
                            groupId = bundle.id, groupLabel = bundle.label), identities[index], restoring = false, native = native)
                    }
                    // Every member is recorded before any member is moved. A group may be partially moved;
                    // never roll it back by guessing which external files belong to us.
                    save(session.copy(records = session.records + records))
                    val directories = paths.map { it.second.substringBeforeLast('/') }.distinct().filterNot { it in prepared }
                    prepareDirectories(directories)
                    prepared += directories
                    for (initial in records) {
                        currentCoroutineContext().ensureActive()
                        active = initial
                        val completed = transfer(initial, native, progress) { record ->
                            update(record); active = record
                        }
                        session = session.replace(completed)
                        active = null
                        destinations += completed.destination
                        progress.completed(completed)
                    }
                    completedGroups++
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message = error.message ?: "Export could not finish"
                    active?.let { update(it.copy(error = message)) }
                    errors += "${bundle.label}: $message. See history for any interrupted files."
                    if (!shell.isAuthorized()) break
                } finally {
                    // Batch completions; durable intent and verified identities cover crashes.
                    if (session.records.isNotEmpty()) journal.save(session)
                }
            }
        } finally {
            if (session.records.isNotEmpty()) save(session.copy(finishedAt = System.currentTimeMillis()))
            scanMedia(destinations)
        }
        progress.finish("Finished $completedGroups of ${bundles.size} media groups")
        ExportOutcome(completedGroups, errors, emptyList(), destinations)
    } }

    /** IDs are session-scoped. Null means the latest session with outstanding exports, not all history. */
    suspend fun undo(
        sessionId: String? = null,
        recordIds: Set<String>? = null,
        onProgress: (ExportProgress) -> Unit,
    ): ExportOutcome = withContext(Dispatchers.IO) { transferLock.withLock {
        require(shell.isAuthorized()) { "Reconnect Shizuku before restoring originals." }
        val sessions = journal.sessions()
        var session = (if (sessionId == null) sessions.firstOrNull { it.undoable.isNotEmpty() }
            else sessions.firstOrNull { it.id == sessionId }) ?: error("No matching export session")
        val records = session.undoable.filter { recordIds == null || it.id in recordIds }.asReversed()
        require(records.isNotEmpty()) { "No exported files selected for undo." }
        val newerPaths = sessions.takeWhile { it.id != session.id }.flatMap { it.records }
            .filter { it.status != ExportRecordStatus.NOT_MOVED }.map { it.destination }.toSet()
        val failures = mutableListOf<String>()
        val restored = mutableListOf<String>()
        val progress = TransferProgress(records.size, records.sumOf { it.size }, onProgress)
        fun update(record: ExportRecord, retainedPartial: String? = null) {
            val next = session.replace(record).let { updated ->
                if (retainedPartial == null) updated else updated.copy(
                    legacyTemporary = (updated.legacyTemporary + retainedPartial).distinct())
            }
            journal.save(next); session = next
        }
        for (selectedRecord in records) {
            currentCoroutineContext().ensureActive()
            var record = selectedRecord
            var active = record
            try {
                progress.report(ExportStage.CHECKING, record, "Checking recorded restore paths")
                validateRecord(record)
                require(record.destination !in newerPaths) { "A newer session has used this destination; restore the newer session instead" }
                val others = sessions.filter { it.id != session.id }.flatMap { it.records } +
                    session.records.filter { it.id != record.id }
                val busyPaths = others.filter { it.status.pending }.flatMap { listOf(it.source, it.destination) }.toSet()
                require(record.source !in busyPaths && record.destination !in busyPaths) { "Another interrupted transfer claims this path" }
                val (exported, original) = restorePaths(record.destination, record.source)
                check(original == RestorePathState.ABSENT) {
                    "Original path already exists or is not a regular file; no file overwritten: ${record.source}. " +
                        "Review both paths and use interrupted-transfer recovery if the move already finished."
                }
                if (exported == RestorePathState.ABSENT) {
                    val claimed = others.flatMap { listOf(it.source, it.destination) }.toSet() + newerPaths
                    val path = resolveRenamedExport(record, claimed) ?: error(
                        "Exported file not visible at ${record.destination}; no unique matching name in its recorded folder")
                    val corrected = record.copy(destination = path)
                    validateRecord(corrected)
                    record = corrected
                } else check(exported == RestorePathState.REGULAR) { "Exported path is not a regular file: ${record.destination}" }
                // Old verified-copy attempts may have left partials. Never delete them as part of undo.
                val retained = record.temporary?.takeIf { restorePath(it) != RestorePathState.ABSENT }
                active = record.copy(status = ExportRecordStatus.RESTORING, originIdentity = null,
                    targetIdentity = null, temporary = null, error = null)
                update(active, retained)
                currentCoroutineContext().ensureActive()
                progress.report(ExportStage.RESTORING, record, "Moving back to original filename and folder")
                val parent = record.source.substringBeforeLast('/')
                shell.check(guard(record.destination) + guard(record.source) + guard(parent) +
                    directRestoreMoveCommand(record.destination, record.source, parent))
                val completed = completeDirectRestore(active)
                update(completed)
                restored += completed.source
                if (completed.method != LegacyMovieRecoveryMapping.METHOD) pruneEmptyDestinationParents(completed.destination)
                progress.completed(completed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = "${progress.lastMessage}: ${error.message ?: "Restore could not finish"}"
                // Keep the durable pending record if a move started; do not mark it complete on error.
                update(session.records.first { it.id == active.id }.copy(error = message))
                failures += "${record.groupLabel} / ${record.source.substringAfterLast('/')}: $message"
                break
            }
        }
        scanMedia(restored + records.filter { it.source in restored }.map { it.destination })
        progress.finish("Restored ${restored.size} of ${records.size} files" +
            if (failures.isNotEmpty()) "; stopped at first failure. Remaining files were not attempted." else "")
        ExportOutcome(restored.size, failures, emptyList(), restored)
    } }

    suspend fun recover(onProgress: (ExportProgress) -> Unit): ExportOutcome = withContext(Dispatchers.IO) { transferLock.withLock {
        require(shell.isAuthorized()) { "Reconnect Shizuku before recovery." }
        onProgress(ExportProgress(ExportStage.CHECKING, message = "Reconciling interrupted transfers"))
        val errors = reconcilePending(finishVerified = true)
        onProgress(ExportProgress(ExportStage.COMPLETE, message = "Recovery checked"))
        ExportOutcome(0, errors, emptyList(), emptyList())
    } }

    private fun ExportSession.replace(record: ExportRecord) = copy(records = records.map { if (it.id == record.id) record else it })

    // A hard link followed by unlink is another metadata-only path where supported.
    private class NativeMovePolicy(var rename: Boolean, var link: Boolean = !rename) {
        val method: String? get() = if (rename) "Native rename" else if (link) "Link move" else null
    }

    private fun pendingRecord(record: ExportRecord, identity: ExportFileIdentity, restoring: Boolean, native: NativeMovePolicy): ExportRecord {
        val temp = if (restoring) record.source + ".kp-restore-${UUID.randomUUID()}.partial"
            else ProtectedExportPaths.stagingPath(record.destination, UUID.randomUUID().toString())
        return record.copy(status = if (restoring) ExportRecordStatus.RESTORING else ExportRecordStatus.EXPORTING,
            method = if (restoring) record.method else native.method ?: "Verified copy",
            originIdentity = identity, targetIdentity = if (native.method != null) identity else null, temporary = temp, error = null)
    }

    private suspend fun transfer(
        initial: ExportRecord,
        native: NativeMovePolicy,
        progress: TransferProgress,
        persist: (ExportRecord) -> Unit,
    ): ExportRecord {
        validateRecord(initial)
        val source = initial.origin
        val destination = initial.target
        val original = requireNotNull(initial.originIdentity)
        var record = initial
        val nativeMethod = native.method
        if (nativeMethod != null) {
            progress.report(ExportStage.MOVING, record, "Attempting metadata-only move")
            // Ordinary mv can silently copy across mounts. ln -T cannot copy or replace a target.
            val action = if (native.rename) {
                "mv --no-copy -n -T ${shellQuote(source)} ${shellQuote(destination)}"
            } else {
                "ln -T ${shellQuote(source)} ${shellQuote(destination)} || exit 40; " +
                    "${expectIdentity(source, original)}${expectIdentity(destination, original)}" +
                    "rm ${shellQuote(source)} || exit 42; ${expectAbsent(source)}"
            }
            val result = runCatching {
                shell.check("${guard(source)}${guard(destination)}${expectIdentity(source, original)}${expectAbsent(destination)}$action")
            }
            val (old, new) = probePair(source, destination)
            if (old == null && new == original) return finish(record, new, nativeMethod)
            check(old == original && new == null) { "Metadata-only move result is uncertain; recovery required" }
            if (native.rename) native.rename = false else native.link = false
            if (result.isSuccess) error("Metadata-only move did not relocate the file; retry after recovery")
        }
        currentCoroutineContext().ensureActive()
        val temp = requireNotNull(record.temporary)
        progress.report(ExportStage.COPYING, record, "Copying to temporary file")
        copyWithProgress(source, temp, original) { bytes ->
            progress.report(ExportStage.COPYING, record, "Copying temporary file", bytes)
        }
        currentCoroutineContext().ensureActive()
        progress.report(ExportStage.VERIFYING, record, "Comparing file contents once")
        val verified = verifyCopy(source, temp, original)
        record = record.copy(targetIdentity = verified, method = if (record.status == ExportRecordStatus.EXPORTING) "Verified copy" else record.method)
        // The verified temporary identity is durable BEFORE publication or source deletion.
        persist(record)
        currentCoroutineContext().ensureActive()
        progress.report(ExportStage.PUBLISHING, record, "Publishing verified file and removing unchanged original")
        shell.check("${guard(source)}${guard(destination)}${guard(temp)}${expectIdentity(source, original)}" +
            "${expectIdentity(temp, verified)}${expectAbsent(destination)}" +
            "mv -n -T ${shellQuote(temp)} ${shellQuote(destination)} || exit 41; " +
            "${expectAbsent(temp)}${expectIdentity(destination, verified)}${expectIdentity(source, original)}" +
            "rm ${shellQuote(source)} || exit 42; ${expectAbsent(source)}")
        return finish(record, verified, "Verified copy")
    }

    private fun finish(record: ExportRecord, identity: ExportFileIdentity, method: String): ExportRecord {
        val now = System.currentTimeMillis()
        return if (record.status == ExportRecordStatus.RESTORING) record.copy(status = ExportRecordStatus.RESTORED,
            restoredAt = now, originIdentity = null, targetIdentity = null, temporary = null, error = null)
        else record.copy(status = ExportRecordStatus.EXPORTED, method = method, exportedIdentity = identity,
            exportedAt = now, originIdentity = null, targetIdentity = null, temporary = null, error = null)
    }

    /** No guessing based on size alone. Scans reconcile completed operations; only explicit recovery
     * may finish a verified duplicate or remove a journaled partial with its original still intact. */
    private suspend fun reconcilePending(finishVerified: Boolean): List<String> {
        val errors = mutableListOf<String>()
        val indexed = mutableListOf<String>()
        for (initial in journal.sessions().filter { it.hasPending }) {
            var session = initial
            for (record in initial.records.filter { it.status.pending }) {
                currentCoroutineContext().ensureActive()
                val next = try {
                    validateRecord(record)
                    if (record.status == ExportRecordStatus.RESTORING) {
                        val (exported, original) = restorePaths(record.destination, record.source)
                        val retained = record.temporary?.takeIf { restorePath(it) != RestorePathState.ABSENT }
                        val resolved = when {
                            exported == RestorePathState.REGULAR && original == RestorePathState.ABSENT ->
                                record.copy(status = ExportRecordStatus.EXPORTED, originIdentity = null,
                                    targetIdentity = null, temporary = null, error = null)
                            finishVerified && exported == RestorePathState.ABSENT && original == RestorePathState.REGULAR -> {
                                indexed += listOf(record.destination, record.source)
                                completeDirectRestore(record)
                            }
                            !finishVerified -> record
                            else -> error("Restore paths are occupied, missing or uncertain. Files kept; inspect both recorded paths.")
                        }
                        if (resolved != record && retained != null) session = session.copy(
                            legacyTemporary = (session.legacyTemporary + retained).distinct())
                        resolved
                    } else {
                    val expected = requireNotNull(record.originIdentity)
                    val (source, target) = probePair(record.origin, record.target)
                    val temp = record.temporary
                    val staged = temp?.let { probe(it) }
                    val verified = record.targetIdentity
                    when {
                        source == null && target != null && target == verified && staged == null -> {
                            indexed += listOf(record.origin, record.target)
                            finish(record, target, record.method)
                        }
                        finishVerified && source == expected && target != null && target == verified && staged == null -> {
                            shell.check("${guard(record.origin)}${guard(record.target)}${expectIdentity(record.origin, expected)}" +
                                "${expectIdentity(record.target, target)}rm ${shellQuote(record.origin)} || exit 42; ${expectAbsent(record.origin)}")
                            indexed += listOf(record.origin, record.target)
                            finish(record, target, record.method)
                        }
                        finishVerified && source == expected && target == null -> {
                            if (staged != null) {
                                shell.check("${guard(temp)}${guard(record.origin)}${expectIdentity(record.origin, expected)}" +
                                    "${expectIdentity(temp, staged)}rm ${shellQuote(temp)} || exit 42; ${expectAbsent(temp)}")
                            }
                            record.copy(status = if (record.status == ExportRecordStatus.RESTORING) ExportRecordStatus.EXPORTED else ExportRecordStatus.NOT_MOVED,
                                originIdentity = null, targetIdentity = null, temporary = null, error = "Interrupted before completion; original retained")
                        }
                        !finishVerified && source == expected && target == null -> record
                        !finishVerified && source == expected && target == verified && staged == null -> record
                        else -> error("Paths or metadata differ from the journal. Files were kept; inspect both locations.")
                    }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val message = error.message ?: "Recovery requires attention"
                    errors += "${record.groupLabel}: $message"
                    record.copy(error = message)
                }
                if (next != record) session = session.replace(next)
            }
            // v1 temporary entries have no source association or fingerprint. Preserve them for manual review.
            if (session.legacyTemporary.isNotEmpty()) errors += "Retained partial files need manual review; see export history."
            if (session != initial) journal.save(session)
        }
        scanMedia(indexed)
        return errors
    }

    private class TransferProgress(val totalFiles: Int, val totalBytes: Long, val emit: (ExportProgress) -> Unit) {
        private var count = 0
        private var bytes = 0L
        var lastMessage = "Checking transfer"
            private set
        fun report(stage: ExportStage, record: ExportRecord, message: String, copied: Long = 0) {
            lastMessage = message
            emit(ExportProgress(stage, record.source.substringAfterLast('/'), count, totalFiles, bytes, totalBytes,
                copied.coerceIn(0, record.size), message, record.size))
        }
        fun completed(record: ExportRecord) { count++; bytes += record.size; report(ExportStage.CLEANING, record, "Transfer recorded") }
        fun finish(message: String) = emit(ExportProgress(ExportStage.COMPLETE, completedFiles = count,
            totalFiles = totalFiles, completedBytes = bytes, totalBytes = totalBytes, message = message))
    }

    private fun existsDirectory(path: String): Boolean =
        shell.check("if [ -d ${shellQuote(path)} ] && [ -r ${shellQuote(path)} ]; then echo yes; fi") == "yes"

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

    private fun validateRecord(record: ExportRecord) {
        require(ProtectedExportPaths.inProtectedSource(record.source)) { "Invalid journal source" }
        if (record.method == LegacyMovieRecoveryMapping.METHOD) {
            require(LegacyMovieRecoveryMapping.target(record.destination) == record.source) { "Invalid reconstructed Movies mapping" }
            require(record.status != ExportRecordStatus.EXPORTING) { "Recovery mappings cannot be used for export" }
        } else {
            require(ProtectedExportPaths.destination(record.destination.removePrefix("${ProtectedExportPaths.LIBRARY_ROOT}/")) == record.destination) {
                "Invalid journal destination"
            }
        }
        require(record.size >= 0)
        record.temporary?.let { temp ->
            val token = Regex("[a-f0-9-]{36}")
            val valid = if (record.status == ExportRecordStatus.RESTORING) {
                temp.startsWith(record.source + ".kp-restore-") &&
                    temp.removePrefix(record.source + ".kp-restore-").removeSuffix(".partial").matches(token) && temp.endsWith(".partial")
            } else temp.substringBeforeLast('/') == record.destination.substringBeforeLast('/') &&
                temp.substringAfterLast('/').matches(Regex("\\.kp-export-[a-f0-9-]{36}\\.partial"))
            require(valid) { "Invalid journal temporary path" }
        }
    }

    private fun parentComponents(path: String): List<String> {
        val root = ProtectedExportPaths.LIBRARY_ROOT
        require(path.startsWith("$root/")) { "Unsafe transfer path" }
        val segments = path.removePrefix("$root/").split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." && '\u0000' !in it }) { "Unsafe transfer path" }
        var current = root
        return segments.dropLast(1).map { current += "/$it"; current }
    }

    private fun guard(path: String): String = transferPathGuard(path, parentComponents(path))

    private fun prepareDirectories(paths: List<String>) {
        paths.distinct().chunked(20).forEach { chunk ->
            shell.check(chunk.joinToString(" ") { path ->
                val q = shellQuote(path)
                "${guard(path)}" + "mkdir -p $q || exit 31; ${guard(path)}" +
                    "[ -d $q ] || { printf '%s\\n' ${shellQuote("Destination is not a directory: $path")} >&2; exit 31; };"
            })
        }
    }

    private fun metadataCommand(path: String): String = guard(path) + transferMetadataCommand(path)

    private fun restorePath(path: String): RestorePathState =
        RestorePathState.valueOf(shell.check(guard(path) + restorePathCommand(path)))

    private fun restorePaths(exported: String, original: String): Pair<RestorePathState, RestorePathState> {
        val rows = shell.check(guard(exported) + guard(original) + restorePathCommand(exported) + restorePathCommand(original)).lines()
        check(rows.size == 2) { "Unexpected restore path response" }
        return RestorePathState.valueOf(rows[0]) to RestorePathState.valueOf(rows[1])
    }

    private fun resolveRenamedExport(record: ExportRecord, claimedPaths: Set<String>): String? {
        if (record.method == LegacyMovieRecoveryMapping.METHOD) return null
        val folder = record.destination.substringBeforeLast('/')
        val q = shellQuote(folder)
        val listing = shell.output(guard(folder) + "find $q -maxdepth 1 -type f -print0")
        val matches = splitNull(listing).filter { path -> path !in claimedPaths &&
            path != record.destination && matchesRecordedExportName(record, path) }.distinct()
        check(matches.size <= 1) { "Multiple files match the recorded export in $folder; no files changed" }
        return matches.singleOrNull()
    }

    // Keep each typed stat row intact; the decoder removes only the validated file-type prefix.
    private fun metadataRows(command: String): List<String> =
        shell.output(command).toString(Charsets.UTF_8).removeSuffix("\n").split('\n')

    private fun probe(path: String): ExportFileIdentity? {
        val rows = metadataRows(metadataCommand(path))
        check(rows.size == 1) { "Unexpected metadata output for $path: ${rows.joinToString(" | ").take(200)}" }
        return decodeTransferIdentity(rows.single(), path)
    }

    private fun probePair(source: String, destination: String): Pair<ExportFileIdentity?, ExportFileIdentity?> {
        val rows = metadataRows(metadataCommand(source) + metadataCommand(destination))
        check(rows.size == 2) { "Incomplete metadata for $source and $destination: ${rows.joinToString(" | ").take(200)}" }
        return decodeTransferIdentity(rows[0], source) to decodeTransferIdentity(rows[1], destination)
    }

    private fun preflight(paths: List<Pair<String, String>>, sizes: List<Long>): List<ExportFileIdentity> {
        return paths.indices.chunked(16).flatMap { chunk ->
            val rows = metadataRows(chunk.joinToString(" ") { index ->
                metadataCommand(paths[index].first) + metadataCommand(paths[index].second)
            })
            check(rows.size == chunk.size * 2) { "Incomplete transfer preflight: ${rows.joinToString(" | ").take(200)}" }
            chunk.mapIndexed { offset, index ->
                val source = decodeTransferIdentity(rows[offset * 2], paths[index].first) ?: error("Source is missing; rescan")
                check(source.size == sizes[index]) { "Source size changed; rescan" }
                check(decodeTransferIdentity(rows[offset * 2 + 1], paths[index].second) == null) { "Destination already exists" }
                source
            }
        }
    }

    private fun expectIdentity(path: String, identity: ExportFileIdentity): String =
        "actual=\$(${metadataCommand(path)}) || exit 33; " +
            transferIdentityAssertion(identity)

    private fun expectAbsent(path: String): String =
        "actual=\$(${metadataCommand(path)}) || exit 34; [ \"\$actual\" = ABSENT ] || exit 34; "

    private fun supportsNativeRename(): Boolean = shell.check("mv --help 2>&1 || true").contains("--no-copy")

    private suspend fun copyWithProgress(source: String, destination: String, identity: ExportFileIdentity, onBytes: (Long) -> Unit) = coroutineScope {
        currentCoroutineContext().ensureActive()
        val process = shell.start("${guard(source)}${guard(destination)}${expectIdentity(source, identity)}${expectAbsent(destination)}" +
            "(set -C; : > ${shellQuote(destination)}) || exit 35; exec cp ${shellQuote(source)} ${shellQuote(destination)}")
        val copyJob = async(Dispatchers.IO) { shell.collect(process) }
        try {
            // No second Shizuku process while cp is running. Some transports/devices may
            // interrupt concurrent commands. Keep the copying stage visible until cp returns.
            copyJob.await()
        } finally {
            process.destroy()
        }
        currentCoroutineContext().ensureActive()
        onBytes(identity.size)
    }

    private fun verifyCopy(source: String, temp: String, original: ExportFileIdentity): ExportFileIdentity {
        val result = metadataRows("${guard(source)}${guard(temp)}${expectIdentity(source, original)}" +
            "before=\$(${metadataCommand(temp)}) || exit 36; [ \"\$before\" != ABSENT ] || exit 36; " +
            "cmp -s ${shellQuote(source)} ${shellQuote(temp)} || exit 37; ${expectIdentity(source, original)}" +
            "after=\$(${metadataCommand(temp)}) || exit 36; " +
            "[ \"\$before\" = \"\$after\" ] || exit 36; printf '%s\\n' \"\$after\"")
        check(result.size == 1) { "Unexpected verified-copy metadata for $temp: ${result.joinToString(" | ").take(200)}" }
        return requireNotNull(decodeTransferIdentity(result.single(), temp)).also { check(it.size == original.size) { "Copy size mismatch" } }
    }

    private fun scanMedia(paths: List<String>) {
        if (paths.isNotEmpty()) runCatching { MediaScannerConnection.scanFile(context, paths.distinct().toTypedArray(), null, null) }
    }

    private fun pruneEmptyDestinationParents(path: String) {
        val parents = parentComponents(path).asReversed().filter {
            it.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/Movies/") ||
                it.startsWith("${ProtectedExportPaths.LIBRARY_ROOT}/TV Shows/")
        }
        if (parents.isNotEmpty()) runCatching {
            shell.check(parents.joinToString(" ") { parent ->
                "${guard(parent)}rmdir ${shellQuote(parent)} 2>/dev/null || true;"
            })
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

/** Android emulated-storage access(2) probes may disagree with direct file operations.
 * Reject observable symlinks and non-directory ancestors, but let actual checked
 * stat/copy/rename/mkdir operations determine access rather than requiring test -x.
 * This is not a substitute for the identity and no-overwrite checks at mutation time.
 */
internal fun transferPathGuard(path: String, parents: List<String>): String = buildString {
    fun reject(message: String): String = "{ printf '%s\\n' ${shellQuote(message)} >&2; exit 30; }"
    parents.forEach { parent ->
        val q = shellQuote(parent)
        append("[ ! -L $q ] || ${reject("Symbolic link in transfer path: $parent")}; ")
        append("if [ -e $q ] && [ ! -d $q ]; then ${reject("Transfer parent is not a directory: $parent")}; fi; ")
    }
    append("[ ! -L ${shellQuote(path)} ] || ${reject("Symbolic link at transfer path: $path")}; ")
}

/** Do not infer absence from test -f/-e: access errors can look like false.
 * Only an explicit stat ENOENT diagnostic is accepted as absent. Android may still
 * hide a file from this UID, so callers must not treat absence as proof of deletion.
 */
internal fun transferMetadataCommand(path: String): String {
    val q = shellQuote(path)
    return "if meta=\$(LC_ALL=C TZ=UTC stat -c '%F|%d:%i:%s:%y' $q 2>&1); then " +
        "case \"\$meta\" in 'regular file|'*|'regular empty file|'*) printf '%s\\n' \"\$meta\";; " +
        "*) printf '%s\\n' ${shellQuote("Not a regular file: $path")} >&2; exit 32;; esac; " +
        "else case \"\$meta\" in *': No such file or directory') printf 'ABSENT\\n';; " +
        "*) printf '%s: %s\\n' ${shellQuote("Cannot stat $path")} \"\$meta\" >&2; exit 32;; esac; fi; "
}

/** Compare the complete, type-checked stat row against the existing prefix-free journal
 * identity. Do not depend on Android shell pattern removal or change stored identities.
 */
internal fun transferIdentityAssertion(identity: ExportFileIdentity): String =
    "[ \"\$actual\" = ${shellQuote("regular file|${identity.value}")} ] || " +
        "[ \"\$actual\" = ${shellQuote("regular empty file|${identity.value}")} ] || exit 33; "

/** Device/inode are opaque identifiers, not sizes. Older Android stat implementations
 * may print signed or hexadecimal IDs. Keep their exact spelling for recovery checks.
 */
internal fun decodeTransferIdentity(value: String, path: String): ExportFileIdentity? {
    if (value == "ABSENT") return null
    val payload = when {
        value.startsWith("regular file|") -> value.removePrefix("regular file|")
        value.startsWith("regular empty file|") -> value.removePrefix("regular empty file|")
        else -> value
    }
    val fields = payload.split(':', limit = 4)
    val identifier = Regex("[+-]?(?:0[xX])?[0-9a-fA-F]+(?:,[0-9]+)?")
    val valid = fields.size == 4 && fields.take(2).all { identifier.matches(it.trim()) } &&
        (fields[2].trim().toLongOrNull()?.let { it >= 0 } == true) &&
        fields[3].isNotBlank() && fields[3].none { it.isISOControl() } && fields[3].trim() != "?"
    check(valid) { "Invalid file metadata for $path: ${value.take(200)}" }
    return ExportFileIdentity(payload)
}

/** Restrict fallback to the recorded directory and known names, not fuzzy title or size-only search. */
internal fun matchesRecordedExportName(record: ExportRecord, candidate: String): Boolean {
    if (candidate.substringBeforeLast('/') != record.destination.substringBeforeLast('/')) return false
    val leaf = candidate.substringAfterLast('/')
    if (leaf.startsWith('.') || leaf.any { it.isISOControl() || it == '\\' }) return false
    fun key(path: String): Pair<String, String> {
        val name = path.substringAfterLast('/')
        return name.substringBeforeLast('.', "").lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() } to
            name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    }
    val name = key(candidate)
    return name.first.isNotEmpty() && name.second.isNotEmpty() &&
        (name == key(record.destination) || name == key(record.source)) &&
        MediaFileClassifier.classify(leaf) == MediaFileClassifier.classify(record.source.substringAfterLast('/'))
}

/** Undo uses the recorded names, not metadata or byte comparisons. A false existence test
 * is only a visibility result; mv must succeed and its source/target postconditions must hold. */
internal enum class RestorePathState { REGULAR, OCCUPIED, ABSENT }

internal fun restorePathCommand(path: String): String {
    val q = shellQuote(path)
    return "if [ -f $q ]; then printf 'REGULAR\\n'; elif [ -e $q ] || [ -L $q ]; then printf 'OCCUPIED\\n'; else printf 'ABSENT\\n'; fi; "
}

internal fun directRestoreMoveCommand(exported: String, original: String, parent: String): String {
    val from = shellQuote(exported)
    val to = shellQuote(original)
    val dir = shellQuote(parent)
    fun fail(message: String) = "{ printf '%s\\n' ${shellQuote(message)} >&2; exit 45; }"
    return "[ -f $from ] && [ ! -L $from ] || ${fail("Exported file not visible: $exported")}; " +
        "mkdir -p $dir || exit 31; [ -d $dir ] && [ ! -L $dir ] || ${fail("Original directory unavailable: $parent")}; " +
        "[ ! -e $to ] && [ ! -L $to ] || ${fail("Original path occupied: $original")}; " +
        "mv -n -T $from $to || exit $?; " +
        "[ ! -e $from ] && [ ! -L $from ] && [ -f $to ] && [ ! -L $to ] || " +
        "${fail("Move did not complete; inspect $exported and $original")}; "
}

internal fun completeDirectRestore(record: ExportRecord, now: Long = System.currentTimeMillis()): ExportRecord =
    record.copy(status = ExportRecordStatus.RESTORED, restoredAt = now, originIdentity = null,
        targetIdentity = null, temporary = null, error = null)
