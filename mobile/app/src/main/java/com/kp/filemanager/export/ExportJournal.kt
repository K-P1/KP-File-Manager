/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ExportJournal.kt
 * Purpose: Durable atomic transaction journal for crash cleanup and reversible protected exports.
 * Last changed: 2026-11-23, introduce crash-safe export records.
 */
package com.kp.filemanager.export

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

internal data class ExportRecord(val source: String, val destination: String, val size: Long)

internal class ExportJournal(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "protected_export_journal.json"))

    @Synchronized
    fun entries(): List<ExportRecord> = load().first

    @Synchronized
    fun temporaryFiles(): List<String> = load().second

    @Synchronized
    fun addTemporary(path: String) = update { _, temps ->
        if (path !in temps) temps.add(path)
    }

    @Synchronized
    fun removeTemporary(path: String) = update { _, temps -> temps.remove(path) }

    @Synchronized
    fun addCompleted(records: List<ExportRecord>) = update { entries, _ ->
        records.forEach { record ->
            if (entries.none { it.source == record.source && it.destination == record.destination }) {
                entries.add(record)
            }
        }
    }

    @Synchronized
    fun removeCompleted(record: ExportRecord) = update { entries, _ -> entries.remove(record) }

    private fun update(mutator: (MutableList<ExportRecord>, MutableList<String>) -> Unit) {
        val (entries, temps) = load()
        mutator(entries, temps)
        val payload = JSONObject().apply {
            put("version", 1)
            put("records", JSONArray().apply {
                entries.forEach { record ->
                    put(JSONObject().apply {
                        put("source", record.source)
                        put("destination", record.destination)
                        put("size", record.size)
                    })
                }
            })
            put("temporary", JSONArray(temps))
        }.toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(payload)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun load(): Pair<MutableList<ExportRecord>, MutableList<String>> {
        // openRead can recover an AtomicFile backup after an interrupted journal write.
        val payload = try {
            file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (error: FileNotFoundException) {
            return mutableListOf<ExportRecord>() to mutableListOf()
        }
        val root = JSONObject(payload)
        check(root.optInt("version") == 1) { "Unsupported export recovery journal version" }
        val items = root.getJSONArray("records")
        val entries = MutableList(items.length()) { index ->
            val item = items.getJSONObject(index)
            ExportRecord(item.getString("source"), item.getString("destination"), item.getLong("size"))
        }
        val partial = root.getJSONArray("temporary")
        val temps = MutableList(partial.length()) { partial.getString(it) }
        return entries to temps
    }
}
