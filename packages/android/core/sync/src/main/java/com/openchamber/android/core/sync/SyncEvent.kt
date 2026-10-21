package com.openchamber.android.core.sync

import com.openchamber.android.core.model.AssistantFinish
import com.openchamber.android.core.model.AssistantRetry
import com.openchamber.android.core.model.AssistantSnapshot
import com.openchamber.android.core.model.FilePart
import com.openchamber.android.core.model.Message
import com.openchamber.android.core.model.ModelRef
import com.openchamber.android.core.model.PatchValue
import com.openchamber.android.core.model.Part
import com.openchamber.android.core.model.PermissionRequest
import com.openchamber.android.core.model.FormRequest
import com.openchamber.android.core.model.Session
import com.openchamber.android.core.model.SessionOutcome
import com.openchamber.android.core.model.SessionRevert
import com.openchamber.android.core.model.SessionStatus
import com.openchamber.android.core.model.StructuredError
import com.openchamber.android.core.model.TokenUsage
import kotlinx.serialization.json.JsonObject

/**
 * The events the app reacts to.
 *
 * The wire events are translated into these before anything reads them, so
 * one event type means one thing in the app, no matter which server frame it
 * came from.
 */
sealed interface SyncEvent {
    data object ServerConnected : SyncEvent
    data class InstallationUpdateAvailable(val version: String) : SyncEvent

    data class SessionCreated(val info: Session) : SyncEvent
    data class SessionPatched(val sessionID: String, val patch: SessionPatch) : SyncEvent
    data class SessionDeleted(val sessionID: String) : SyncEvent
    data class SessionForked(val sessionID: String, val parentID: String) : SyncEvent
    data class SessionRevertCommitted(val sessionID: String, val to: String) : SyncEvent
    data class SessionStatusChanged(val sessionID: String, val status: SessionStatus) : SyncEvent
    data class SessionIdle(val sessionID: String, val outcome: SessionOutcome?) : SyncEvent
    data class SessionError(val sessionID: String, val error: StructuredError) : SyncEvent

    data class MessageUpdated(val info: Message) : SyncEvent
    data class MessagePatched(
        val sessionID: String,
        val messageID: String,
        val patch: MessagePatch,
    ) : SyncEvent
    data class MessageRemoved(val sessionID: String, val messageID: String) : SyncEvent
    data class MessagePartUpdated(val sessionID: String, val part: Part) : SyncEvent
    data class MessagePartDelta(
        val sessionID: String,
        val messageID: String,
        val partID: String,
        val field: String,
        val delta: String,
    ) : SyncEvent
    data class MessageToolTransition(
        val sessionID: String,
        val messageID: String,
        val partID: String,
        val transition: ToolTransition,
    ) : SyncEvent
    data class MessagePartsReplaced(
        val sessionID: String,
        val messageID: String,
        val parts: List<Part>,
    ) : SyncEvent
    data class MessageCompactionDelta(val sessionID: String, val delta: String) : SyncEvent

    data class PermissionAsked(val request: PermissionRequest) : SyncEvent
    data class PermissionReplied(val sessionID: String, val requestID: String) : SyncEvent
    data class FormCreated(val form: FormRequest) : SyncEvent
    data class FormSettled(val sessionID: String, val formID: String) : SyncEvent

    data class ShellStarted(val shell: RunningShell) : SyncEvent
    data class ShellEnded(val shellID: String, val end: ShellEnd, val endedAt: Double) : SyncEvent

    data class VcsBranchUpdated(val branch: String?) : SyncEvent
    data class McpStatusChanged(val server: String) : SyncEvent
    data class CatalogUpdated(val kind: CatalogKind) : SyncEvent
    data object LocationShutdown : SyncEvent
}

/** The slice of the catalog a rebuild touched. */
enum class CatalogKind(val wire: String) {
    Config("config"),
    Agent("agent"),
    Command("command"),
    Skill("skill"),
    Plugin("plugin"),
    Provider("provider"),
    Model("model"),
    Credential("credential"),
    Project("project"),
    Websearch("websearch"),
    ;

    companion object {
        fun fromWire(value: String): CatalogKind? = entries.firstOrNull { it.wire == value }
    }
}

data class SessionPatch(
    val title: PatchValue<String> = PatchValue.Unchanged,
    val directory: PatchValue<String> = PatchValue.Unchanged,
    val projectID: PatchValue<String> = PatchValue.Unchanged,
    val subpath: PatchValue<String> = PatchValue.Unchanged,
    val agent: PatchValue<String> = PatchValue.Unchanged,
    val model: PatchValue<ModelRef> = PatchValue.Unchanged,
    val cost: PatchValue<Double> = PatchValue.Unchanged,
    val tokens: PatchValue<TokenUsage> = PatchValue.Unchanged,
    val permissions: PatchValue<List<JsonObject>> = PatchValue.Unchanged,
    val revert: PatchValue<SessionRevert> = PatchValue.Unchanged,
    val outcome: PatchValue<SessionOutcome> = PatchValue.Unchanged,
    val metadata: PatchValue<JsonObject> = PatchValue.Unchanged,
    val time: SessionTimePatch = SessionTimePatch(),
)

data class SessionTimePatch(
    val created: PatchValue<Double> = PatchValue.Unchanged,
    val updated: PatchValue<Double> = PatchValue.Unchanged,
    val idle: PatchValue<Double> = PatchValue.Unchanged,
    val viewed: PatchValue<Double> = PatchValue.Unchanged,
    val archived: PatchValue<Double> = PatchValue.Unchanged,
)

data class MessageTimePatch(
    val created: PatchValue<Double> = PatchValue.Unchanged,
    val streamed: PatchValue<Double> = PatchValue.Unchanged,
    val completed: PatchValue<Double> = PatchValue.Unchanged,
)

data class MessagePatch(
    val time: MessageTimePatch = MessageTimePatch(),
    val finish: PatchValue<AssistantFinish> = PatchValue.Unchanged,
    val error: PatchValue<StructuredError> = PatchValue.Unchanged,
    val cost: PatchValue<Double> = PatchValue.Unchanged,
    val tokens: PatchValue<TokenUsage> = PatchValue.Unchanged,
    val snapshot: PatchValue<AssistantSnapshot> = PatchValue.Unchanged,
    val retry: PatchValue<AssistantRetry> = PatchValue.Unchanged,
    val shell: ShellPatch? = null,
)

data class ShellPatch(
    val status: String,
    val exit: Int? = null,
    val signal: String? = null,
    val output: com.openchamber.android.core.model.ShellOutput? = null,
)

/** A step in the life of a tool call. */
sealed interface ToolTransition {
    data class Input(val raw: String) : ToolTransition
    data class Called(val input: JsonObject, val executed: Boolean, val start: Double) : ToolTransition
    data class Progress(val metadata: JsonObject?) : ToolTransition
    data class Success(
        val output: String,
        val attachments: List<FilePart>?,
        val metadata: JsonObject?,
        val executed: Boolean,
        val end: Double,
    ) : ToolTransition
    data class Failed(
        val error: String,
        val output: String?,
        val metadata: JsonObject?,
        val executed: Boolean,
        val end: Double,
    ) : ToolTransition
}

data class RunningShell(
    val id: String,
    val sessionID: String,
    val command: String,
    val file: String? = null,
    val startedAt: Double = 0.0,
)

sealed interface ShellEnd {
    data class Exited(val status: String, val exit: Int?) : ShellEnd
    data object Removed : ShellEnd
}

/** An event together with the directory it belongs to. */
data class RoutedSyncEvent(
    val directory: String,
    val event: SyncEvent,
)

const val GLOBAL_EVENT_DIRECTORY = "global"
