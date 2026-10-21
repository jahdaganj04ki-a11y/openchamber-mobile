package com.openchamber.android.core.api

import com.openchamber.android.core.api.dto.AgentInfoDto
import com.openchamber.android.core.api.dto.CommandInfoDto
import com.openchamber.android.core.api.dto.ConfigEntryDto
import com.openchamber.android.core.api.dto.FormInfoDto
import com.openchamber.android.core.api.dto.MessageInfoDto
import com.openchamber.android.core.api.dto.MessageListPageDto
import com.openchamber.android.core.api.dto.McpServerDto
import com.openchamber.android.core.api.dto.ModelInfoDto
import com.openchamber.android.core.api.dto.PageCursorDto
import com.openchamber.android.core.api.dto.PermissionRequestDto
import com.openchamber.android.core.api.dto.ProjectInfoDto
import com.openchamber.android.core.api.dto.ProviderInfoDto
import com.openchamber.android.core.api.dto.SessionInfoDto
import com.openchamber.android.core.api.dto.SessionListPageDto
import com.openchamber.android.core.api.dto.SkillInfoDto
import com.openchamber.android.core.api.json.OpenChamberJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The OpenCode 2.x client.
 *
 * Every call carries the directory it belongs to, because OpenCode keeps one
 * service graph per directory and answers a directory-less read from its own
 * working directory. The wire shapes come back as they went out; the
 * projection in `core:model` turns them into the domain model.
 */
class OpenCodeClient(private val transport: RuntimeTransport) {

    suspend fun listSessions(directory: String? = null): List<SessionInfoDto> {
        val body = transport.get("/api/session", directory = directory)
        return OpenChamberJson.decodeFromString<SessionListPageDto>(body).data
    }

    suspend fun getSession(sessionID: String, directory: String? = null): SessionInfoDto {
        val body = transport.get("/api/session/$sessionID", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun createSession(
        title: String? = null,
        parentID: String? = null,
        agent: String? = null,
        model: ModelRefRequest? = null,
        directory: String? = null,
    ): SessionInfoDto {
        val body = buildJsonObject {
            if (title != null) put("title", title)
            if (parentID != null) put("parentID", parentID)
            if (agent != null) put("agent", agent)
            if (model != null) put("model", model.toJson())
        }
        val response = transport.post("/api/session", body, directory = directory)
        return OpenChamberJson.decodeFromString(response)
    }

    suspend fun deleteSession(sessionID: String, directory: String? = null) {
        transport.delete("/api/session/$sessionID", directory = directory)
    }

    suspend fun listMessages(
        sessionID: String,
        limit: Int? = null,
        cursor: String? = null,
        order: String? = null,
        directory: String? = null,
    ): MessagePage {
        val query = buildMap {
            if (limit != null) put("limit", limit.toString())
            if (cursor != null) put("cursor", cursor)
            if (order != null && cursor == null) put("order", order)
        }
        val body = transport.get("/api/session/$sessionID/message", query, directory)
        val page = OpenChamberJson.decodeFromString<MessageListPageDto>(body)
        val lastPage = limit != null && page.data.size < limit
        return MessagePage(
            items = page.data,
            cursor = PageCursor(page.cursor?.previous, if (lastPage) null else page.cursor?.next),
        )
    }

    suspend fun listPermissions(sessionID: String, directory: String? = null): List<PermissionRequestDto> {
        val body = transport.get("/api/session/$sessionID/permission", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun replyPermission(
        sessionID: String,
        requestID: String,
        decision: String,
        message: String? = null,
        directory: String? = null,
    ) {
        val body = buildJsonObject {
            put("decision", decision)
            if (message != null) put("message", message)
        }
        transport.post("/api/session/$sessionID/permission/$requestID/reply", body, directory = directory)
    }

    suspend fun listForms(sessionID: String, directory: String? = null): List<FormInfoDto> {
        val body = transport.get("/api/session/$sessionID/form", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun replyForm(
        sessionID: String,
        formID: String,
        answer: JsonObject,
        directory: String? = null,
    ) {
        val body = buildJsonObject { put("answer", answer) }
        transport.post("/api/session/$sessionID/form/$formID/reply", body, directory = directory)
    }

    suspend fun prompt(
        sessionID: String,
        text: String,
        files: List<PromptFileRequest> = emptyList(),
        agents: List<PromptAgentRequest> = emptyList(),
        model: ModelRefRequest? = null,
        agent: String? = null,
        directory: String? = null,
    ) {
        val body = buildJsonObject {
            put("text", text)
            if (files.isNotEmpty()) {
                put(
                    "files",
                    kotlinx.serialization.json.buildJsonArray {
                        files.forEach { add(it.toJson()) }
                    },
                )
            }
            if (agents.isNotEmpty()) {
                put(
                    "agents",
                    kotlinx.serialization.json.buildJsonArray {
                        agents.forEach { add(it.toJson()) }
                    },
                )
            }
            if (model != null) put("model", model.toJson())
            if (agent != null) put("agent", agent)
        }
        transport.post("/api/session/$sessionID/prompt", body, directory = directory)
    }

    suspend fun listProjects(directory: String? = null): List<ProjectInfoDto> {
        val body = transport.get("/api/project", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listAgents(directory: String? = null): List<AgentInfoDto> {
        val body = transport.get("/api/agent", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listCommands(directory: String? = null): List<CommandInfoDto> {
        val body = transport.get("/api/command", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listSkills(directory: String? = null): List<SkillInfoDto> {
        val body = transport.get("/api/skill", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listProviders(directory: String? = null): List<ProviderInfoDto> {
        val body = transport.get("/api/provider", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listModels(directory: String? = null): List<ModelInfoDto> {
        val body = transport.get("/api/model", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listMcpServers(directory: String? = null): List<McpServerDto> {
        val body = transport.get("/api/mcp", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listConfig(directory: String? = null): List<ConfigEntryDto> {
        val body = transport.get("/api/config", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listActiveSessions(directory: String? = null): List<SessionInfoDto> {
        val body = transport.get("/api/session/active", directory = directory)
        return OpenChamberJson.decodeFromString(body)
    }
}

data class MessagePage(
    val items: List<MessageInfoDto>,
    val cursor: PageCursor,
)

data class PageCursor(
    val previous: String? = null,
    val next: String? = null,
)

data class ModelRefRequest(
    val providerID: String,
    val id: String,
    val variant: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("providerID", providerID)
        put("id", id)
        if (variant != null) put("variant", variant)
    }
}

data class PromptFileRequest(
    val mime: String,
    val name: String?,
    val data: String?,
    val uri: String?,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("mime", mime)
        if (name != null) put("name", name)
        if (data != null) put("data", data)
        put(
            "source",
            if (uri != null) {
                buildJsonObject {
                    put("type", "uri")
                    put("uri", uri)
                }
            } else {
                buildJsonObject { put("type", "inline") }
            },
        )
    }
}

data class PromptAgentRequest(val name: String) {
    fun toJson(): JsonObject = buildJsonObject { put("name", name) }
}
