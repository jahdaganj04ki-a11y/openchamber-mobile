package com.openchamber.android.core.model

import kotlinx.serialization.json.JsonObject

/**
 * The message roles the transcript shows.
 *
 * A conversation is made of the user and the assistant. The other roles are
 * notices the engine wrote into the transcript: a synthetic note, a system
 * instruction, a skill load, a background shell run, a compaction, a switch of
 * agent, model or location, and the end of a turn.
 */
sealed interface Message {
    val id: String
    val sessionID: String
    val time: MessageTime
    val metadata: JsonObject?
}

sealed interface ConversationMessage : Message

data class UserMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    override val metadata: JsonObject? = null,
) : ConversationMessage

enum class AssistantFinish(val wire: String) {
    Stop("stop"),
    Length("length"),
    ToolCalls("tool-calls"),
    ContentFilter("content-filter"),
    Error("error"),
    Unknown("unknown"),
    ;

    companion object {
        fun fromWire(value: String?): AssistantFinish? = entries.firstOrNull { it.wire == value }
    }
}

data class AssistantSnapshot(
    val start: String? = null,
    val end: String? = null,
    val files: List<String>? = null,
)

data class AssistantRetry(
    val attempt: Int,
    val at: Double,
    val error: StructuredError,
)

data class AssistantMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val agent: String = "",
    val providerID: String = "",
    val modelID: String = "",
    val variant: String? = null,
    val finish: AssistantFinish? = null,
    val error: StructuredError? = null,
    val closedLocally: Boolean = false,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    val snapshot: AssistantSnapshot? = null,
    val retry: AssistantRetry? = null,
    override val metadata: JsonObject? = null,
) : ConversationMessage

data class SyntheticMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val text: String = "",
    val description: String? = null,
    override val metadata: JsonObject? = null,
) : Message

data class SystemMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val text: String = "",
    val description: String? = null,
    override val metadata: JsonObject? = null,
) : Message

data class SkillMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val skill: String = "",
    val name: String = "",
    val text: String = "",
    override val metadata: JsonObject? = null,
) : Message

data class ShellMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val shellID: String = "",
    val command: String = "",
    val status: String = "running",
    val exit: Int? = null,
    val signal: String? = null,
    val output: ShellOutput? = null,
    override val metadata: JsonObject? = null,
) : Message

data class ShellOutput(
    val output: String = "",
    val cursor: Int = 0,
    val size: Int = 0,
    val truncated: Boolean = false,
)

data class CompactionMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val status: String = "running",
    val reason: String = "auto",
    val summary: String = "",
    val error: StructuredError? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    override val metadata: JsonObject? = null,
) : Message

data class AgentSwitchedMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val agent: String = "",
    val previous: String? = null,
    override val metadata: JsonObject? = null,
) : Message

data class ModelSwitchedMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val model: ModelRef,
    val previous: ModelRef? = null,
    override val metadata: JsonObject? = null,
) : Message

data class LocationSwitchedMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val directory: String = "",
    val previous: String? = null,
    override val metadata: JsonObject? = null,
) : Message

data class IdleMessage(
    override val id: String,
    override val sessionID: String,
    override val time: MessageTime = MessageTime(),
    val outcome: SessionOutcome? = null,
    val error: StructuredError? = null,
    override val metadata: JsonObject? = null,
) : Message

data class MessageTime(
    val created: Double = 0.0,
    val streamed: Double? = null,
    val completed: Double? = null,
)

sealed interface Part {
    val id: String
    val sessionID: String
    val messageID: String
}

data class TextPart(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val text: String = "",
    val start: Double? = null,
    val end: Double? = null,
    val metadata: JsonObject? = null,
) : Part

data class ReasoningPart(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val text: String = "",
    val start: Double? = null,
    val end: Double? = null,
) : Part

data class FilePart(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val mime: String = "",
    val filename: String? = null,
    val url: String = "",
) : Part

data class AgentPart(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val name: String = "",
) : Part

data class ToolPart(
    override val id: String,
    override val sessionID: String,
    override val messageID: String,
    val callID: String = "",
    val tool: String = "",
    val state: ToolState,
    val executed: Boolean? = null,
) : Part

/**
 * The state of a tool call.
 *
 * The wire record calls the first state "streaming" and carries the raw
 * argument text; the app calls it pending, because that is what the user
 * sees: the arguments are still arriving.
 */
sealed interface ToolState {
    val input: JsonObject

    data class Pending(
        override val input: JsonObject = JsonObject(emptyMap()),
        val raw: String = "",
    ) : ToolState

    data class Running(
        override val input: JsonObject = JsonObject(emptyMap()),
        val metadata: JsonObject? = null,
        val start: Double = 0.0,
    ) : ToolState

    data class Completed(
        override val input: JsonObject = JsonObject(emptyMap()),
        val output: String = "",
        val metadata: JsonObject? = null,
        val start: Double = 0.0,
        val end: Double = 0.0,
        val attachments: List<FilePart>? = null,
    ) : ToolState

    data class Failed(
        override val input: JsonObject = JsonObject(emptyMap()),
        val error: String = "",
        val output: String? = null,
        val metadata: JsonObject? = null,
        val start: Double = 0.0,
        val end: Double = 0.0,
    ) : ToolState
}

/** The identifiers the parts carry, so a live stream and a loaded page agree. */
object PartIds {
    fun text(messageID: String, ordinal: Int): String = "$messageID:text:$ordinal"
    fun reasoning(messageID: String, ordinal: Int): String = "$messageID:reasoning:$ordinal"
    fun tool(callID: String): String = callID
    fun userText(messageID: String): String = "$messageID:text:0"
    fun userFile(messageID: String, index: Int): String = "$messageID:file:$index"
    fun userAgent(messageID: String, index: Int): String = "$messageID:agent:$index"
}

fun isFinalToolStatus(status: String): Boolean = status == "completed" || status == "error"

fun hasParts(message: Message): Boolean = message is ConversationMessage

fun isConversationRole(message: Message): Boolean = message is ConversationMessage

/** The last user or assistant message, skipping the notices in between. */
fun getLastConversationMessage(messages: List<Message>?): ConversationMessage? =
    messages?.lastOrNull { it is ConversationMessage } as ConversationMessage?

/** An assistant turn that never finished. */
fun isIncompleteAssistantTurn(message: Message?): Boolean =
    message is AssistantMessage && message.time.completed == null

/**
 * The same message with a different time. A patch that only carries a time
 * leaves every other field alone.
 */
fun Message.withTime(time: MessageTime): Message = when (this) {
    is UserMessage -> copy(time = time)
    is AssistantMessage -> copy(time = time)
    is SyntheticMessage -> copy(time = time)
    is SystemMessage -> copy(time = time)
    is SkillMessage -> copy(time = time)
    is ShellMessage -> copy(time = time)
    is CompactionMessage -> copy(time = time)
    is AgentSwitchedMessage -> copy(time = time)
    is ModelSwitchedMessage -> copy(time = time)
    is LocationSwitchedMessage -> copy(time = time)
    is IdleMessage -> copy(time = time)
}
