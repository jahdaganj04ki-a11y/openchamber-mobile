package com.openchamber.android.core.sync

import com.openchamber.android.core.api.dto.WireEventDto
import com.openchamber.android.core.model.AssistantFinish
import com.openchamber.android.core.model.AssistantMessage
import com.openchamber.android.core.model.AssistantRetry
import com.openchamber.android.core.model.AssistantSnapshot
import com.openchamber.android.core.model.AgentSwitchedMessage
import com.openchamber.android.core.model.CompactionMessage
import com.openchamber.android.core.model.FilePart
import com.openchamber.android.core.model.IdleMessage
import com.openchamber.android.core.model.LocationSwitchedMessage
import com.openchamber.android.core.model.Message
import com.openchamber.android.core.model.MessageTime
import com.openchamber.android.core.model.ModelRef
import com.openchamber.android.core.model.ModelSwitchedMessage
import com.openchamber.android.core.model.Part
import com.openchamber.android.core.model.PartIds
import com.openchamber.android.core.model.PermissionRequest
import com.openchamber.android.core.model.Project
import com.openchamber.android.core.model.Projection
import com.openchamber.android.core.model.ReasoningPart
import com.openchamber.android.core.model.Session
import com.openchamber.android.core.model.SessionOutcome
import com.openchamber.android.core.model.SessionRevert
import com.openchamber.android.core.model.SessionStatus
import com.openchamber.android.core.model.ShellMessage
import com.openchamber.android.core.model.ShellOutput
import com.openchamber.android.core.model.SkillMessage
import com.openchamber.android.core.model.StructuredError
import com.openchamber.android.core.model.SyntheticMessage
import com.openchamber.android.core.model.SystemMessage
import com.openchamber.android.core.model.TextPart
import com.openchamber.android.core.model.TokenCache
import com.openchamber.android.core.model.TokenUsage
import com.openchamber.android.core.model.ToolPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The translation from the wire events to the app's own events.
 *
 * The translation is pure, so a burst of deltas can be coalesced before the
 * reducer sees it.
 */
object EventTranslator {

    fun translate(event: WireEventDto): List<SyncEvent> {
        val data = event.data
        val created = event.created
        return when (event.type) {
            "server.connected" -> listOf(SyncEvent.ServerConnected)

            "installation.update-available" ->
                listOf(SyncEvent.InstallationUpdateAvailable(data.str("version").orEmpty()))

            // --- sessions ---------------------------------------------------------

            "session.created" -> {
                val info = Session(
                    id = data.str("sessionID").orEmpty(),
                    parentID = data.str("parentID"),
                    projectID = data.str("projectID").orEmpty(),
                    directory = data.obj("location")?.str("directory").orEmpty(),
                    subpath = data.str("subpath"),
                    title = data.str("title").orEmpty(),
                    agent = data.str("agent"),
                    model = data.modelRef("model"),
                    cost = 0.0,
                    tokens = TokenUsage(),
                    time = com.openchamber.android.core.model.SessionTime(created = created, updated = created),
                    metadata = data.obj("metadata"),
                    permissions = data.arr("permissions")?.mapNotNull { it as? JsonObject },
                )
                listOf(SyncEvent.SessionCreated(info))
            }

            "session.deleted" -> listOf(SyncEvent.SessionDeleted(data.str("sessionID").orEmpty()))

            // OpenCode sends no `session.created` for a fork, so the fork record
            // is what the app reads the new session from.
            "session.forked" -> listOf(
                SyncEvent.SessionForked(
                    sessionID = data.str("sessionID").orEmpty(),
                    parentID = data.str("parentID").orEmpty(),
                ),
            )

            "session.renamed" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(title = com.openchamber.android.core.model.PatchValue.Assigned(data.str("title").orEmpty())),
                ),
            )

            "session.metadata.updated" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(
                        metadata = com.openchamber.android.core.model.PatchValue.Assigned(
                            data.obj("metadata") ?: JsonObject(emptyMap()),
                        ),
                    ),
                ),
            )

            "session.moved" -> {
                val sessionID = data.str("sessionID").orEmpty()
                val directory = data.obj("location")?.str("directory").orEmpty()
                listOf(
                    sessionPatch(
                        sessionID,
                        SessionPatch(
                            directory = com.openchamber.android.core.model.PatchValue.Assigned(directory),
                            projectID = com.openchamber.android.core.model.PatchValue.Assigned(
                                data.str("projectID").orEmpty(),
                            ),
                            subpath = com.openchamber.android.core.model.PatchValue.of(data.str("subpath")),
                            time = SessionTimePatch(
                                updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                        ),
                    ),
                    SyncEvent.MessageUpdated(
                        LocationSwitchedMessage(
                            id = messageIdFromEvent(event.id),
                            sessionID = sessionID,
                            time = MessageTime(created = created),
                            directory = directory,
                        ),
                    ),
                )
            }

            "session.agent.selected" -> {
                val sessionID = data.str("sessionID").orEmpty()
                listOf(
                    sessionPatch(
                        sessionID,
                        SessionPatch(
                            agent = com.openchamber.android.core.model.PatchValue.Assigned(data.str("agent").orEmpty()),
                            time = SessionTimePatch(
                                updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                        ),
                    ),
                    SyncEvent.MessageUpdated(
                        AgentSwitchedMessage(
                            id = messageIdFromEvent(event.id),
                            sessionID = sessionID,
                            time = MessageTime(created = created),
                            agent = data.str("agent").orEmpty(),
                            previous = data.str("previous"),
                        ),
                    ),
                )
            }

            "session.model.selected" -> {
                val sessionID = data.str("sessionID").orEmpty()
                val model = data.modelRef("model")
                listOf(
                    sessionPatch(
                        sessionID,
                        SessionPatch(
                            model = com.openchamber.android.core.model.PatchValue.of(model),
                            time = SessionTimePatch(
                                updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                        ),
                    ),
                    SyncEvent.MessageUpdated(
                        ModelSwitchedMessage(
                            id = messageIdFromEvent(event.id),
                            sessionID = sessionID,
                            time = MessageTime(created = created),
                            model = model ?: ModelRef("", ""),
                            previous = data.modelRef("previous"),
                        ),
                    ),
                )
            }

            "session.usage.updated" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(
                        cost = com.openchamber.android.core.model.PatchValue.Assigned(data.num("cost") ?: 0.0),
                        tokens = com.openchamber.android.core.model.PatchValue.Assigned(data.tokenUsage("tokens")),
                        time = SessionTimePatch(
                            updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                        ),
                    ),
                ),
            )

            "session.permissions" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(
                        permissions = com.openchamber.android.core.model.PatchValue.Assigned(
                            data.arr("permissions")?.mapNotNull { it as? JsonObject }.orEmpty(),
                        ),
                    ),
                ),
            )

            "session.viewed" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(
                        time = SessionTimePatch(
                            viewed = com.openchamber.android.core.model.PatchValue.Assigned(created),
                        ),
                    ),
                ),
            )

            "session.revert.staged" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(
                        revert = com.openchamber.android.core.model.PatchValue.Assigned(data.revert("revert")),
                    ),
                ),
            )

            "session.revert.cleared" -> listOf(
                sessionPatch(
                    data.str("sessionID").orEmpty(),
                    SessionPatch(revert = com.openchamber.android.core.model.PatchValue.Cleared),
                ),
            )

            "session.revert.committed" -> {
                val sessionID = data.str("sessionID").orEmpty()
                listOf(
                    SyncEvent.SessionRevertCommitted(sessionID, data.str("to").orEmpty()),
                    sessionPatch(sessionID, SessionPatch(revert = com.openchamber.android.core.model.PatchValue.Cleared)),
                )
            }

            // --- execution -------------------------------------------------------

            "session.status" -> listOf(
                SyncEvent.SessionStatusChanged(
                    data.str("sessionID").orEmpty(),
                    data.sessionStatus("status"),
                ),
            )

            "session.idle" -> listOf(SyncEvent.SessionIdle(data.str("sessionID").orEmpty(), null))

            "session.execution.started" -> listOf(
                SyncEvent.SessionStatusChanged(data.str("sessionID").orEmpty(), SessionStatus.Busy),
            )

            "session.execution.succeeded" -> {
                val sessionID = data.str("sessionID").orEmpty()
                listOf(
                    sessionPatch(
                        sessionID,
                        SessionPatch(
                            outcome = com.openchamber.android.core.model.PatchValue.Assigned(SessionOutcome.Succeeded),
                            time = SessionTimePatch(
                                idle = com.openchamber.android.core.model.PatchValue.Assigned(created),
                                updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                        ),
                    ),
                    SyncEvent.SessionIdle(sessionID, SessionOutcome.Succeeded),
                )
            }

            "session.execution.failed" -> {
                val sessionID = data.str("sessionID").orEmpty()
                listOf(
                    sessionPatch(
                        sessionID,
                        SessionPatch(
                            outcome = com.openchamber.android.core.model.PatchValue.Assigned(SessionOutcome.Failed),
                            time = SessionTimePatch(
                                idle = com.openchamber.android.core.model.PatchValue.Assigned(created),
                                updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                        ),
                    ),
                    SyncEvent.SessionError(sessionID, data.structuredError("error")),
                )
            }

            "session.execution.interrupted" -> {
                val sessionID = data.str("sessionID").orEmpty()
                // A shutdown interruption is the engine going away mid-turn. It
                // keeps its claim on the session and records no outcome.
                if (data.str("reason") == "shutdown") {
                    emptyList()
                } else {
                    listOf(
                        sessionPatch(
                            sessionID,
                            SessionPatch(
                                outcome = com.openchamber.android.core.model.PatchValue.Assigned(
                                    SessionOutcome.Interrupted,
                                ),
                                time = SessionTimePatch(
                                    idle = com.openchamber.android.core.model.PatchValue.Assigned(created),
                                    updated = com.openchamber.android.core.model.PatchValue.Assigned(created),
                                ),
                            ),
                        ),
                        SyncEvent.SessionIdle(sessionID, SessionOutcome.Interrupted),
                    )
                }
            }

            // --- user input ------------------------------------------------------

            "session.inbox.enqueued" -> {
                val sessionID = data.str("sessionID").orEmpty()
                val inboxID = data.str("inboxID").orEmpty()
                val projected = data.inboxMessage(sessionID, inboxID, created)
                if (projected == null) {
                    emptyList()
                } else {
                    buildList {
                        add(SyncEvent.MessageUpdated(projected.message))
                        if (projected.parts != null) {
                            add(SyncEvent.MessagePartsReplaced(sessionID, inboxID, projected.parts))
                        }
                    }
                }
            }

            "session.inbox.delivered" -> listOf(
                SyncEvent.MessagePatched(
                    data.str("sessionID").orEmpty(),
                    data.str("inboxID").orEmpty(),
                    MessagePatch(time = MessageTimePatch(created = com.openchamber.android.core.model.PatchValue.Assigned(created))),
                ),
            )

            "session.inbox.cancelled" -> listOf(
                SyncEvent.MessageRemoved(
                    data.str("sessionID").orEmpty(),
                    data.str("inboxID").orEmpty(),
                ),
            )

            "session.synthetic" -> listOf(
                SyncEvent.MessageUpdated(
                    SyntheticMessage(
                        id = messageIdFromEvent(event.id),
                        sessionID = data.str("sessionID").orEmpty(),
                        time = MessageTime(created = created),
                        text = data.str("text").orEmpty(),
                        description = data.str("description"),
                        metadata = data.obj("metadata"),
                    ),
                ),
            )

            "session.skill.activated" -> listOf(
                SyncEvent.MessageUpdated(
                    SkillMessage(
                        id = messageIdFromEvent(event.id),
                        sessionID = data.str("sessionID").orEmpty(),
                        time = MessageTime(created = created),
                        skill = data.str("id").orEmpty(),
                        name = data.str("name").orEmpty(),
                        text = data.str("text").orEmpty(),
                    ),
                ),
            )

            "session.instructions.updated" -> {
                val text = data.strOrNull("text") ?: return emptyList()
                val deltaKeys = data.obj("delta")?.keys?.toList().orEmpty()
                listOf(
                    SyncEvent.MessageUpdated(
                        SystemMessage(
                            id = messageIdFromEvent(event.id),
                            sessionID = data.str("sessionID").orEmpty(),
                            time = MessageTime(created = created),
                            text = text,
                            description = "Instructions updated: ${deltaKeys.joinToString(", ")}",
                            metadata = JsonObject(
                                mapOf(
                                    "notice" to JsonPrimitive("instructions"),
                                    "instructionSources" to JsonArray(
                                        deltaKeys.map { JsonPrimitive(it) },
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }

            // --- assistant steps -------------------------------------------------

            "session.step.started" -> {
                val model = data.modelRef("model")
                listOf(
                    SyncEvent.MessageUpdated(
                        AssistantMessage(
                            id = data.str("assistantMessageID").orEmpty(),
                            sessionID = data.str("sessionID").orEmpty(),
                            time = MessageTime(created = created),
                            agent = data.str("agent").orEmpty(),
                            providerID = model?.providerID.orEmpty(),
                            modelID = model?.id.orEmpty(),
                            variant = model?.variant,
                            snapshot = data.str("snapshot")?.let { AssistantSnapshot(start = it) },
                        ),
                    ),
                )
            }

            "session.step.streamed" -> listOf(
                SyncEvent.MessagePatched(
                    data.str("sessionID").orEmpty(),
                    data.str("assistantMessageID").orEmpty(),
                    MessagePatch(
                        time = MessageTimePatch(
                            streamed = com.openchamber.android.core.model.PatchValue.Assigned(created),
                        ),
                    ),
                ),
            )

            "session.step.ended" -> {
                val snapshot = data.str("snapshot")
                listOf(
                    SyncEvent.MessagePatched(
                        data.str("sessionID").orEmpty(),
                        data.str("assistantMessageID").orEmpty(),
                        MessagePatch(
                            time = MessageTimePatch(
                                completed = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                            finish = com.openchamber.android.core.model.PatchValue.of(
                                AssistantFinish.fromWire(data.str("finish")),
                            ),
                            cost = com.openchamber.android.core.model.PatchValue.of(data.num("cost")),
                            tokens = com.openchamber.android.core.model.PatchValue.of(data.tokenUsage("tokens")),
                            snapshot = com.openchamber.android.core.model.PatchValue.of(
                                if (snapshot != null) {
                                    AssistantSnapshot(end = snapshot, files = data.arr("files")?.mapNotNull { it.strValue() })
                                } else {
                                    null
                                },
                            ),
                            retry = com.openchamber.android.core.model.PatchValue.Cleared,
                        ),
                    ),
                )
            }

            "session.step.failed" -> {
                val finish = data.str("finish") ?: "error"
                listOf(
                    SyncEvent.MessagePatched(
                        data.str("sessionID").orEmpty(),
                        data.str("assistantMessageID").orEmpty(),
                        MessagePatch(
                            time = MessageTimePatch(
                                completed = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                            finish = com.openchamber.android.core.model.PatchValue.Assigned(
                                AssistantFinish.fromWire(finish) ?: AssistantFinish.Error,
                            ),
                            error = com.openchamber.android.core.model.PatchValue.Assigned(
                                data.structuredError("error"),
                            ),
                            cost = com.openchamber.android.core.model.PatchValue.of(data.num("cost")),
                            tokens = com.openchamber.android.core.model.PatchValue.of(data.tokenUsage("tokens")),
                            retry = com.openchamber.android.core.model.PatchValue.Cleared,
                        ),
                    ),
                )
            }

            "session.retry.scheduled" -> listOf(
                SyncEvent.MessagePatched(
                    data.str("sessionID").orEmpty(),
                    data.str("assistantMessageID").orEmpty(),
                    MessagePatch(
                        retry = com.openchamber.android.core.model.PatchValue.Assigned(
                            AssistantRetry(
                                attempt = data.num("attempt")?.toInt() ?: 0,
                                at = data.num("at") ?: 0.0,
                                error = data.structuredError("error"),
                            ),
                        ),
                    ),
                ),
            )

            // --- text and reasoning ----------------------------------------------

            "session.text.started" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartUpdated(
                        data.str("sessionID").orEmpty(),
                        TextPart(
                            id = PartIds.text(messageID, data.num("ordinal")?.toInt() ?: 0),
                            sessionID = data.str("sessionID").orEmpty(),
                            messageID = messageID,
                            text = "",
                            start = created,
                        ),
                    ),
                )
            }

            "session.text.delta" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartDelta(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = messageID,
                        partID = PartIds.text(messageID, data.num("ordinal")?.toInt() ?: 0),
                        field = "text",
                        delta = data.str("delta").orEmpty(),
                    ),
                )
            }

            "session.text.ended" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartUpdated(
                        data.str("sessionID").orEmpty(),
                        TextPart(
                            id = PartIds.text(messageID, data.num("ordinal")?.toInt() ?: 0),
                            sessionID = data.str("sessionID").orEmpty(),
                            messageID = messageID,
                            text = data.str("text").orEmpty(),
                            start = created,
                            end = created,
                        ),
                    ),
                )
            }

            "session.reasoning.started" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartUpdated(
                        data.str("sessionID").orEmpty(),
                        ReasoningPart(
                            id = PartIds.reasoning(messageID, data.num("ordinal")?.toInt() ?: 0),
                            sessionID = data.str("sessionID").orEmpty(),
                            messageID = messageID,
                            text = "",
                            start = created,
                        ),
                    ),
                )
            }

            "session.reasoning.delta" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartDelta(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = messageID,
                        partID = PartIds.reasoning(messageID, data.num("ordinal")?.toInt() ?: 0),
                        field = "text",
                        delta = data.str("delta").orEmpty(),
                    ),
                )
            }

            "session.reasoning.ended" -> {
                val messageID = data.str("assistantMessageID").orEmpty()
                listOf(
                    SyncEvent.MessagePartUpdated(
                        data.str("sessionID").orEmpty(),
                        ReasoningPart(
                            id = PartIds.reasoning(messageID, data.num("ordinal")?.toInt() ?: 0),
                            sessionID = data.str("sessionID").orEmpty(),
                            messageID = messageID,
                            text = data.str("text").orEmpty(),
                            start = created,
                            end = created,
                        ),
                    ),
                )
            }

            // --- tool calls ------------------------------------------------------

            "session.tool.input.started" -> {
                val callID = data.str("id").orEmpty()
                listOf(
                    SyncEvent.MessagePartUpdated(
                        data.str("sessionID").orEmpty(),
                        ToolPart(
                            id = PartIds.tool(callID),
                            sessionID = data.str("sessionID").orEmpty(),
                            messageID = data.str("assistantMessageID").orEmpty(),
                            callID = callID,
                            tool = data.str("name").orEmpty(),
                            state = com.openchamber.android.core.model.ToolState.Pending(),
                        ),
                    ),
                )
            }

            "session.tool.input.delta" -> {
                val callID = data.str("id").orEmpty()
                listOf(
                    SyncEvent.MessagePartDelta(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = data.str("assistantMessageID").orEmpty(),
                        partID = PartIds.tool(callID),
                        field = "raw",
                        delta = data.str("delta").orEmpty(),
                    ),
                )
            }

            "session.tool.input.ended" -> {
                val callID = data.str("id").orEmpty()
                listOf(
                    SyncEvent.MessageToolTransition(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = data.str("assistantMessageID").orEmpty(),
                        partID = PartIds.tool(callID),
                        transition = ToolTransition.Input(data.str("text").orEmpty()),
                    ),
                )
            }

            "session.tool.called" -> {
                val callID = data.str("id").orEmpty()
                listOf(
                    SyncEvent.MessageToolTransition(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = data.str("assistantMessageID").orEmpty(),
                        partID = PartIds.tool(callID),
                        transition = ToolTransition.Called(
                            input = data.obj("input") ?: JsonObject(emptyMap()),
                            executed = data.bool("executed") ?: false,
                            start = created,
                        ),
                    ),
                )
            }

            "session.tool.progress" -> {
                val callID = data.str("id").orEmpty()
                listOf(
                    SyncEvent.MessageToolTransition(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = data.str("assistantMessageID").orEmpty(),
                        partID = PartIds.tool(callID),
                        transition = ToolTransition.Progress(data.obj("metadata")),
                    ),
                )
            }

            "session.tool.success" -> {
                val callID = data.str("id").orEmpty()
                val sessionID = data.str("sessionID").orEmpty()
                val messageID = data.str("assistantMessageID").orEmpty()
                val content = data.toolContent("content")
                listOf(
                    SyncEvent.MessageToolTransition(
                        sessionID = sessionID,
                        messageID = messageID,
                        partID = PartIds.tool(callID),
                        transition = ToolTransition.Success(
                            output = Projection.toolOutputText(content),
                            attachments = Projection.toolAttachments(content, sessionID, messageID, callID),
                            metadata = data.obj("metadata"),
                            executed = data.bool("executed") ?: false,
                            end = created,
                        ),
                    ),
                )
            }

            "session.tool.failed" -> {
                val callID = data.str("id").orEmpty()
                val sessionID = data.str("sessionID").orEmpty()
                val messageID = data.str("assistantMessageID").orEmpty()
                val content = data.toolContent("content")
                listOf(
                    SyncEvent.MessageToolTransition(
                        sessionID = sessionID,
                        messageID = messageID,
                        partID = PartIds.tool(callID),
                        transition = ToolTransition.Failed(
                            error = Projection.structuredErrorText(data.structuredError("error")),
                            output = Projection.toolOutputText(content).takeIf { it.isNotEmpty() },
                            metadata = data.obj("metadata"),
                            executed = data.bool("executed") ?: false,
                            end = created,
                        ),
                    ),
                )
            }

            // --- shell and compaction --------------------------------------------

            "session.shell.started" -> {
                val shell = data.obj("shell")
                listOf(
                    SyncEvent.MessageUpdated(
                        ShellMessage(
                            id = messageIdFromEvent(event.id),
                            sessionID = data.str("sessionID").orEmpty(),
                            time = MessageTime(created = created),
                            shellID = shell?.str("id").orEmpty(),
                            command = shell?.str("command").orEmpty(),
                            status = shell?.str("status").orEmpty(),
                            exit = Projection.finiteExit(shell?.get("exit")),
                        ),
                    ),
                )
            }

            "session.shell.ended" -> {
                val shell = data.obj("shell")
                listOf(
                    SyncEvent.MessagePatched(
                        sessionID = data.str("sessionID").orEmpty(),
                        messageID = "shell:${shell?.str("id").orEmpty()}",
                        patch = MessagePatch(
                            time = MessageTimePatch(
                                completed = com.openchamber.android.core.model.PatchValue.Assigned(created),
                            ),
                            shell = ShellPatch(
                                status = shell?.str("status").orEmpty(),
                                exit = Projection.finiteExit(shell?.get("exit")),
                                signal = shell?.str("signal"),
                                output = data.obj("output")?.let {
                                    ShellOutput(
                                        output = it.str("output").orEmpty(),
                                        cursor = it.num("cursor")?.toInt() ?: 0,
                                        size = it.num("size")?.toInt() ?: 0,
                                        truncated = it.bool("truncated") ?: false,
                                    )
                                },
                            ),
                        ),
                    ),
                )
            }

            "session.compaction.started" -> listOf(
                SyncEvent.MessageUpdated(
                    CompactionMessage(
                        id = data.str("inputID") ?: messageIdFromEvent(event.id),
                        sessionID = data.str("sessionID").orEmpty(),
                        time = MessageTime(created = created),
                        status = "running",
                        reason = data.str("reason").orEmpty(),
                        summary = "",
                    ),
                ),
            )

            "session.compaction.delta" -> listOf(
                SyncEvent.MessageCompactionDelta(
                    data.str("sessionID").orEmpty(),
                    data.str("text").orEmpty(),
                ),
            )

            "session.compaction.ended" -> listOf(
                SyncEvent.MessageUpdated(
                    CompactionMessage(
                        id = messageIdFromEvent(event.id),
                        sessionID = data.str("sessionID").orEmpty(),
                        time = MessageTime(created = created),
                        status = "completed",
                        reason = data.str("reason").orEmpty(),
                        summary = data.str("text").orEmpty(),
                        cost = data.num("cost"),
                        tokens = data.tokenUsage("tokens"),
                    ),
                ),
            )

            "session.compaction.failed" -> listOf(
                SyncEvent.MessageUpdated(
                    CompactionMessage(
                        id = data.str("inputID") ?: messageIdFromEvent(event.id),
                        sessionID = data.str("sessionID").orEmpty(),
                        time = MessageTime(created = created),
                        status = "failed",
                        reason = data.str("reason").orEmpty(),
                        summary = "",
                        error = data.structuredError("error"),
                        cost = data.num("cost"),
                        tokens = data.tokenUsage("tokens"),
                    ),
                ),
            )

            // --- requests to the user --------------------------------------------

            "permission.asked" -> listOf(
                SyncEvent.PermissionAsked(
                    PermissionRequest(
                        id = data.str("id").orEmpty(),
                        sessionID = data.str("sessionID").orEmpty(),
                        action = data.str("action").orEmpty(),
                        resources = data.arr("resources")?.mapNotNull { it.strValue() }.orEmpty(),
                        save = data.arr("save")?.mapNotNull { it.strValue() },
                        metadata = data.obj("metadata"),
                        source = data.obj("source"),
                        message = data.str("message"),
                    ),
                ),
            )

            "permission.replied" -> listOf(
                SyncEvent.PermissionReplied(
                    data.str("sessionID").orEmpty(),
                    data.str("requestID").orEmpty(),
                ),
            )

            "form.created" -> {
                val form = data.obj("form") ?: JsonObject(emptyMap())
                listOf(
                    SyncEvent.FormCreated(
                        com.openchamber.android.core.model.FormRequest(
                            id = form.str("id").orEmpty(),
                            sessionID = form.str("sessionID").orEmpty(),
                            title = form.str("title").orEmpty(),
                            metadata = form.obj("metadata"),
                            fields = form.arr("fields")?.mapNotNull { it as? JsonObject }.orEmpty().map { field ->
                                com.openchamber.android.core.model.FormField(
                                    key = field.str("key").orEmpty(),
                                    type = field.str("type").orEmpty(),
                                    title = field.str("title"),
                                    description = field.str("description"),
                                    required = field.bool("required") ?: false,
                                    hidden = field.bool("hidden") ?: false,
                                    `when` = field.obj("when"),
                                    options = field.arr("options")?.mapNotNull { it as? JsonObject }.orEmpty().map {
                                        com.openchamber.android.core.model.FormOption(
                                            value = it.str("value").orEmpty(),
                                            label = it.str("label").orEmpty(),
                                            description = it.str("description"),
                                        )
                                    },
                                    minimum = field.num("minimum"),
                                    maximum = field.num("maximum"),
                                    default = field["default"],
                                    format = field.str("format"),
                                    minLength = field.num("minLength")?.toInt(),
                                    maxLength = field.num("maxLength")?.toInt(),
                                    pattern = field.str("pattern"),
                                    placeholder = field.str("placeholder"),
                                    custom = field.str("custom"),
                                    url = field.str("url"),
                                )
                            },
                        ),
                    ),
                )
            }

            "form.replied", "form.cancelled" -> listOf(
                SyncEvent.FormSettled(
                    data.str("sessionID").orEmpty(),
                    data.str("id").orEmpty(),
                ),
            )

            // --- catalogs and location -------------------------------------------

            "vcs.branch.updated" -> listOf(SyncEvent.VcsBranchUpdated(data.str("branch")))

            "mcp.status.changed" -> listOf(SyncEvent.McpStatusChanged(data.str("server").orEmpty()))

            "config.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Config))
            "agent.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Agent))
            "command.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Command))
            "skill.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Skill))
            "plugin.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Plugin))
            "credential.updated", "credential.switched" ->
                listOf(SyncEvent.CatalogUpdated(CatalogKind.Credential))
            "project.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Project))
            "provider.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Provider))
            "model.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Model))
            "websearch.updated" -> listOf(SyncEvent.CatalogUpdated(CatalogKind.Websearch))

            "location.shutdown" -> listOf(SyncEvent.LocationShutdown)

            // --- background shells -------------------------------------------------

            "shell.created" -> {
                val info = data.obj("info")
                val sessionID = info?.obj("metadata")?.str("sessionID")
                if (info?.str("status") != "running" || sessionID == null) {
                    emptyList()
                } else {
                    listOf(
                        SyncEvent.ShellStarted(
                            RunningShell(
                                id = info.str("id").orEmpty(),
                                sessionID = sessionID,
                                command = info.str("command").orEmpty(),
                                file = info.str("file"),
                                startedAt = info.obj("time")?.num("started") ?: 0.0,
                            ),
                        ),
                    )
                }
            }

            "shell.exited" -> {
                val status = data.str("status").orEmpty()
                if (status == "running") {
                    emptyList()
                } else {
                    listOf(
                        SyncEvent.ShellEnded(
                            shellID = data.str("id").orEmpty(),
                            end = ShellEnd.Exited(status, Projection.finiteExit(data["exit"])),
                            endedAt = created,
                        ),
                    )
                }
            }

            "shell.deleted" -> listOf(
                SyncEvent.ShellEnded(
                    shellID = data.str("id").orEmpty(),
                    end = ShellEnd.Removed,
                    endedAt = created,
                ),
            )

            // PTY frames, TUI frames and plugin RPC frames carry nothing the
            // app shows.
            else -> emptyList()
        }
    }

    fun route(event: WireEventDto): List<RoutedSyncEvent> =
        translate(event).map { RoutedSyncEvent(event.location?.directory ?: GLOBAL_EVENT_DIRECTORY, it) }

    /**
     * The message id an event implies. The server and the messages it creates
     * share one id space, so the prefix is swapped.
     */
    fun messageIdFromEvent(eventID: String): String =
        if (eventID.startsWith("evt_")) "msg_" + eventID.removePrefix("evt_") else eventID

    private fun sessionPatch(sessionID: String, patch: SessionPatch): SyncEvent.SessionPatched =
        SyncEvent.SessionPatched(sessionID, patch)
}

// --- reading the wire payload ------------------------------------------------

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

/** The raw string value, empty strings included. */
private fun JsonObject.strOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.num(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.let {
        when (it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** The string a JSON value holds, or nothing when it holds something else. */
private fun kotlinx.serialization.json.JsonElement.strValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonObject.modelRef(key: String): ModelRef? {
    val value = obj(key) ?: return null
    val providerID = value.str("providerID") ?: return null
    val id = value.str("id") ?: return null
    return ModelRef(providerID, id, value.str("variant"))
}

private fun JsonObject.tokenUsage(key: String): TokenUsage {
    val value = obj(key) ?: return TokenUsage()
    val cache = value.obj("cache")
    return TokenUsage(
        input = value.num("input") ?: 0.0,
        output = value.num("output") ?: 0.0,
        reasoning = value.num("reasoning") ?: 0.0,
        cache = TokenCache(
            read = cache?.num("read") ?: 0.0,
            write = cache?.num("write") ?: 0.0,
        ),
    )
}

private fun JsonObject.sessionStatus(key: String): SessionStatus {
    val value = obj(key) ?: return SessionStatus.Idle
    return when (value.str("type")) {
        "busy" -> SessionStatus.Busy
        "retry" -> SessionStatus.Retry(
            attempt = value.num("attempt")?.toInt() ?: 0,
            message = value.str("message").orEmpty(),
            action = value.obj("action")?.let {
                SessionStatus.RetryAction(
                    reason = it.str("reason"),
                    provider = it.str("provider"),
                    title = it.str("title"),
                    message = it.str("message"),
                    label = it.str("label"),
                    link = it.str("link"),
                )
            },
            next = value.num("next") ?: 0.0,
        )
        else -> SessionStatus.Idle
    }
}

private fun JsonObject.structuredError(key: String): StructuredError {
    val value = obj(key) ?: return StructuredError()
    return StructuredError(
        type = value.str("type"),
        message = value.str("message"),
        name = value.str("name"),
        data = value.obj("data"),
    )
}

private fun JsonObject.revert(key: String): SessionRevert {
    val value = obj(key) ?: return SessionRevert("")
    return SessionRevert(
        messageID = value.str("messageID").orEmpty(),
        partID = value.str("partID"),
        snapshot = value.str("snapshot"),
        files = value.arr("files")?.mapNotNull { it.strValue() },
    )
}

private fun JsonObject.toolContent(key: String): List<com.openchamber.android.core.api.dto.ToolContentDto>? =
    arr(key)?.mapNotNull { element ->
        val value = element as? JsonObject ?: return@mapNotNull null
        com.openchamber.android.core.api.dto.ToolContentDto(
            type = value.str("type").orEmpty(),
            text = value.str("text"),
            mime = value.str("mime"),
            uri = value.str("uri"),
            name = value.str("name"),
        )
    }

private fun JsonObject.inboxMessage(
    sessionID: String,
    messageID: String,
    created: Double,
): InboxProjection? {
    val item = obj("item") ?: return null
    return when (item.str("type")) {
        "user" -> {
            val payload = item.obj("payload")
            InboxProjection(
                message = com.openchamber.android.core.model.UserMessage(
                    id = messageID,
                    sessionID = sessionID,
                    time = MessageTime(created = created),
                    metadata = payload?.obj("metadata"),
                ),
                parts = Projection.projectUserParts(
                    text = payload?.str("text"),
                    files = payload?.arr("files")?.mapNotNull { it as? JsonObject }?.map { it.promptFile() },
                    agents = payload?.arr("agents")?.mapNotNull { it as? JsonObject }?.map { it.promptAgent() },
                    sessionID = sessionID,
                    messageID = messageID,
                    created = created,
                ),
            )
        }
        "synthetic" -> {
            val payload = item.obj("payload")
            InboxProjection(
                message = SyntheticMessage(
                    id = messageID,
                    sessionID = sessionID,
                    time = MessageTime(created = created),
                    text = payload?.str("text").orEmpty(),
                    description = payload?.str("description"),
                    metadata = payload?.obj("metadata"),
                ),
                parts = null,
            )
        }
        else -> null
    }
}

private fun JsonObject.promptFile(): com.openchamber.android.core.api.dto.PromptFileAttachmentDto {
    val source = obj("source")
    return com.openchamber.android.core.api.dto.PromptFileAttachmentDto(
        mime = str("mime").orEmpty(),
        data = strOrNull("data"),
        source = source?.let {
            com.openchamber.android.core.api.dto.PromptFileSourceDto(
                type = it.str("type").orEmpty(),
                uri = it.str("uri"),
            )
        },
        name = str("name"),
        description = str("description"),
        mention = str("mention"),
    )
}

private fun JsonObject.promptAgent(): com.openchamber.android.core.api.dto.PromptAgentAttachmentDto =
    com.openchamber.android.core.api.dto.PromptAgentAttachmentDto(
        name = str("name").orEmpty(),
        mention = str("mention"),
    )

data class InboxProjection(
    val message: Message,
    val parts: List<Part>?,
)
