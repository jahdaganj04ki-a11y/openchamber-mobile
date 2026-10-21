package com.openchamber.android.core.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The wire event envelope OpenCode publishes on `/api/global/event`.
 *
 * The payload under `data` differs per event type, so it stays raw JSON and
 * the translation layer reads the fields it needs. That keeps a new event
 * type from breaking the read of every other one.
 */
@Serializable
data class WireEventDto(
    val id: String,
    val type: String,
    val created: Double = 0.0,
    val location: LocationDto? = null,
    val data: JsonObject = JsonObject(emptyMap()),
)
