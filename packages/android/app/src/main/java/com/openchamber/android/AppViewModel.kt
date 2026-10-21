package com.openchamber.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openchamber.android.core.design.ThemeMode
import com.openchamber.android.core.model.FormRequest
import com.openchamber.android.core.model.Message
import com.openchamber.android.core.model.Part
import com.openchamber.android.core.model.PermissionRequest
import com.openchamber.android.core.model.Project
import com.openchamber.android.core.model.Session
import com.openchamber.android.core.runtime.RuntimeState
import com.openchamber.android.core.sync.GLOBAL_EVENT_DIRECTORY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the screens read.
 *
 * The state is a snapshot of the store for the directory the app is looking
 * at. The store keeps the truth; this picks the part the phone shows.
 */
data class AppUiState(
    val runtime: RuntimeState = RuntimeState.Stopped,
    val themeMode: ThemeMode = ThemeMode.System,
    val directory: String? = null,
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val selectedSessionID: String? = null,
    val messages: List<Message> = emptyList(),
    val parts: Map<String, List<Part>> = emptyMap(),
    val permissions: List<PermissionRequest> = emptyList(),
    val forms: List<FormRequest> = emptyList(),
    val draft: String = "",
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val selectedSession = MutableStateFlow<String?>(null)
    private val directory = MutableStateFlow<String?>(null)
    private val projects = MutableStateFlow<List<Project>>(emptyList())
    private val draft = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AppUiState> = combine(
        combine(
            container.supervisor.state,
            container.preferences.themeMode,
            container.store.version,
        ) { runtime, theme, _ -> Triple(runtime, theme, Unit) },
        combine(selectedSession, directory, projects) { session, dir, list -> Triple(session, dir, list) },
        combine(draft, refreshing, error) { text, busy, failure -> Triple(text, busy, failure) },
    ) { runtimePart, selectionPart, inputPart ->
        val (runtime, theme, _) = runtimePart
        val (sessionID, dir, projectList) = selectionPart
        val (draftText, busy, failure) = inputPart
        val scope = dir ?: GLOBAL_EVENT_DIRECTORY
        val directoryState = container.store.state(scope)
        AppUiState(
            runtime = runtime,
            themeMode = theme,
            directory = dir,
            projects = projectList,
            sessions = directoryState.sessionOrder
                .mapNotNull { directoryState.sessions[it] }
                .sortedByDescending { it.time.updated },
            selectedSessionID = sessionID,
            messages = sessionID?.let { directoryState.messagesFor(it) }.orEmpty(),
            parts = sessionID?.let { directoryState.messagesFor(it) }
                ?.associate { message -> message.id to directoryState.partsFor(scope, message.id) }
                .orEmpty(),
            permissions = directoryState.permissions.values
                .filter { sessionID == null || it.sessionID == sessionID },
            forms = directoryState.forms.values
                .filter { sessionID == null || it.sessionID == sessionID },
            draft = draftText,
            isRefreshing = busy,
            error = failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            container.preferences.lastDirectory.collect { stored ->
                if (directory.value == null && stored != null) {
                    directory.value = stored
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            container.preferences.themeMode.collect { mode ->
                // The theme is part of the state; the flow above already
                // carries it, and the settings screen writes it back.
            }
        }
        viewModelScope.launch {
            container.preferences.draft.collect { draft.value = it }
        }
        viewModelScope.launch {
            container.preferences.lastSessionID.collect { stored ->
                if (selectedSession.value == null && stored != null) {
                    selectedSession.value = stored
                }
            }
        }
    }

    fun selectDirectory(value: String?) {
        directory.value = value
        selectedSession.value = null
        viewModelScope.launch { container.preferences.setLastDirectory(value) }
        refresh()
    }

    fun selectSession(sessionID: String?) {
        selectedSession.value = sessionID
        viewModelScope.launch { container.preferences.setLastSession(sessionID) }
        if (sessionID != null) loadMessages(sessionID)
    }

    fun setDraft(text: String) {
        draft.value = text
        viewModelScope.launch { container.preferences.setDraft(text) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferences.setThemeMode(mode) }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            runCatching { container.refreshSessions(directory.value) }
                .onFailure { error.value = it.message }
            projects.value = loadProjects()
            refreshing.value = false
        }
    }

    fun send(text: String) {
        val sessionID = selectedSession.value ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            val client = container.supervisor.openCodeClient
            if (client == null) {
                error.value = "The server is not up yet"
                return@launch
            }
            runCatching { client.prompt(sessionID, body, directory = directory.value) }
                .onFailure { error.value = it.message }
            setDraft("")
        }
    }

    fun replyPermission(requestID: String, decision: String) {
        val sessionID = selectedSession.value ?: return
        viewModelScope.launch {
            val client = container.supervisor.openCodeClient ?: return@launch
            runCatching {
                client.replyPermission(sessionID, requestID, decision, directory = directory.value)
            }.onFailure { error.value = it.message }
        }
    }

    fun replyForm(formID: String, answer: kotlinx.serialization.json.JsonObject) {
        val sessionID = selectedSession.value ?: return
        viewModelScope.launch {
            val client = container.supervisor.openCodeClient ?: return@launch
            runCatching {
                client.replyForm(sessionID, formID, answer, directory = directory.value)
            }.onFailure { error.value = it.message }
        }
    }

    fun createSession() {
        viewModelScope.launch {
            val client = container.supervisor.openCodeClient ?: return@launch
            runCatching {
                client.createSession(title = null, directory = directory.value)
            }.onSuccess { session ->
                selectSession(session.id)
                refresh()
            }.onFailure { error.value = it.message }
        }
    }

    fun dismissError() {
        error.value = null
    }

    private suspend fun loadMessages(sessionID: String) {
        val client = container.supervisor.openCodeClient ?: return
        runCatching {
            client.listMessages(sessionID, limit = 200, order = "asc", directory = directory.value)
        }.onSuccess { page ->
            val scope = directory.value ?: GLOBAL_EVENT_DIRECTORY
            page.items.forEach { info ->
                val projected = com.openchamber.android.core.model.Projection.projectMessage(info, sessionID)
                container.store.apply(
                    com.openchamber.android.core.sync.SyncEvent.MessageUpdated(projected.message),
                    scope,
                )
                if (projected.parts.isNotEmpty()) {
                    container.store.apply(
                        com.openchamber.android.core.sync.SyncEvent.MessagePartsReplaced(
                            sessionID,
                            projected.message.id,
                            projected.parts,
                        ),
                        scope,
                    )
                }
            }
        }.onFailure { error.value = it.message }
    }

    private suspend fun loadProjects(): List<Project> {
        val client = container.supervisor.openChamberClient ?: return emptyList()
        return runCatching {
            client.listDirectory(container.supervisor.paths.projectsDir.absolutePath)
                .entries
                .filter { it.isDirectory }
                .map { entry ->
                    Project(
                        id = entry.path,
                        worktree = entry.path,
                        name = entry.name,
                    )
                }
        }.getOrElse { emptyList() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
