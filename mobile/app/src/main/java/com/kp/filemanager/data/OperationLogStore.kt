/** mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt: Writes sanitized internal operation logs; created for preview and troubleshooting parity. */
package com.kp.filemanager.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OperationLogStore(context: Context) {
    private val logFile = context.filesDir.resolve("operation_logs.txt")

    fun replace(operation: String, lines: List<String>) {
        logFile.bufferedWriter().use { writer ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).format(Date())
            writer.appendLine("$timestamp [$operation]")
            lines.forEach(writer::appendLine)
        }
    }
}
