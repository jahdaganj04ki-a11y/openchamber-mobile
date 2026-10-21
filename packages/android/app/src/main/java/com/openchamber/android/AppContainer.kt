package com.openchamber.android

import android.content.Context
import com.openchamber.android.core.api.MessageStreamClient
import com.openchamber.android.core.runtime.RuntimeSupervisor
import com.openchamber.android.core.sync.EventPipeline
import com.openchamber.android.core.sync.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The object graph of the app.
 *
 * The pieces are created once and live as long as the process. The event
 * pipeline is the only one that is rebuilt, because it needs the port the
 * server printed, and the port is only known after the server started.
 */
class AppContainer(context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val supervisor = RuntimeSupervisor(context, scope)
    val store = SyncStore(scope)
    val preferences = AppPreferences(context)
    val catalogStore = com.openchamber.android.i18n.CatalogStore(context)

    private val _pipeline = MutableStateFlow<EventPipeline?>(null)
    val pipeline: StateFlow<EventPipeline?> = _pipeline.asStateFlow()

    init {
        scope.launch {
            supervisor.state.collect { state ->
                if (state is com.openchamber.android.core.runtime.RuntimeState.Ready) {
                    connectStream()
                }
            }
        }
    }

    private var connectedPort: Int? = null

    private fun connectStream() {
        val port = supervisor.listeningPort ?: return
        if (connectedPort == port) return
        connectedPort = port
        _pipeline.value?.stop()
        val client = MessageStreamClient(
            baseUrl = "http://127.0.0.1:$port".toHttpUrl(),
            tokenProvider = { null },
        )
        val created = EventPipeline(client, store, scope)
        created.onServerConnected = { refreshSessions() }
        _pipeline.value = created
        created.start()
    }

    /** Reads the sessions of a directory from the server. */
    suspend fun refreshSessions(directory: String? = null): List<com.openchamber.android.core.model.Session> {
        val client = supervisor.openCodeClient ?: return emptyList()
        return runCatching { client.listSessions(directory) }
            .getOrElse { emptyList() }
            .map { com.openchamber.android.core.model.Projection.projectSession(it) }
            .also { sessions ->
                sessions.forEach { store.apply(
                    com.openchamber.android.core.sync.SyncEvent.SessionCreated(it),
                    directory ?: com.openchamber.android.core.sync.GLOBAL_EVENT_DIRECTORY,
                ) }
            }
    }
}
