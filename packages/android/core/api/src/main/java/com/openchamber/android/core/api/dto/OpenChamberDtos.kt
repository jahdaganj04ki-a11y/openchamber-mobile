package com.openchamber.android.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthDto(
    val status: String = "",
    val openchamberVersion: String? = null,
    val runtime: String? = null,
    val compatibility: JsonObject? = null,
    val isOpenCodeReady: Boolean = false,
    val lastOpenCodeError: String? = null,
)

@Serializable
data class EngineHealthDto(
    val healthy: Boolean = false,
    val error: String? = null,
)

@Serializable
data class SystemInfoDto(
    val openchamberVersion: String = "",
    val runtime: String = "web",
    val pid: Int = 0,
    val startedAt: Double = 0.0,
    val port: Int? = null,
    val tunnelUrl: String? = null,
)

@Serializable
data class ClientRecordDto(
    val id: String = "",
    val label: String = "",
    val clientKind: String? = null,
    val dedupeKey: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null,
)

@Serializable
data class CreateClientResponseDto(
    val client: ClientRecordDto = ClientRecordDto(),
    val token: String = "",
)

@Serializable
data class FsEntryDto(
    val name: String = "",
    val path: String = "",
    val isDirectory: Boolean = false,
    val isFile: Boolean = false,
    val isSymbolicLink: Boolean = false,
)

@Serializable
data class FsListDto(
    val path: String = "",
    val entries: List<FsEntryDto> = emptyList(),
)

@Serializable
data class FsReadDto(
    val path: String = "",
    val content: String = "",
    val encoding: String? = null,
    val truncated: Boolean = false,
    val size: Long = 0,
)

@Serializable
data class GitFileChangeDto(
    val path: String = "",
    val status: String = "",
    val staged: Boolean = false,
    val additions: Int = 0,
    val deletions: Int = 0,
    val binary: Boolean = false,
)

@Serializable
data class GitStatusDto(
    val isRepository: Boolean = false,
    val branch: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFileChangeDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class GitDiffDto(
    val diff: String = "",
    val submodule: Boolean = false,
)

@Serializable
data class TerminalSessionDto(
    val id: String = "",
    val title: String? = null,
    val cwd: String = "",
    val shell: String? = null,
    val status: String = "",
    val purpose: JsonObject? = null,
)

@Serializable
data class TerminalListDto(
    val sessions: List<TerminalSessionDto> = emptyList(),
)

@Serializable
data class ApiErrorDto(
    val error: String? = null,
    val reason: String? = null,
    val code: String? = null,
    val data: JsonElement? = null,
)
