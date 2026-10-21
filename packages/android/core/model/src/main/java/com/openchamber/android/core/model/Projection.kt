package com.openchamber.android.core.model

import com.openchamber.android.core.api.dto.AssistantContentDto
import com.openchamber.android.core.api.dto.ContentTimeDto
import com.openchamber.android.core.api.dto.MessageInfoDto
import com.openchamber.android.core.api.dto.PromptAgentAttachmentDto
import com.openchamber.android.core.api.dto.PromptFileAttachmentDto
import com.openchamber.android.core.api.dto.SessionInfoDto
import com.openchamber.android.core.api.dto.StructuredErrorDto
import com.openchamber.android.core.api.dto.ToolContentDto
import com.openchamber.android.core.api.dto.ToolStateDto
import com.openchamber.android.core.api.json.OpenChamberJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The projection from the wire shapes to the domain model.
 *
 * Every part identifier and every tool-state conversion lives here, so a
 * message that was loaded from the server and a message that arrived as a
 * live event end up with the same parts.
 */
object Projection {

    fun projectSession(info: SessionInfoDto): Session = Session(
        id = info.id,
        parentID = info.parentID,
        projectID = info.projectID,
        directory = info.location.directory,
        subpath = info.subpath,
        title = info.title ?: "",
        agent = info.agent,
        model = info.model?.let { ModelRef(it.providerID, it.id, it.variant) },
        cost = info.cost,
        tokens = TokenUsage(
            input = info.tokens.input,
            output = info.tokens.output,
            reasoning = info.tokens.reasoning,
            cache = TokenCache(info.tokens.cache.read, info.tokens.cache.write),
        ),
        outcome = SessionOutcome.fromWire(info.outcome),
        time = SessionTime(
            created = info.time.created,
            updated = info.time.updated,
            idle = info.time.idle,
            viewed = info.time.viewed,
            archived = info.time.archived,
        ),
        metadata = info.metadata,
        permissions = info.permissions,
        revert = info.revert?.let { SessionRevert(it.messageID, it.partID, it.snapshot, it.files) },
        fork = info.fork?.let { fork ->
            SessionFork(
                sessionID = fork.sessionID,
                boundary = SessionForkBoundary(fork.boundary.type, fork.boundary.messageID),
            )
        },
    )

    fun projectAgent(id: String, displayName: String, model: com.openchamber.android.core.api.dto.ModelRefDto?): Agent =
        Agent(id = id, displayName = displayName, model = model?.let { ModelRef(it.providerID, it.id, it.variant) })

    /** The text of a tool result, with the file attachments left out. */
    fun toolOutputText(content: List<ToolContentDto>?): String {
        if (content.isNullOrEmpty()) return ""
        return content.filter { it.type == "text" }.joinToString("\n") { it.text.orEmpty() }
    }

    /** The files a tool produced, or nothing when it produced no files. */
    fun toolAttachments(
        content: List<ToolContentDto>?,
        sessionID: String,
        messageID: String,
        callID: String,
    ): List<FilePart>? {
        if (content == null) return null
        val files = content.filter { it.type == "file" }
        if (files.isEmpty()) return null
        return files.mapIndexed { index, file ->
            FilePart(
                id = "$callID:file:$index",
                sessionID = sessionID,
                messageID = messageID,
                mime = file.mime.orEmpty(),
                filename = file.name,
                url = file.uri.orEmpty(),
            )
        }
    }

    /** A structured error with an empty message falls back to its type. */
    fun structuredErrorText(error: StructuredErrorDto?): String {
        if (error == null) return ""
        return error.message?.takeIf { it.isNotEmpty() } ?: error.type.orEmpty()
    }

    /** A structured error with an empty message falls back to its type. */
    fun structuredErrorText(error: StructuredError?): String {
        if (error == null) return ""
        return error.message?.takeIf { it.isNotEmpty() } ?: error.type.orEmpty()
    }

    fun projectToolPart(tool: AssistantContentDto, sessionID: String, messageID: String): ToolPart {
        val state = decodeToolState(tool.state)
        val start = tool.time?.ran ?: tool.time?.created ?: 0.0
        val end = tool.time?.completed ?: start
        val callID = tool.id.orEmpty()
        val mapped = when (state?.status) {
            // The streaming state carries the argument text as a plain string.
            "streaming" -> ToolState.Pending(input = JsonObject(emptyMap()), raw = state.rawText())
            "running" -> ToolState.Running(
                input = state.inputObject(),
                metadata = state.metadata,
                start = start,
            )
            "completed" -> ToolState.Completed(
                input = state.inputObject(),
                output = toolOutputText(state.content),
                metadata = state.metadata,
                start = start,
                end = end,
                attachments = toolAttachments(state.content, sessionID, messageID, callID),
            )
            "error" -> ToolState.Failed(
                input = state.inputObject(),
                error = structuredErrorText(state.error),
                output = toolOutputText(state.content).takeIf { it.isNotEmpty() },
                metadata = state.metadata,
                start = start,
                end = end,
            )
            else -> ToolState.Pending(input = JsonObject(emptyMap()), raw = state?.rawText().orEmpty())
        }
        return ToolPart(
            id = PartIds.tool(callID),
            sessionID = sessionID,
            messageID = messageID,
            callID = callID,
            tool = tool.name.orEmpty(),
            state = mapped,
            executed = tool.executed,
        )
    }

    /**
     * The parts of an assistant message.
     *
     * The text and the reasoning each count on their own, which is the count
     * the live stream sends with every text and reasoning delta.
     */
    fun projectAssistantContent(
        content: List<AssistantContentDto>,
        sessionID: String,
        messageID: String,
        created: Double,
        completed: Double?,
    ): List<Part> {
        val parts = mutableListOf<Part>()
        var textOrdinal = 0
        var reasoningOrdinal = 0
        content.forEachIndexed { index, item ->
            when (item.type) {
                "text" -> {
                    val followed = index < content.size - 1
                    parts += TextPart(
                        id = PartIds.text(messageID, textOrdinal),
                        sessionID = sessionID,
                        messageID = messageID,
                        text = item.text.orEmpty(),
                        start = created,
                        end = completed ?: if (followed) created else null,
                    )
                    textOrdinal++
                }
                "reasoning" -> {
                    parts += ReasoningPart(
                        id = PartIds.reasoning(messageID, reasoningOrdinal),
                        sessionID = sessionID,
                        messageID = messageID,
                        text = item.text.orEmpty(),
                        start = item.time?.created ?: created,
                        end = item.time?.completed,
                    )
                    reasoningOrdinal++
                }
                "tool" -> parts += projectToolPart(item, sessionID, messageID)
            }
        }
        return parts
    }

    /**
     * The parts of a user message: the text first, then the files in the
     * order they were attached, then the agents.
     */
    fun projectUserParts(
        text: String?,
        files: List<PromptFileAttachmentDto>?,
        agents: List<PromptAgentAttachmentDto>?,
        sessionID: String,
        messageID: String,
        created: Double,
    ): List<Part> {
        val parts = mutableListOf<Part>()
        if (!text.isNullOrEmpty()) {
            parts += TextPart(
                id = PartIds.userText(messageID),
                sessionID = sessionID,
                messageID = messageID,
                text = text,
                start = created,
                end = created,
            )
        }
        files?.forEachIndexed { index, file ->
            parts += FilePart(
                id = PartIds.userFile(messageID, index),
                sessionID = sessionID,
                messageID = messageID,
                mime = file.mime,
                filename = file.name,
                url = fileAttachmentUrl(file),
            )
        }
        agents?.forEachIndexed { index, agent ->
            parts += AgentPart(
                id = PartIds.userAgent(messageID, index),
                sessionID = sessionID,
                messageID = messageID,
                name = agent.name,
            )
        }
        return parts
    }

    /**
     * Where an attached file comes from. A file that came from a URI keeps
     * it; an inline file becomes a data URL the app can render.
     */
    fun fileAttachmentUrl(file: PromptFileAttachmentDto): String {
        val source = file.source
        val uri = source?.uri
        if (source?.type == "uri" && !uri.isNullOrEmpty()) return uri
        return "data:${file.mime};base64,${file.data.orEmpty()}"
    }

    fun projectMessage(info: MessageInfoDto, sessionID: String): ProjectedMessage {
        val time = MessageTime(info.time.created, info.time.streamed, info.time.completed)
        return when (info.type) {
            "user" -> ProjectedMessage(
                message = UserMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    metadata = info.metadata,
                ),
                parts = projectUserParts(
                    text = info.text,
                    files = info.files,
                    agents = info.agents,
                    sessionID = sessionID,
                    messageID = info.id,
                    created = info.time.created,
                ),
            )
            "assistant" -> {
                val model = info.model
                ProjectedMessage(
                    message = AssistantMessage(
                        id = info.id,
                        sessionID = sessionID,
                        time = time,
                        agent = info.agent.orEmpty(),
                        providerID = model?.providerID.orEmpty(),
                        modelID = model?.id.orEmpty(),
                        variant = model?.variant,
                        finish = AssistantFinish.fromWire(info.finish),
                        error = info.error?.toStructuredError(),
                        cost = info.cost,
                        tokens = info.tokens?.let { it.toTokenUsage() },
                        snapshot = info.snapshot?.let {
                            AssistantSnapshot(it.start, it.end, it.files)
                        },
                        retry = info.retry?.let {
                            AssistantRetry(it.attempt, it.at, it.error.toStructuredError())
                        },
                        metadata = info.metadata,
                    ),
                    parts = projectAssistantContent(
                        content = info.content.orEmpty(),
                        sessionID = sessionID,
                        messageID = info.id,
                        created = info.time.created,
                        completed = info.time.completed,
                    ),
                )
            }
            "synthetic" -> ProjectedMessage(
                message = SyntheticMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    text = info.text.orEmpty(),
                    description = info.description,
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "system" -> ProjectedMessage(
                message = SystemMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    text = info.text.orEmpty(),
                    description = info.description,
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "skill" -> ProjectedMessage(
                message = SkillMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    skill = info.skill.orEmpty(),
                    name = info.name.orEmpty(),
                    text = info.text.orEmpty(),
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "shell" -> ProjectedMessage(
                message = ShellMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    shellID = info.shellID.orEmpty(),
                    command = info.command.orEmpty(),
                    status = info.status.orEmpty(),
                    exit = finiteExit(info.exit),
                    signal = info.signal,
                    output = info.output?.let { ShellOutput(it.output, it.cursor, it.size, it.truncated) },
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "compaction" -> {
                val status = info.status.orEmpty()
                ProjectedMessage(
                    message = CompactionMessage(
                        id = info.id,
                        sessionID = sessionID,
                        time = time,
                        status = status,
                        reason = info.reason.orEmpty(),
                        summary = if (status == "failed") "" else info.summary.orEmpty(),
                        error = if (status == "failed") info.error?.toStructuredError() else null,
                        cost = if (status == "running") null else info.cost,
                        tokens = if (status == "running") null else info.tokens?.toTokenUsage(),
                        metadata = info.metadata,
                    ),
                    parts = emptyList(),
                )
            }
            "agent-switched" -> ProjectedMessage(
                message = AgentSwitchedMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    agent = info.agent.orEmpty(),
                    previous = (info.previous as? JsonPrimitive)?.content,
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "model-switched" -> {
                val model = info.model
                ProjectedMessage(
                    message = ModelSwitchedMessage(
                        id = info.id,
                        sessionID = sessionID,
                        time = time,
                        model = model?.let { ModelRef(it.providerID, it.id, it.variant) }
                            ?: ModelRef("", ""),
                        previous = (info.previous as? kotlinx.serialization.json.JsonObject)?.let {
                            val providerID = it["providerID"] as? JsonPrimitive
                            val id = it["id"] as? JsonPrimitive
                            if (providerID != null && id != null) {
                                ModelRef(providerID.content, id.content, (it["variant"] as? JsonPrimitive)?.content)
                            } else {
                                null
                            }
                        },
                        metadata = info.metadata,
                    ),
                    parts = emptyList(),
                )
            }
            "location-switched" -> ProjectedMessage(
                message = LocationSwitchedMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    directory = info.location?.directory.orEmpty(),
                    previous = ((info.previous as? kotlinx.serialization.json.JsonObject)
                        ?.get("directory") as? JsonPrimitive)?.contentOrNull,
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            "idle" -> ProjectedMessage(
                message = IdleMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    outcome = SessionOutcome.fromWire(info.outcome),
                    error = info.error?.toStructuredError(),
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
            else -> ProjectedMessage(
                message = SyntheticMessage(
                    id = info.id,
                    sessionID = sessionID,
                    time = time,
                    text = info.text.orEmpty(),
                    metadata = info.metadata,
                ),
                parts = emptyList(),
            )
        }
    }

    fun projectMessages(items: List<MessageInfoDto>, sessionID: String): List<ProjectedMessage> =
        items.map { projectMessage(it, sessionID) }

    /**
     * The exit code of a shell message. The wire record may carry a
     * non-finite value, which means the process had no exit code at all.
     */
    fun finiteExit(value: kotlinx.serialization.json.JsonElement?): Int? = when (value) {
        null -> null
        is JsonPrimitive -> if (value.isString) null else value.content.toIntOrNull()
        else -> null
    }

    private fun decodeToolState(raw: kotlinx.serialization.json.JsonElement?): ToolStateDto? {
        if (raw == null) return null
        return runCatching {
            OpenChamberJson.decodeFromJsonElement(ToolStateDto.serializer(), raw)
        }.getOrNull()
    }

    /** The argument text of a call that is still streaming its arguments. */
    private fun ToolStateDto.rawText(): String =
        (input as? JsonPrimitive)?.content.orEmpty()

    /** The arguments of a call, as an object. A missing value is an empty one. */
    private fun ToolStateDto.inputObject(): JsonObject =
        input as? JsonObject ?: JsonObject(emptyMap())

    private fun StructuredErrorDto.toStructuredError(): StructuredError =
        StructuredError(type = type, message = message, name = name, data = data)

    private fun com.openchamber.android.core.api.dto.TokenUsageDto.toTokenUsage(): TokenUsage =
        TokenUsage(
            input = input,
            output = output,
            reasoning = reasoning,
            cache = TokenCache(cache.read, cache.write),
        )
}

/** A message and the parts that belong to it. */
data class ProjectedMessage(
    val message: Message,
    val parts: List<Part>,
)
