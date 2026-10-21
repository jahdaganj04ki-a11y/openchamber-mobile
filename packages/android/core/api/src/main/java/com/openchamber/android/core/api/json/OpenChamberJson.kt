package com.openchamber.android.core.api.json

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration every client in this app uses.
 *
 * The server sends fields the app does not model yet, and a newer OpenCode may
 * add more, so unknown keys are dropped instead of failing the read. `null` is
 * treated as an absent value, which is how the TypeScript client reads the
 * same payloads.
 */
val OpenChamberJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
