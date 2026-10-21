package com.openchamber.android.core.api

import com.openchamber.android.core.api.dto.ClientRecordDto
import com.openchamber.android.core.api.dto.CreateClientResponseDto
import com.openchamber.android.core.api.dto.EngineHealthDto
import com.openchamber.android.core.api.dto.FsListDto
import com.openchamber.android.core.api.dto.FsReadDto
import com.openchamber.android.core.api.dto.GitDiffDto
import com.openchamber.android.core.api.dto.GitStatusDto
import com.openchamber.android.core.api.dto.HealthDto
import com.openchamber.android.core.api.dto.SystemInfoDto
import com.openchamber.android.core.api.dto.TerminalListDto
import com.openchamber.android.core.api.json.OpenChamberJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The OpenChamber-owned server routes.
 *
 * These are the routes the desktop shell also uses, so the phone reaches the
 * same file tree, the same git state and the same terminal list as the web
 * client. The engine routes live in [OpenCodeClient].
 */
class OpenChamberClient(private val transport: RuntimeTransport) {

    /**
     * The server's own health. The route is open, so the app can call it
     * before it holds a client token.
     */
    suspend fun health(): HealthDto {
        val body = transport.get("/health")
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun systemInfo(): SystemInfoDto {
        val body = transport.get("/api/system/info")
        return OpenChamberJson.decodeFromString(body)
    }

    /**
     * The engine's own health. The route holds the request until the engine
     * answers or the wait runs out, so a starting client learns of readiness
     * at once instead of polling.
     */
    suspend fun engineHealth(waitMs: Int = 8_000): EngineHealthDto {
        val body = transport.get("/api/opencode/health", mapOf("wait" to waitMs.toString()))
        return OpenChamberJson.decodeFromString(body)
    }

    /**
     * Mints a client token for this device. The token comes back once and is
     * never readable again, so the caller stores it right away.
     */
    suspend fun createClient(
        label: String,
        clientKind: String = CLIENT_KIND,
        dedupeKey: String = CLIENT_KIND,
    ): CreateClientResponseDto {
        val body = buildJsonObject {
            put("label", label)
            put("clientKind", clientKind)
            put("dedupeKey", dedupeKey)
        }
        val response = transport.post("/api/client-auth/clients", body)
        return OpenChamberJson.decodeFromString(response)
    }

    suspend fun listClients(): List<ClientRecordDto> {
        val body = transport.get("/api/client-auth/clients")
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun listDirectory(path: String, respectGitignore: Boolean = true): FsListDto {
        val body = transport.get(
            "/api/fs/list",
            mapOf("path" to path, "respectGitignore" to respectGitignore.toString()),
        )
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun readFile(path: String): FsReadDto {
        val body = transport.get("/api/fs/read", mapOf("path" to path))
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun gitStatus(directory: String): GitStatusDto {
        val body = transport.get("/api/git/status", mapOf("directory" to directory))
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun gitDiff(directory: String, path: String, staged: Boolean = false): GitDiffDto {
        val body = transport.get(
            "/api/git/diff",
            mapOf("directory" to directory, "path" to path, "staged" to staged.toString()),
        )
        return OpenChamberJson.decodeFromString(body)
    }

    suspend fun gitCommit(
        directory: String,
        message: String,
        addAll: Boolean = false,
        files: List<String>? = null,
    ): String {
        val body = buildJsonObject {
            put("message", message)
            put("addAll", addAll)
            if (files != null) {
                put(
                    "files",
                    kotlinx.serialization.json.buildJsonArray { files.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } },
                )
            }
        }
        return transport.post("/api/git/commit", body, mapOf("directory" to directory))
    }

    suspend fun listTerminals(): TerminalListDto {
        val body = transport.get("/api/terminal/sessions")
        return OpenChamberJson.decodeFromString(body)
    }

    /**
     * Tells the server which directory it should work in.
     *
     * The server registers the directory as a project and makes it the active
     * one, the same way the web UI does when the user picks a folder. The
     * answer carries the whole settings object, because the server keeps one
     * list of projects and the phone shows that list.
     */
    suspend fun setDirectory(path: String): String {
        val body = buildJsonObject { put("path", path) }
        return transport.post("/api/openchamber/directory", body)
    }

    companion object {
        /**
         * The client kind the phone registers under. The server treats
         * `clientKind` and `dedupeKey` as free-form strings, so a phone
         * mint replaces its own previous token instead of piling up records.
         */
        const val CLIENT_KIND = "android-local"
    }
}
