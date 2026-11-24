/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ExportJournal.kt
 * Purpose: Atomic per-session transfer history, migration and pending-operation records.
 * Last changed: 2026-11-24, allow retry of interrupted restores through recorded paths.
 */
package com.kp.filemanager.export

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Metadata is a change detector, not a cryptographic content fingerprint. */
data class ExportFileIdentity(val value: String) {
    val size: Long get() = value.split(':', limit = 4)[2].trim().toLong()
}

enum class ExportRecordStatus(val label: String) {
    EXPORTING("Export interrupted or pending"), EXPORTED("Exported"),
    RESTORING("Restore interrupted or pending"), RESTORED("Restored"), NOT_MOVED("Not moved");
    val pending: Boolean get() = this == EXPORTING || this == RESTORING
}

data class ExportRecord(
    val source: String,
    val destination: String,
    val size: Long,
    val id: String = UUID.randomUUID().toString(),
    val groupId: String = source,
    val groupLabel: String = source.substringAfterLast('/'),
    val status: ExportRecordStatus = ExportRecordStatus.EXPORTED,
    val method: String = "Legacy copy",
    val exportedIdentity: ExportFileIdentity? = null,
    val originIdentity: ExportFileIdentity? = null,
    val targetIdentity: ExportFileIdentity? = null,
    val temporary: String? = null,
    val exportedAt: Long = 0,
    val restoredAt: Long = 0,
    val error: String? = null,
) {
    val origin: String get() = if (status == ExportRecordStatus.RESTORING) destination else source
    val target: String get() = if (status == ExportRecordStatus.RESTORING) source else destination
}

data class ExportSession(
    val id: String,
    val startedAt: Long,
    val records: List<ExportRecord>,
    val finishedAt: Long = 0,
    val legacyTemporary: List<String> = emptyList(),
) {
    val undoable: List<ExportRecord> get() = records.filter { it.status == ExportRecordStatus.EXPORTED || it.status == ExportRecordStatus.RESTORING }
    val hasPending: Boolean get() = records.any { it.status.pending } || legacyTemporary.isNotEmpty()
}

internal class ExportJournal(context: Context) {
    private val directory = File(context.noBackupFilesDir, "protected_export_sessions")
    private val legacy = AtomicFile(File(context.noBackupFilesDir, "protected_export_journal.json"))

    @Synchronized
    fun sessions(): List<ExportSession> {
        migrate()
        check(directory.isDirectory || !directory.exists()) { "Invalid export journal directory" }
        val files = if (directory.exists()) directory.listFiles() ?: error("Cannot list export history") else emptyArray()
        return files.map { it.name.removeSuffix(".bak") }
            .filter { it.endsWith(".json") }.distinct()
            .map { name -> decode(read(AtomicFile(File(directory, name))) ?: error("Missing export session $name")) }
            .sortedWith(compareByDescending<ExportSession> { it.startedAt }.thenByDescending { it.id })
    }

    @Synchronized
    fun save(session: ExportSession) {
        require(session.id == LEGACY_ID || session.id.matches(Regex("[a-f0-9-]{36}")))
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create export journal directory" }
        val root = JSONObject().apply {
            put("version", 2)
            put("id", session.id)
            put("startedAt", session.startedAt)
            put("finishedAt", session.finishedAt)
            put("legacyTemporary", JSONArray(session.legacyTemporary))
            put("records", JSONArray().apply { session.records.forEach { put(encode(it)) } })
        }
        val atomic = AtomicFile(File(directory, "${session.id}.json"))
        val payload = root.toString().toByteArray(Charsets.UTF_8)
        val output = atomic.startWrite()
        try {
            output.write(payload)
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        // AtomicFile may log a failed rename rather than throw. Do not authorize a move
        // until the new record is actually readable from the committed file.
        check(atomic.openRead().use { it.readBytes().contentEquals(payload) }) { "Export journal commit failed" }
    }

    private fun migrate() {
        val migrated = File(directory, "$LEGACY_ID.json")
        if (migrated.exists() || File(directory, "$LEGACY_ID.json.bak").exists()) return
        val root = read(legacy) ?: return
        check(root.getInt("version") == 1) { "Unsupported legacy export journal version" }
        val records = root.getJSONArray("records")
        val temps = root.getJSONArray("temporary")
        // Keep the old file intact; the committed legacy session is the migration marker.
        save(ExportSession(LEGACY_ID, 0, List(records.length()) { index ->
            val item = records.getJSONObject(index)
            ExportRecord(item.getString("source"), item.getString("destination"), item.getLong("size"),
                id = "legacy-$index")
        }, legacyTemporary = List(temps.length()) { temps.getString(it) }))
    }

    private fun read(file: AtomicFile): JSONObject? = try {
        JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
        null
    }

    private fun encode(record: ExportRecord) = JSONObject().apply {
        put("source", record.source); put("destination", record.destination); put("size", record.size)
        put("id", record.id); put("groupId", record.groupId); put("groupLabel", record.groupLabel)
        put("status", record.status.name); put("method", record.method)
        put("exportedIdentity", record.exportedIdentity?.value ?: JSONObject.NULL)
        put("originIdentity", record.originIdentity?.value ?: JSONObject.NULL)
        put("targetIdentity", record.targetIdentity?.value ?: JSONObject.NULL)
        put("temporary", record.temporary ?: JSONObject.NULL)
        put("exportedAt", record.exportedAt); put("restoredAt", record.restoredAt)
        put("error", record.error ?: JSONObject.NULL)
    }

    private fun decode(root: JSONObject): ExportSession {
        check(root.getInt("version") == 2) { "Unsupported export session version" }
        val records = root.getJSONArray("records")
        val temps = root.getJSONArray("legacyTemporary")
        return ExportSession(root.getString("id"), root.getLong("startedAt"), List(records.length()) { index ->
            val item = records.getJSONObject(index)
            fun optional(key: String) = if (item.isNull(key)) null else item.getString(key)
            ExportRecord(item.getString("source"), item.getString("destination"), item.getLong("size"),
                item.getString("id"), item.getString("groupId"), item.getString("groupLabel"),
                ExportRecordStatus.valueOf(item.getString("status")), item.getString("method"),
                optional("exportedIdentity")?.let(::ExportFileIdentity), optional("originIdentity")?.let(::ExportFileIdentity),
                optional("targetIdentity")?.let(::ExportFileIdentity), optional("temporary"),
                item.getLong("exportedAt"), item.getLong("restoredAt"), optional("error"))
        }, root.getLong("finishedAt"), List(temps.length()) { temps.getString(it) })
    }

    private companion object { const val LEGACY_ID = "legacy-v1" }
}
