package com.openchamber.android.core.model

import kotlinx.serialization.json.JsonObject

/**
 * The value a patch carries for one field.
 *
 * A patch says what changed. A field that is not mentioned stays as it is, and
 * a field that is mentioned with no value is cleared. The three cases are
 * separate on purpose: an empty title and a title that was never set are
 * different things.
 */
sealed interface PatchValue<out T> {
    data object Unchanged : PatchValue<Nothing>
    data class Assigned<T>(val value: T) : PatchValue<T>
    data object Cleared : PatchValue<Nothing>

    companion object {
        fun <T> of(value: T?): PatchValue<T> =
            if (value == null) Cleared else Assigned(value)
    }
}

/** Reads the patched value, or the value the field had before. */
fun <T> PatchValue<T>.resolve(current: T?): T? = when (this) {
    PatchValue.Unchanged -> current
    is PatchValue.Assigned -> value
    PatchValue.Cleared -> null
}

/** Whether the patch changes the field at all. */
val PatchValue<*>.isUnchanged: Boolean get() = this is PatchValue.Unchanged

data class ModelRef(
    val providerID: String,
    val id: String,
    val variant: String? = null,
)

/** The model reference the composer sends, with the variant left out when unset. */
fun ModelRef.toRequest(): ModelRef = if (variant.isNullOrBlank()) copy(variant = null) else this

fun isSameModelRef(current: ModelRef, next: ModelRef): Boolean =
    current.providerID == next.providerID &&
        current.id == next.id &&
        (current.variant ?: "default") == (next.variant ?: "default")

data class TokenUsage(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val reasoning: Double = 0.0,
    val cache: TokenCache = TokenCache(),
)

data class TokenCache(
    val read: Double = 0.0,
    val write: Double = 0.0,
)

data class SessionTime(
    val created: Double = 0.0,
    val updated: Double = 0.0,
    val idle: Double? = null,
    val viewed: Double? = null,
    val archived: Double? = null,
)

data class SessionRevert(
    val messageID: String,
    val partID: String? = null,
    val snapshot: String? = null,
    val files: List<String>? = null,
)

data class SessionFork(
    val sessionID: String,
    val boundary: SessionForkBoundary,
)

data class SessionForkBoundary(
    val type: String,
    val messageID: String,
)

enum class SessionOutcome(val wire: String) {
    Succeeded("succeeded"),
    Failed("failed"),
    Interrupted("interrupted"),
    ;

    companion object {
        fun fromWire(value: String?): SessionOutcome? = entries.firstOrNull { it.wire == value }
    }
}

data class Session(
    val id: String,
    val parentID: String? = null,
    val projectID: String,
    val directory: String,
    val subpath: String? = null,
    val title: String = "",
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage(),
    val outcome: SessionOutcome? = null,
    val time: SessionTime = SessionTime(),
    val metadata: JsonObject? = null,
    val permissions: List<JsonObject>? = null,
    val revert: SessionRevert? = null,
    val fork: SessionFork? = null,
)

sealed interface SessionStatus {
    data object Idle : SessionStatus
    data object Busy : SessionStatus
    data class Retry(
        val attempt: Int,
        val message: String,
        val action: RetryAction? = null,
        val next: Double,
    ) : SessionStatus

    data class RetryAction(
        val reason: String? = null,
        val provider: String? = null,
        val title: String? = null,
        val message: String? = null,
        val label: String? = null,
        val link: String? = null,
    )
}

data class Project(
    val id: String,
    val worktree: String,
    val vcs: String? = null,
    val name: String? = null,
    val icon: ProjectIcon? = null,
    val commands: ProjectCommands? = null,
    val time: ProjectTime = ProjectTime(),
    val sandboxes: List<String> = emptyList(),
)

data class ProjectIcon(
    val url: String? = null,
    val override: String? = null,
    val color: String? = null,
)

data class ProjectCommands(val start: String? = null)

data class ProjectTime(
    val created: Double = 0.0,
    val updated: Double = 0.0,
    val active: Double = 0.0,
    val archived: Double? = null,
)

data class Vcs(
    val branch: String? = null,
    val defaultBranch: String? = null,
)

/**
 * The agent as the app uses it. The wire record calls the key `id` and the
 * display name `name`; the app keeps the key in `name` because that is what
 * a prompt sends back.
 */
data class Agent(
    val id: String,
    val displayName: String,
    val model: ModelRef? = null,
    val request: JsonObject? = null,
    val system: String? = null,
    val description: String? = null,
    val mode: String = "all",
    val hidden: Boolean = false,
    val color: String? = null,
    val steps: Int? = null,
    val permissions: List<JsonObject> = emptyList(),
)

data class Provider(
    val id: String,
    val name: String,
    val canonical: String? = null,
    val integrationID: String? = null,
)

data class ModelInfo(
    val id: String,
    val modelID: String,
    val providerID: String,
    val name: String,
    val family: String? = null,
    val canonical: String? = null,
)

data class Command(
    val name: String,
    val description: String? = null,
)

data class Skill(
    val id: String,
    val name: String,
    val description: String? = null,
    val path: String? = null,
    val content: String? = null,
)

data class McpServerStatus(
    val name: String,
    val status: String,
    val integrationID: String? = null,
)

data class StructuredError(
    val type: String? = null,
    val message: String? = null,
    val name: String? = null,
    val data: JsonObject? = null,
)

data class PermissionRequest(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: List<String> = emptyList(),
    val save: List<String>? = null,
    val metadata: JsonObject? = null,
    val source: JsonObject? = null,
    val message: String? = null,
)

enum class PermissionReply(val wire: String) {
    Once("once"),
    Always("always"),
    Reject("reject"),
    ;

    companion object {
        fun fromWire(value: String): PermissionReply? = entries.firstOrNull { it.wire == value }
    }
}

data class FormField(
    val key: String,
    val type: String,
    val title: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val hidden: Boolean = false,
    val `when`: JsonObject? = null,
    val options: List<FormOption> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val default: kotlinx.serialization.json.JsonElement? = null,
    val format: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val placeholder: String? = null,
    val custom: String? = null,
    val url: String? = null,
)

data class FormOption(
    val value: String,
    val label: String,
    val description: String? = null,
)

data class FormRequest(
    val id: String,
    val sessionID: String,
    val title: String,
    val metadata: JsonObject? = null,
    val fields: List<FormField> = emptyList(),
)
