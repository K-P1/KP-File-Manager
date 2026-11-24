/**
 * Path: mobile/app/src/test/java/com/kp/filemanager/export/ShizukuShellTest.kt
 * Purpose: Verifies the process execution core used by the Shizuku user-service bridge.
 * Last changed: 2026-11-24, cover shared process collection and one-shot command cancellation.
 */
package com.kp.filemanager.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ShizukuShellTest {
    @Test
    fun commandExecutorCollectsOutputErrorAndExitCode() {
        val process = FakeProcess(
            stdout = byteArrayOf(1, 0, 2),
            stderr = "permission denied".toByteArray(),
            exitCode = 13,
        )
        val executor = ShellCommandExecutor { process }

        val result = executor.execute("command-1", "ignored")

        assertArrayEquals(byteArrayOf(1, 0, 2), result.stdout)
        assertEquals("permission denied", result.stderr.toString(Charsets.UTF_8))
        assertEquals(13, result.exitCode)
    }

    @Test
    fun cancelledCommandDoesNotExecute() {
        var executions = 0
        var cancellations = 0
        val command = ShizukuCommand(
            execute = { executions++; ShellExecutionResult(byteArrayOf(), byteArrayOf(), 0) },
            cancel = { cancellations++ },
        )
        command.destroy()
        command.destroy()
        try {
            command.collectResult()
            fail("Cancelled command must not run")
        } catch (_: IOException) {
            // Expected.
        }
        assertEquals(0, executions)
        assertEquals(1, cancellations)
        assertFalse(command.isAlive)
    }

    @Test
    fun failedCommandCannotBeCollectedAgain() {
        var executions = 0
        val command = ShizukuCommand(
            execute = { executions++; throw IOException("transport lost") },
            cancel = {},
        )
        try {
            command.collectResult()
            fail("Expected command failure")
        } catch (_: IOException) {
            // Expected.
        }
        try {
            command.collectResult()
            fail("Command must not be replayed")
        } catch (_: IllegalStateException) {
            // Expected.
        }
        assertEquals(1, executions)
        assertFalse(command.isAlive)
    }

    private class FakeProcess(
        stdout: ByteArray,
        stderr: ByteArray,
        private val exitCode: Int,
    ) : Process() {
        private val input = ByteArrayInputStream(stdout)
        private val error = ByteArrayInputStream(stderr)

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = input
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = exitCode
        override fun exitValue(): Int = exitCode
        override fun destroy() = Unit
    }
}
