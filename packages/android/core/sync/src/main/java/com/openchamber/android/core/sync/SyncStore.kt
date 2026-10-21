package com.openchamber.android.core.sync

import com.openchamber.android.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * The live state of every directory the app knows about.
 *
 * The store is the only writer of that state. The event pipeline applies
 * events to it, and the screens read from it.
 */
class SyncStore(scope: CoroutineScope) {

    private val directories = mutableMapOf<String, DirectoryState>()
    private val _version = MutableStateFlow(0)

    /** Bumped on every change, so a screen can recompose without a diff. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun state(directory: String): DirectoryState =
        directories.getOrPut(directory) { DirectoryState() }

    fun sessions(directory: String): List<Session> =
        state(directory).sessions.values.toList()

    fun apply(event: SyncEvent, directory: String) {
        EventReducer.reduce(state(directory), event)
        _version.value += 1
    }

    fun applyAll(events: List<RoutedSyncEvent>) {
        events.forEach { apply(it.event, it.directory) }
    }

    fun forget(directory: String) {
        directories.remove(directory)
        _version.value += 1
    }

    fun clear() {
        directories.clear()
        _version.value += 1
    }

    /** The session ids that are busy, retrying or waiting for the user. */
    fun activeSessionIDs(): Set<String> = directories.values.flatMap { directory ->
        directory.statuses.filter { (_, status) ->
            status !is com.openchamber.android.core.model.SessionStatus.Idle
        }.keys
    }.toSet()
}
