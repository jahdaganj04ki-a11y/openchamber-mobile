package com.openchamber.android.core.sync

import com.openchamber.android.core.api.MessageStreamClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the stream is doing right now. */
sealed interface StreamState {
    data object Disconnected : StreamState
    data class Connecting(val transport: Transport) : StreamState
    data class Connected(val transport: Transport) : StreamState
    data class Failed(val reason: String, val transport: Transport) : StreamState

    enum class Transport { WebSocket, ServerSentEvents }
}

/**
 * The event pipeline.
 *
 * The app prefers the WebSocket and falls back to the server-sent event
 * stream when the WebSocket fails before it opens, or when it opens and dies
 * again straight away. The fallback lasts a minute, so a broken socket does
 * not turn into a reconnect loop.
 */
class EventPipeline(
    private val streamClient: MessageStreamClient,
    private val store: SyncStore,
    private val scope: CoroutineScope,
    private val reconnectDelayMs: Long = 1_000,
    private val reconnectDelayCapMs: Long = 30_000,
) {
    private var job: Job? = null
    private var wsFallbackUntil = 0L
    private var delayMs = reconnectDelayMs

    private val _state = MutableStateFlow<StreamState>(StreamState.Disconnected)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    /** Called when the stream reports that the server is back. */
    var onServerConnected: (suspend () -> Unit)? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = StreamState.Disconnected
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val transport = resolveTransport()
            _state.value = StreamState.Connecting(transport)
            try {
                when (transport) {
                    StreamState.Transport.WebSocket -> runWebSocket()
                    StreamState.Transport.ServerSentEvents -> runServerSentEvents()
                }
                delayMs = reconnectDelayMs
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.value = StreamState.Failed(
                    reason = error.message ?: "The event stream stopped",
                    transport = transport,
                )
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(reconnectDelayCapMs)
            }
        }
    }

    private fun resolveTransport(): StreamState.Transport =
        if (wsFallbackUntil > System.currentTimeMillis()) {
            StreamState.Transport.ServerSentEvents
        } else {
            StreamState.Transport.WebSocket
        }

    private suspend fun runWebSocket() {
        var opened = false
        val readyAt = System.currentTimeMillis()
        streamClient.webSocketEvents()
            .onEach {
                opened = true
                _state.value = StreamState.Connected(StreamState.Transport.WebSocket)
                handle(it)
            }
            .collect()
        // The socket closed without an error. A socket that lived less than
        // two seconds is treated as unstable and moves the app to the
        // server-sent event stream for a minute.
        if (System.currentTimeMillis() - readyAt < UNSTABLE_AFTER_READY_MS) {
            wsFallbackUntil = System.currentTimeMillis() + WS_FALLBACK_WINDOW_MS
        }
        if (!opened) {
            wsFallbackUntil = System.currentTimeMillis() + WS_FALLBACK_WINDOW_MS
        }
    }

    private suspend fun runServerSentEvents() {
        streamClient.serverSentEvents()
            .onEach {
                _state.value = StreamState.Connected(StreamState.Transport.ServerSentEvents)
                handle(it)
            }
            .collect()
    }

    private suspend fun handle(event: com.openchamber.android.core.api.dto.WireEventDto) {
        val routed = EventTranslator.route(event)
        store.applyAll(routed)
        if (routed.any { it.event is SyncEvent.ServerConnected }) {
            onServerConnected?.invoke()
        }
    }

    companion object {
        const val WS_FALLBACK_WINDOW_MS = 60_000L
        const val UNSTABLE_AFTER_READY_MS = 2_000L
    }
}
