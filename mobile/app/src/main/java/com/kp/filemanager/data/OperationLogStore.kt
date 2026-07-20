/** mobile/app/src/main/java/com/kp/filemanager/data/OperationLogStore.kt: Writes sanitized internal operation logs; created for preview and troubleshooting parity. */
package com.kp.filemanager.data

import android.content.Context
import java.time.Instant

class OperationLogStore(context: Context) {
    private val logFile = context.filesDir.resolve("operation_logs.txt")

    fun replace(operation: String, lines: List<String>) {
        logFile.bufferedWriter().use { writer ->
            writer.appendLine("${Instant.now()} [$operation]")
            lines.forEach(writer::appendLine)
        }
    }
}
