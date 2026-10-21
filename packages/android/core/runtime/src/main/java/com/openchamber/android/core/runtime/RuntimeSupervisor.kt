package com.openchamber.android.core.runtime

import android.content.Context
import com.openchamber.android.core.api.ApiException
import com.openchamber.android.core.api.OpenChamberClient
import com.openchamber.android.core.api.OpenCodeClient
import com.openchamber.android.core.api.RuntimeTransport
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * What the app knows about the server and the engine.
 *
 * The two are separate on purpose. The server can be up while the engine is
 * still starting, and the app shows that difference instead of one "loading".
 */
sealed interface RuntimeState {
    data object Stopped : RuntimeState
    data class Starting(val stage: String) : RuntimeState
    data class ServerReady(val port: Int) : RuntimeState
    data class EngineStarting(val port: Int) : RuntimeState
    data class Ready(val port: Int) : RuntimeState
    data class Failed(val reason: String) : RuntimeState
}

/**
 * The supervisor of the two child processes.
 *
 * The server is the desktop's own server, started on the loopback interface.
 * The engine is the server's own child, so the app never starts it directly.
 * The supervisor starts the server, reads the port it printed, waits for the
 * health route, mints a client token, and waits for the engine through the
 * engine health route. A server that dies is started again with a backoff.
 */
class RuntimeSupervisor(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    val paths = DevicePaths(appContext)
    private val installer = BinaryInstaller(appContext, paths)
    private val tokenStore = ClientTokenStore(appContext)

    val logs = LogRingBuffer()

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Stopped)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private var process: Process? = null
    private var supervisorJob: Job? = null
    private var restartDelayMs = RESTART_DELAY_MS

    @Volatile
    private var token: String? = null

    @Volatile
    private var port: Int? = null

    /** The transport to the running server, or nothing while it is not up. */
    val transport: RuntimeTransport?
        get() = port?.let { activePort ->
            RuntimeTransport(
                baseUrl = "http://127.0.0.1:$activePort".toHttpUrl(),
                tokenProvider = { token },
            )
        }

    val openCodeClient: OpenCodeClient? get() = transport?.let { OpenCodeClient(it) }
    val openChamberClient: OpenChamberClient? get() = transport?.let { OpenChamberClient(it) }

    /** The port the server listens on, or nothing while it is not up. */
    val listeningPort: Int? get() = port

    /**
     * Starts the server and waits until the engine answers.
     *
     * The supervisor keeps running: a server that exits is started again with
     * a growing backoff, because the phone may have killed it for memory.
     */
    fun start() {
        if (supervisorJob?.isActive == true) return
        supervisorJob = scope.launch {
            while (isActive) {
                try {
                    runOnce()
                    restartDelayMs = RESTART_DELAY_MS
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    _state.value = RuntimeState.Failed(error.message ?: "The server stopped")
                    logs.append(error.stackTraceToString(), "supervisor")
                    delay(restartDelayMs)
                    restartDelayMs = (restartDelayMs * 2).coerceAtMost(RESTART_DELAY_CAP_MS)
                }
            }
        }
    }

    /** Stops the server and the engine it owns. */
    suspend fun stop() {
        supervisorJob?.cancel()
        supervisorJob = null
        terminateProcess()
        _state.value = RuntimeState.Stopped
    }

    /**
     * Mints a new client token. The server replaces the record that carries
     * the same dedupe key, so the phone never piles up tokens.
     */
    suspend fun refreshToken(): String? {
        val client = openChamberClient ?: return null
        val minted = client.createClient(label = "OpenChamber on Android")
        tokenStore.write(minted.token)
        token = minted.token
        return minted.token
    }

    private suspend fun runOnce() {
        _state.value = RuntimeState.Starting("Preparing the bundled binaries")
        if (!installer.isInstalled()) {
            withContext(Dispatchers.IO) { installer.install() }
        }

        _state.value = RuntimeState.Starting("Starting the server")
        val resolvedPort = withContext(Dispatchers.IO) { startServerProcess() }
        port = resolvedPort
        _state.value = RuntimeState.ServerReady(resolvedPort)

        val client = OpenChamberClient(
            RuntimeTransport(
                baseUrl = "http://127.0.0.1:$resolvedPort".toHttpUrl(),
                tokenProvider = { token },
            ),
        )
        awaitServerHealth(client)
        _state.value = RuntimeState.EngineStarting(resolvedPort)
        awaitEngineHealth(client)

        token = tokenStore.read()
        if (token.isNullOrBlank()) {
            token = refreshToken()
        }
        _state.value = RuntimeState.Ready(resolvedPort)
    }

    private suspend fun awaitServerHealth(client: OpenChamberClient) {
        var lastError: Throwable? = null
        repeat(SERVER_HEALTH_ATTEMPTS) {
            try {
                client.health()
                return
            } catch (error: ApiException) {
                lastError = error
            } catch (error: IOException) {
                lastError = error
            }
            delay(SERVER_HEALTH_INTERVAL_MS)
        }
        throw IOException("The server did not answer the health route", lastError)
    }

    private suspend fun awaitEngineHealth(client: OpenChamberClient) {
        var lastError: Throwable? = null
        repeat(ENGINE_HEALTH_ATTEMPTS) {
            try {
                val health = client.engineHealth(waitMs = ENGINE_HEALTH_WAIT_MS)
                if (health.healthy) return
                lastError = IOException(health.error ?: "The engine is not ready yet")
            } catch (error: IOException) {
                lastError = error
            }
            delay(ENGINE_HEALTH_INTERVAL_MS)
        }
        throw IOException("The engine did not become ready", lastError)
    }

    /**
     * Starts the server and waits for the line it prints with the port.
     *
     * Both streams are drained into the log buffer. A pipe nobody reads
     * blocks the writer, and a blocked server never prints its port, so one
     * reader owns the standard output and hands the port line over.
     */
    private suspend fun startServerProcess(): Int {
        val command = listOf(paths.serverBinary.absolutePath)
        val builder = ProcessBuilder(command)
        builder.environment().putAll(paths.serverEnvironment(0))
        builder.directory(paths.dataDir)
        val child = builder.start()
        process = child

        val portReady = CompletableDeferred<Int>()
        scope.launch(Dispatchers.IO) {
            runCatching {
                BufferedReader(InputStreamReader(child.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        logs.append(line, "server")
                        val port = parsePort(line)
                        if (port != null && !portReady.isCompleted) portReady.complete(port)
                    }
                }
            }
            if (!portReady.isCompleted) {
                portReady.completeExceptionally(IOException("The server stopped before it reported its port"))
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                BufferedReader(InputStreamReader(child.errorStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        logs.append(line, "server")
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            val code = runCatching { child.waitFor() }.getOrDefault(-1)
            logs.append("The server exited with code $code", "supervisor")
        }

        return withTimeoutOrNull(SERVER_START_TIMEOUT_MS) { portReady.await() }
            ?: throw IOException("The server did not report the port it listens on")
    }

    private fun parsePort(line: String): Int? {
        val marker = "\"type\":\"openchamber.listening\""
        if (!line.contains(marker)) return null
        val portMatch = Regex("\"port\":(\\d+)").find(line) ?: return null
        return portMatch.groupValues[1].toIntOrNull()
    }

    private fun terminateProcess() {
        val child = process ?: return
        process = null
        runCatching { child.destroy() }
        if (!child.waitFor(TERMINATE_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            runCatching { child.destroyForcibly() }
        }
    }

    companion object {
        const val RESTART_DELAY_MS = 1_000L
        const val RESTART_DELAY_CAP_MS = 30_000L
        const val SERVER_START_TIMEOUT_MS = 30_000L
        const val SERVER_HEALTH_ATTEMPTS = 60
        const val SERVER_HEALTH_INTERVAL_MS = 500L
        const val ENGINE_HEALTH_ATTEMPTS = 120
        const val ENGINE_HEALTH_INTERVAL_MS = 500L
        const val ENGINE_HEALTH_WAIT_MS = 8_000
        const val TERMINATE_GRACE_MS = 5_000L
    }
}
