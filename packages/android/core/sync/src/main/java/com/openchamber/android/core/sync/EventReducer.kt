package com.openchamber.android.core.sync

import com.openchamber.android.core.model.AssistantMessage
import com.openchamber.android.core.model.CompactionMessage
import com.openchamber.android.core.model.Message
import com.openchamber.android.core.model.MessageTime
import com.openchamber.android.core.model.Part
import com.openchamber.android.core.model.PatchValue
import com.openchamber.android.core.model.ReasoningPart
import com.openchamber.android.core.model.Session
import com.openchamber.android.core.model.SessionStatus
import com.openchamber.android.core.model.ShellMessage
import com.openchamber.android.core.model.TextPart
import com.openchamber.android.core.model.ToolPart
import com.openchamber.android.core.model.ToolState
import com.openchamber.android.core.model.resolve
import com.openchamber.android.core.model.withTime

/**
 * The state the app holds for one directory.
 *
 * The session list and the transcripts live here, keyed by id. A store owns
 * the truth for the directory it was made for; nothing reads another
 * directory's store.
 */
class DirectoryState {
    val sessions: MutableMap<String, Session> = linkedMapOf()
    val sessionOrder: MutableList<String> = mutableListOf()
    val messages: MutableMap<String, MutableList<Message>> = linkedMapOf()
    val parts: MutableMap<String, MutableMap<String, MutableMap<String, Part>>> = linkedMapOf()
    val statuses: MutableMap<String, SessionStatus> = linkedMapOf()
    val permissions: MutableMap<String, com.openchamber.android.core.model.PermissionRequest> = linkedMapOf()
    val forms: MutableMap<String, com.openchamber.android.core.model.FormRequest> = linkedMapOf()
    val shells: MutableMap<String, RunningShell> = linkedMapOf()

    fun messagesFor(sessionID: String): List<Message> = messages[sessionID].orEmpty()

    fun partsFor(sessionID: String, messageID: String): List<Part> =
        parts[sessionID]?.get(messageID)?.values?.toList().orEmpty()

    fun putSession(session: Session) {
        if (sessions.put(session.id, session) == null) {
            sessionOrder.add(session.id)
        }
    }

    fun removeSession(sessionID: String) {
        sessions.remove(sessionID)
        sessionOrder.remove(sessionID)
        statuses.remove(sessionID)
        messages.remove(sessionID)
        parts.remove(sessionID)
        permissions.entries.removeAll { it.value.sessionID == sessionID }
        forms.entries.removeAll { it.value.sessionID == sessionID }
    }
}

/**
 * The reducer: one event in, one state change out.
 *
 * The rules follow the shared sync documentation. A failed request never
 * clears state, an unknown event leaves the state alone, and a patch that
 * mentions no field changes no field.
 */
object EventReducer {

    fun reduce(state: DirectoryState, event: SyncEvent): DirectoryState {
        when (event) {
            is SyncEvent.SessionCreated -> {
                state.putSession(event.info)
            }

            is SyncEvent.SessionPatched -> applySessionPatch(state, event)

            is SyncEvent.SessionDeleted -> state.removeSession(event.sessionID)

            is SyncEvent.SessionForked -> {
                // A fork sends no `session.created`, so the app reads the new
                // session from the server after the fork event.
            }

            is SyncEvent.SessionRevertCommitted -> {
                // The engine deletes the boundary message and everything after
                // it in one event, without a `message.removed` per record.
                trimMessagesAfter(state, event.sessionID, event.to)
                state.sessions[event.sessionID]?.let { session ->
                    state.sessions[event.sessionID] = session.copy(revert = null)
                }
            }

            is SyncEvent.SessionStatusChanged -> {
                state.statuses[event.sessionID] = event.status
            }

            is SyncEvent.SessionIdle -> {
                state.statuses[event.sessionID] = SessionStatus.Idle
            }

            is SyncEvent.SessionError -> {
                state.statuses[event.sessionID] = SessionStatus.Idle
            }

            is SyncEvent.MessageUpdated -> {
                val message = event.info
                val list = state.messages.getOrPut(message.sessionID) { mutableListOf() }
                val index = list.indexOfFirst { it.id == message.id }
                if (index >= 0) list[index] = message else list.add(message)
            }

            is SyncEvent.MessagePatched -> applyMessagePatch(state, event)

            is SyncEvent.MessageRemoved -> {
                state.messages[event.sessionID]?.removeAll { it.id == event.messageID }
                state.parts[event.sessionID]?.remove(event.messageID)
            }

            is SyncEvent.MessagePartUpdated -> {
                val byMessage = state.parts.getOrPut(event.part.sessionID) { linkedMapOf() }
                val parts = byMessage.getOrPut(event.part.messageID) { linkedMapOf<String, Part>() }
                parts[event.part.id] = event.part
            }

            is SyncEvent.MessagePartDelta -> applyPartDelta(state, event)

            is SyncEvent.MessageToolTransition -> applyToolTransition(state, event)

            is SyncEvent.MessagePartsReplaced -> {
                val byMessage = state.parts.getOrPut(event.sessionID) { linkedMapOf() }
                byMessage[event.messageID] = event.parts.associateBy { it.id }.toMutableMap()
            }

            is SyncEvent.MessageCompactionDelta -> {
                val list = state.messages[event.sessionID].orEmpty()
                val running = list.lastOrNull { it is CompactionMessage && it.status == "running" }
                if (running is CompactionMessage) {
                    val updated = running.copy(summary = running.summary + event.delta)
                    replaceMessage(state, event.sessionID, updated)
                }
            }

            is SyncEvent.PermissionAsked -> {
                state.permissions[event.request.id] = event.request
            }

            is SyncEvent.PermissionReplied -> {
                state.permissions.remove(event.requestID)
            }

            is SyncEvent.FormCreated -> {
                state.forms[event.form.id] = event.form
            }

            is SyncEvent.FormSettled -> {
                state.forms.remove(event.formID)
            }

            is SyncEvent.ShellStarted -> {
                state.shells[event.shell.id] = event.shell
            }

            is SyncEvent.ShellEnded -> {
                state.shells.remove(event.shellID)
            }

            else -> Unit
        }
        return state
    }

    private fun applySessionPatch(state: DirectoryState, event: SyncEvent.SessionPatched) {
        val session = state.sessions[event.sessionID] ?: return
        val patch = event.patch
        val updated = session.copy(
            title = patch.title.resolve(session.title) ?: session.title,
            directory = patch.directory.resolve(session.directory) ?: session.directory,
            projectID = patch.projectID.resolve(session.projectID) ?: session.projectID,
            subpath = patch.subpath.resolve(session.subpath),
            agent = patch.agent.resolve(session.agent),
            model = patch.model.resolve(session.model),
            cost = patch.cost.resolve(session.cost) ?: session.cost,
            tokens = patch.tokens.resolve(session.tokens) ?: session.tokens,
            permissions = patch.permissions.resolve(session.permissions),
            revert = patch.revert.resolve(session.revert),
            outcome = patch.outcome.resolve(session.outcome),
            metadata = patch.metadata.resolve(session.metadata),
            time = session.time.copy(
                created = patch.time.created.resolve(session.time.created) ?: session.time.created,
                updated = patch.time.updated.resolve(session.time.updated) ?: session.time.updated,
                idle = patch.time.idle.resolve(session.time.idle),
                viewed = patch.time.viewed.resolve(session.time.viewed),
                archived = patch.time.archived.resolve(session.time.archived),
            ),
        )
        state.sessions[event.sessionID] = updated
    }

    private fun applyMessagePatch(state: DirectoryState, event: SyncEvent.MessagePatched) {
        val list = state.messages[event.sessionID] ?: return
        val index = list.indexOfFirst { it.id == event.messageID }
        if (index < 0) return
        val message = list[index]
        val patch = event.patch
        val updated = when (message) {
            is AssistantMessage -> message.copy(
                time = message.time.copy(
                    created = patch.time.created.resolve(message.time.created) ?: message.time.created,
                    streamed = patch.time.streamed.resolve(message.time.streamed),
                    completed = patch.time.completed.resolve(message.time.completed),
                ),
                finish = patch.finish.resolve(message.finish),
                error = patch.error.resolve(message.error),
                cost = patch.cost.resolve(message.cost),
                tokens = patch.tokens.resolve(message.tokens),
                snapshot = patch.snapshot.resolve(message.snapshot),
                retry = patch.retry.resolve(message.retry),
            )
            is ShellMessage -> {
                val shell = patch.shell
                message.copy(
                    time = message.time.copy(
                        created = patch.time.created.resolve(message.time.created) ?: message.time.created,
                        streamed = patch.time.streamed.resolve(message.time.streamed),
                        completed = patch.time.completed.resolve(message.time.completed),
                    ),
                    status = shell?.status ?: message.status,
                    exit = shell?.exit ?: message.exit,
                    signal = shell?.signal ?: message.signal,
                    output = shell?.output ?: message.output,
                )
            }
            else -> message.withTime(
                message.time.copy(
                    created = patch.time.created.resolve(message.time.created) ?: message.time.created,
                    streamed = patch.time.streamed.resolve(message.time.streamed),
                    completed = patch.time.completed.resolve(message.time.completed),
                ),
            )
        }
        list[index] = updated
    }

    private fun applyPartDelta(state: DirectoryState, event: SyncEvent.MessagePartDelta) {
        val parts = state.parts[event.sessionID]?.get(event.messageID) ?: return
        val part = parts[event.partID] ?: return
        when (part) {
            is TextPart -> if (event.field == "text") {
                parts[event.partID] = part.copy(text = part.text + event.delta)
            }
            is ReasoningPart -> if (event.field == "text") {
                parts[event.partID] = part.copy(text = part.text + event.delta)
            }
            is ToolPart -> if (event.field == "raw") {
                val pending = part.state as? ToolState.Pending
                parts[event.partID] = part.copy(
                    state = ToolState.Pending(
                        input = pending?.input ?: kotlinx.serialization.json.JsonObject(emptyMap()),
                        raw = (pending?.raw.orEmpty()) + event.delta,
                    ),
                )
            }
            else -> Unit
        }
    }

    private fun applyToolTransition(state: DirectoryState, event: SyncEvent.MessageToolTransition) {
        val parts = state.parts[event.sessionID]?.get(event.messageID) ?: return
        val part = parts[event.partID] as? ToolPart ?: return
        val current = part.state
        val next = when (val transition = event.transition) {
            is ToolTransition.Input -> ToolState.Pending(
                input = (current as? ToolState.Pending)?.input ?: kotlinx.serialization.json.JsonObject(emptyMap()),
                raw = transition.raw,
            )
            is ToolTransition.Called -> ToolState.Running(
                input = transition.input,
                metadata = (current as? ToolState.Running)?.metadata,
                start = transition.start,
            )
            is ToolTransition.Progress -> when (current) {
                is ToolState.Running -> current.copy(metadata = transition.metadata)
                is ToolState.Completed -> current.copy(metadata = transition.metadata)
                is ToolState.Failed -> current.copy(metadata = transition.metadata)
                else -> current
            }
            is ToolTransition.Success -> ToolState.Completed(
                input = current.input,
                output = transition.output,
                metadata = transition.metadata,
                start = (current as? ToolState.Running)?.start
                    ?: (current as? ToolState.Completed)?.start
                    ?: transition.end,
                end = transition.end,
                attachments = transition.attachments,
            )
            is ToolTransition.Failed -> ToolState.Failed(
                input = current.input,
                error = transition.error,
                output = transition.output,
                metadata = transition.metadata,
                start = (current as? ToolState.Running)?.start
                    ?: (current as? ToolState.Failed)?.start
                    ?: transition.end,
                end = transition.end,
            )
        }
        parts[event.partID] = part.copy(state = next)
    }

    private fun replaceMessage(state: DirectoryState, sessionID: String, message: Message) {
        val list = state.messages[sessionID] ?: return
        val index = list.indexOfFirst { it.id == message.id }
        if (index >= 0) list[index] = message
    }

    /**
     * Drops the messages from the boundary on. The engine deletes the
     * boundary message and everything after it in one event, so the app does
     * the same in one step.
     */
    private fun trimMessagesAfter(state: DirectoryState, sessionID: String, boundary: String) {
        val list = state.messages[sessionID] ?: return
        val index = list.indexOfFirst { it.id == boundary }
        if (index < 0) return
        val removed = list.subList(index, list.size)
        removed.forEach { state.parts[sessionID]?.remove(it.id) }
        list.subList(index, list.size).clear()
    }
}
