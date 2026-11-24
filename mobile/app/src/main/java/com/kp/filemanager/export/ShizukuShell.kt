/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ShizukuShell.kt
 * Purpose: Executes checked Shizuku shell commands with a UserService and legacy compatibility transport.
 * Last changed: 2026-11-24, recover from UserService startup failures with the authorized legacy process transport.
 */
package com.kp.filemanager.export

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

internal data class ShellExecutionResult(
    val stdout: ByteArray,
    val stderr: ByteArray,
    val exitCode: Int,
)

internal class ShellCommandExecutor(
    private val processStarter: (String) -> Process = { command ->
        Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    },
) {
    private val running = ConcurrentHashMap<String, Process>()

    fun execute(commandId: String, command: String): ShellExecutionResult {
        val process = processStarter(command)
        if (running.putIfAbsent(commandId, process) != null) {
            runCatching { process.destroy() }
            error("Duplicate shell command identifier")
        }
        return try {
            collectProcessResult(process)
        } finally {
            running.remove(commandId, process)
        }
    }

    fun cancel(commandId: String) {
        running[commandId]?.destroy()
    }

    fun close() {
        running.values.forEach { it.destroy() }
        running.clear()
    }
}

// Drain both streams concurrently, preserving binary stdout and the real exit status.
internal fun collectProcessResult(process: Process): ShellExecutionResult {
    var stderr = ByteArray(0)
    var stderrFailure: Exception? = null
    val stderrThread = Thread {
        try {
            stderr = process.errorStream.use { it.readBytes() }
        } catch (error: Exception) {
            stderrFailure = error
            runCatching { process.destroy() }
        }
    }
    return try {
        process.outputStream.close()
        stderrThread.start()
        val stdout = process.inputStream.use { it.readBytes() }
        val exitCode = process.waitFor()
        stderrThread.join()
        stderrFailure?.let { throw IOException("Cannot read Shizuku command stderr", it) }
        ShellExecutionResult(stdout, stderr, exitCode)
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("Shell command interrupted", error)
    } finally {
        runCatching { process.destroy() }
    }
}

// The public Shizuku 13.1.5 AIDL still has newProcess even though the facade
// made it private. Use the authorized server, never the app UID or su.
private class LegacyRemoteProcess(private val remote: IRemoteProcess) : Process() {
    override fun getInputStream(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
    override fun getErrorStream(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
    override fun getOutputStream(): OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
    override fun waitFor(): Int = remote.waitFor()
    override fun exitValue(): Int = remote.exitValue()
    override fun destroy() = remote.destroy()
}

class ShizukuShellUserService : IShizukuShellService.Stub() {
    private val executor = ShellCommandExecutor()

    override fun execute(commandId: String, command: String): Bundle {
        val result = executor.execute(commandId, command)
        return Bundle().apply {
            putByteArray(RESULT_STDOUT, result.stdout)
            putByteArray(RESULT_STDERR, result.stderr)
            putInt(RESULT_EXIT_CODE, result.exitCode)
        }
    }

    override fun cancel(commandId: String) = executor.cancel(commandId)

    override fun destroy() {
        executor.close()
        System.exit(0)
    }
}

class ShizukuShell(context: Context) {
    private val appContext = context.applicationContext
    private val startupLock = Any()
    private val stateLock = Any()
    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext, ShizukuShellUserService::class.java))
            .daemon(false)
            .debuggable(appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .processNameSuffix("kp_shell")
            .tag("kp-protected-export-shell")
            .version(USER_SERVICE_VERSION)
    }
    @Volatile private var connection: ConnectionAttempt? = null
    @Volatile private var legacyBinder: IBinder? = null
    @Volatile private var closed = false
    private val legacyProcesses = ConcurrentHashMap.newKeySet<Process>()

    private class ConnectionAttempt(val server: IBinder) : ServiceConnection {
        val ready = CountDownLatch(1)
        @Volatile var service: IShizukuShellService? = null

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShizukuShellService.Stub.asInterface(binder)
            ready.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            ready.countDown()
        }
    }

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isAuthorized(): Boolean = isRunning() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        require(isRunning()) { "Start Shizuku first." }
        Shizuku.requestPermission(requestCode)
    }

    fun start(command: String): ShizukuCommand = synchronized(startupLock) {
        val server = authorizedServer()
        val id = UUID.randomUUID().toString()
        var startupFailure: Exception? = null
        if (legacyBinder !== server) {
            val userService = try {
                requireService(server)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while starting the Shizuku command service.", error)
            } catch (error: Exception) {
                // Only fall back before execute() is ever called. Never replay a command.
                startupFailure = error
                Log.w(TAG, "UserService startup failed; trying legacy Shizuku transport", error)
                null
            }
            if (userService != null) {
                return@synchronized ShizukuCommand(
                    execute = {
                        val result = userService.execute(id, command)
                        ShellExecutionResult(
                            result.getByteArray(RESULT_STDOUT) ?: ByteArray(0),
                            result.getByteArray(RESULT_STDERR) ?: ByteArray(0),
                            result.getInt(RESULT_EXIT_CODE),
                        )
                    },
                    cancel = { userService.cancel(id) },
                )
            }
        }
        // Recheck permission and server identity after the potentially long startup wait.
        if (authorizedServer() !== server) throw IOException("Shizuku restarted. Retry the operation.")
        val process = try {
            val remote = IShizukuService.Stub.asInterface(server)
                .newProcess(arrayOf("sh", "-c", command), null, null)
                ?: throw IOException("Shizuku returned no remote process")
            LegacyRemoteProcess(remote)
        } catch (error: Exception) {
            legacyBinder = null
            throw IOException(
                "Shizuku is authorized but its command transport failed. " +
                    (startupFailure?.let { "UserService: ${it.message}. " } ?: "") +
                    "Legacy process: ${error.message ?: error.javaClass.simpleName}. " +
                    "Restart Shizuku and check its service logs.",
                error,
            )
        }
        synchronized(stateLock) {
            if (closed) {
                runCatching { process.destroy() }
                throw IOException("Shizuku command bridge is closed.")
            }
            legacyProcesses.add(process)
            legacyBinder = server
        }
        ShizukuCommand(
            execute = {
                try {
                    collectProcessResult(process)
                } finally {
                    legacyProcesses.remove(process)
                }
            },
            cancel = {
                try {
                    process.destroy()
                } finally {
                    legacyProcesses.remove(process)
                }
            },
        )
    }

    fun output(command: String): ByteArray = collect(start(command))

    fun check(command: String): String = output(command).toString(Charsets.UTF_8).trim()

    fun collect(command: ShizukuCommand): ByteArray {
        val result = command.collectResult()
        if (result.exitCode != 0) {
            val detail = result.stderr.toString(Charsets.UTF_8).trim().ifEmpty {
                result.stdout.toString(Charsets.UTF_8).trim().ifEmpty { "Exit code ${result.exitCode}" }
            }
            throw IOException(detail.take(500))
        }
        return result.stdout
    }

    fun close() {
        val previous = synchronized(stateLock) {
            closed = true
            legacyBinder = null
            connection.also {
                connection = null
                it?.ready?.countDown()
            }
        }
        previous?.let { unbind(it) }
        legacyProcesses.forEach { runCatching { it.destroy() } }
        legacyProcesses.clear()
    }

    private fun authorizedServer(): IBinder {
        if (closed) throw IOException("Shizuku command bridge is closed.")
        if (!isAuthorized()) throw SecurityException("Shizuku is disconnected or permission was revoked.")
        return Shizuku.getBinder()?.takeIf { it.isBinderAlive }
            ?: throw IOException("Shizuku disconnected. Retry the operation.")
    }

    private fun requireService(server: IBinder): IShizukuShellService {
        val old = connection
        old?.takeIf { it.server === server }?.service
            ?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        if (old != null) detach(old)
        val attempt = ConnectionAttempt(server)
        try {
            synchronized(stateLock) {
                if (closed) throw IOException("Shizuku command bridge is closed.")
                connection = attempt
                Shizuku.bindUserService(serviceArgs, attempt)
            }
            if (!attempt.ready.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("Timed out while starting the Shizuku UserService")
            }
            if (closed || connection !== attempt || authorizedServer() !== server) {
                throw IOException("Shizuku disconnected before the command service was ready")
            }
            return attempt.service?.takeIf { it.asBinder().isBinderAlive }
                ?: throw IOException("Shizuku command service disconnected before it was ready")
        } catch (error: Exception) {
            detach(attempt)
            throw error
        }
    }

    private fun detach(attempt: ConnectionAttempt) {
        val removed = synchronized(stateLock) {
            if (connection !== attempt) false else {
                connection = null
                true
            }
        }
        if (removed) unbind(attempt)
    }

    private fun unbind(attempt: ConnectionAttempt) {
        runCatching { Shizuku.unbindUserService(serviceArgs, attempt, true) }
            .onFailure { Log.w(TAG, "Could not remove Shizuku UserService", it) }
    }

    private companion object {
        const val TAG = "KpShizukuShell"
        const val USER_SERVICE_VERSION = 2
        const val CONNECTION_TIMEOUT_SECONDS = 10L
    }
}

class ShizukuCommand internal constructor(
    private val execute: () -> ShellExecutionResult,
    private val cancel: () -> Unit,
) {
    private val active = AtomicBoolean(true)
    private val collected = AtomicBoolean(false)
    val isAlive: Boolean get() = active.get()

    fun destroy() {
        if (active.getAndSet(false)) runCatching { cancel() }
    }

    internal fun collectResult(): ShellExecutionResult {
        check(collected.compareAndSet(false, true)) { "Command already collected" }
        if (!active.get()) throw IOException("Shizuku command cancelled")
        return try {
            execute()
        } finally {
            active.set(false)
        }
    }
}

private const val RESULT_STDOUT = "stdout"
private const val RESULT_STDERR = "stderr"
private const val RESULT_EXIT_CODE = "exitCode"
