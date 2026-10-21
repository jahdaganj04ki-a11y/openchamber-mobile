package com.openchamber.android.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class TokenUsageDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val reasoning: Double = 0.0,
    val cache: TokenCacheDto = TokenCacheDto(),
)

@Serializable
data class TokenCacheDto(
    val read: Double = 0.0,
    val write: Double = 0.0,
)

@Serializable
data class ModelRefDto(
    val providerID: String,
    val id: String,
    val variant: String? = null,
)

@Serializable
data class LocationDto(
    val directory: String = "",
    val workspaceID: String? = null,
)

@Serializable
data class SessionTimeDto(
    val created: Double = 0.0,
    val updated: Double = 0.0,
    val idle: Double? = null,
    val viewed: Double? = null,
    val archived: Double? = null,
)

@Serializable
data class SessionRevertDto(
    val messageID: String,
    val partID: String? = null,
    val snapshot: String? = null,
    val files: List<String>? = null,
)

@Serializable
data class SessionForkBoundaryDto(
    val type: String,
    val messageID: String,
)

@Serializable
data class SessionForkDto(
    val sessionID: String,
    val boundary: SessionForkBoundaryDto,
)

@Serializable
data class SessionInfoDto(
    val id: String,
    val parentID: String? = null,
    val fork: SessionForkDto? = null,
    val projectID: String,
    val agent: String? = null,
    val model: ModelRefDto? = null,
    val cost: Double = 0.0,
    val tokens: TokenUsageDto = TokenUsageDto(),
    val outcome: String? = null,
    val time: SessionTimeDto = SessionTimeDto(),
    val title: String? = null,
    val location: LocationDto = LocationDto(),
    val subpath: String? = null,
    val metadata: JsonObject? = null,
    val permissions: List<JsonObject>? = null,
    val revert: SessionRevertDto? = null,
)

@Serializable
data class ProjectTimeDto(
    val created: Double = 0.0,
    val updated: Double = 0.0,
    val active: Double = 0.0,
    val archived: Double? = null,
)

@Serializable
data class ProjectIconDto(
    val url: String? = null,
    val override: String? = null,
    val color: String? = null,
)

@Serializable
data class ProjectCommandsDto(
    val start: String? = null,
)

@Serializable
data class ProjectInfoDto(
    val id: String,
    val canonical: String,
    val vcs: String? = null,
    val name: String? = null,
    val icon: ProjectIconDto? = null,
    val commands: ProjectCommandsDto? = null,
    val time: ProjectTimeDto = ProjectTimeDto(),
    val sandboxes: List<String> = emptyList(),
)

@Serializable
data class AgentInfoDto(
    val id: String,
    val name: String,
    val model: ModelRefDto? = null,
    val request: JsonObject? = null,
    val system: String? = null,
    val description: String? = null,
    val mode: String = "all",
    val hidden: Boolean = false,
    val color: String? = null,
    val steps: Int? = null,
    val permissions: List<JsonObject> = emptyList(),
)

@Serializable
data class CommandInfoDto(
    val name: String,
    val description: String? = null,
)

@Serializable
data class SkillInfoDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val path: String? = null,
    val content: String? = null,
)

@Serializable
data class ProviderInfoDto(
    val id: String,
    val name: String,
    val canonical: String? = null,
    val integrationID: String? = null,
    val activation: JsonObject? = null,
    @SerialName("package") val `package`: String? = null,
)

@Serializable
data class ModelInfoDto(
    val id: String,
    val modelID: String,
    val providerID: String,
    val name: String,
    val family: String? = null,
    val canonical: String? = null,
)

@Serializable
data class McpServerDto(
    val name: String,
    val status: String = "pending",
    val integrationID: String? = null,
)

@Serializable
data class StructuredErrorDto(
    val type: String? = null,
    val message: String? = null,
    val name: String? = null,
    val data: JsonObject? = null,
)

@Serializable
data class PermissionRequestDto(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: List<String> = emptyList(),
    val save: List<String>? = null,
    val metadata: JsonObject? = null,
    val source: JsonObject? = null,
    val message: String? = null,
)

@Serializable
data class FormOptionDto(
    val value: String,
    val label: String,
    val description: String? = null,
)

@Serializable
data class FormFieldDto(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
    val hidden: Boolean? = null,
    @SerialName("when") val `when`: JsonObject? = null,
    val options: List<FormOptionDto>? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val default: JsonElement? = null,
    val format: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val placeholder: String? = null,
    val custom: String? = null,
    val url: String? = null,
)

@Serializable
data class FormInfoDto(
    val id: String,
    val sessionID: String,
    val title: String,
    val metadata: JsonObject? = null,
    val fields: List<FormFieldDto> = emptyList(),
)

@Serializable
data class ConfigEntryDto(
    val type: String,
    val info: JsonObject? = null,
)

@Serializable
data class SessionStatusRetryActionDto(
    val reason: String? = null,
    val provider: String? = null,
    val title: String? = null,
    val message: String? = null,
    val label: String? = null,
    val link: String? = null,
)

@Serializable
data class SessionStatusDto(
    val type: String,
    val attempt: Int? = null,
    val message: String? = null,
    val action: SessionStatusRetryActionDto? = null,
    val next: Double? = null,
)

@Serializable
data class PromptFileSourceDto(
    val type: String,
    val uri: String? = null,
)

@Serializable
data class PromptFileAttachmentDto(
    val mime: String,
    val data: String? = null,
    val source: PromptFileSourceDto? = null,
    val name: String? = null,
    val description: String? = null,
    val mention: String? = null,
)

@Serializable
data class PromptAgentAttachmentDto(
    val name: String,
    val mention: String? = null,
)

@Serializable
data class ToolContentDto(
    val type: String,
    val text: String? = null,
    val mime: String? = null,
    val uri: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolStateDto(
    val status: String = "",
    val input: JsonElement? = null,
    val content: List<ToolContentDto>? = null,
    val metadata: JsonObject? = null,
    val error: StructuredErrorDto? = null,
)

/**
 * The time shape shared by the tool and reasoning variants of the assistant
 * content union. A tool records `ran`, a reasoning block records `created`.
 */
@Serializable
data class ContentTimeDto(
    val created: Double? = null,
    val ran: Double? = null,
    val completed: Double? = null,
)

@Serializable
data class AssistantSnapshotDto(
    val start: String? = null,
    val end: String? = null,
    val files: List<String>? = null,
)

@Serializable
data class AssistantRetryDto(
    val attempt: Int = 0,
    val at: Double = 0.0,
    val error: StructuredErrorDto,
)

@Serializable
data class MessageTimeDto(
    val created: Double = 0.0,
    val streamed: Double? = null,
    val completed: Double? = null,
)

@Serializable
data class ShellOutputDto(
    val output: String = "",
    val cursor: Int = 0,
    val size: Int = 0,
    val truncated: Boolean = false,
)

@Serializable
data class MessageInfoDto(
    val id: String,
    val type: String,
    val metadata: JsonObject? = null,
    val time: MessageTimeDto = MessageTimeDto(),
    // user
    val text: String? = null,
    val files: List<PromptFileAttachmentDto>? = null,
    val agents: List<PromptAgentAttachmentDto>? = null,
    // assistant
    val agent: String? = null,
    val model: ModelRefDto? = null,
    val content: List<AssistantContentDto>? = null,
    val snapshot: AssistantSnapshotDto? = null,
    val finish: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val error: StructuredErrorDto? = null,
    val retry: AssistantRetryDto? = null,
    // synthetic / system / skill
    val description: String? = null,
    val skill: String? = null,
    val name: String? = null,
    // shell
    val shellID: String? = null,
    val command: String? = null,
    val status: String? = null,
    val exit: JsonElement? = null,
    val signal: String? = null,
    val output: ShellOutputDto? = null,
    // compaction
    val reason: String? = null,
    val summary: String? = null,
    // agent-switched / model-switched
    val previous: JsonElement? = null,
    // location-switched
    val location: LocationDto? = null,
    // idle
    val outcome: String? = null,
)

/**
 * The assistant content union. The three shapes are told apart by `type`, and
 * the fields that only one shape carries stay `null` for the others. The tool
 * state is kept as raw JSON because the text variant carries a state of a
 * different shape.
 */
@Serializable
data class AssistantContentDto(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val executed: Boolean? = null,
    val state: JsonElement? = null,
    val time: ContentTimeDto? = null,
)

@Serializable
data class SessionInboxUserPayloadDto(
    val text: String = "",
    val files: List<PromptFileAttachmentDto>? = null,
    val agents: List<PromptAgentAttachmentDto>? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class SessionInboxSyntheticPayloadDto(
    val text: String = "",
    val description: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class SessionInboxItemDto(
    val type: String,
    val payload: JsonObject? = null,
)

@Serializable
data class RunningShellDto(
    val id: String,
    val command: String,
    val file: String? = null,
    val startedAt: Double = 0.0,
    val sessionID: String? = null,
)

@Serializable
data class VcsBranchDto(
    val current: String? = null,
    val default: String? = null,
)

@Serializable
data class VcsInfoDto(
    val provider: String? = null,
    val branch: VcsBranchDto = VcsBranchDto(),
)

@Serializable
data class PromptRequestDto(
    val text: String? = null,
    val files: List<PromptFileAttachmentDto>? = null,
    val agents: List<PromptAgentAttachmentDto>? = null,
    val model: ModelRefDto? = null,
    val agent: String? = null,
    val variant: String? = null,
    val system: String? = null,
    val tools: JsonObject? = null,
    val permission: JsonObject? = null,
    val parts: List<JsonElement>? = null,
)

@Serializable
data class PageCursorDto(
    val previous: String? = null,
    val next: String? = null,
)

@Serializable
data class MessageListPageDto(
    val data: List<MessageInfoDto> = emptyList(),
    val cursor: PageCursorDto? = null,
)

@Serializable
data class SessionListPageDto(
    val data: List<SessionInfoDto> = emptyList(),
    val cursor: PageCursorDto? = null,
)

@Serializable
data class PartListPageDto(
    val data: List<JsonObject> = emptyList(),
    val cursor: PageCursorDto? = null,
)

@Serializable
data class NamedValueDto(
    @SerialName("name") val name: String,
    val value: String,
)
