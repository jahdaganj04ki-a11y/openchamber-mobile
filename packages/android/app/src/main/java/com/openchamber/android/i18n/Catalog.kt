package com.openchamber.android.i18n

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The message catalog of one locale.
 *
 * The entries come from the shared TypeScript catalogs, so the phone and the
 * web client say the same thing in the same language.
 */
class Catalog(private val entries: Map<String, String>) {

    /**
     * The text of a key.
     *
     * A key that is not in this catalog falls back to the English catalog,
     * and a key that is nowhere falls back to itself, so a missing string
     * shows up on screen instead of an empty space.
     */
    fun t(key: String, params: Map<String, String> = emptyMap()): String {
        val template = entries[key] ?: fallback?.t(key, params) ?: key
        return interpolate(template, params)
    }

    /** The English catalog, used for the keys this locale has not caught up with. */
    var fallback: Catalog? = null

    private fun interpolate(template: String, params: Map<String, String>): String {
        if (params.isEmpty()) return template
        var result = template
        params.forEach { (name, value) ->
            result = result.replace("{$name}", value)
        }
        return result
    }
}

/** The catalog the screens read. */
val LocalCatalog = staticCompositionLocalOf { Catalog(emptyMap()) }

/**
 * The loader of the catalogs.
 *
 * The English catalog is read once at start, the chosen locale is read when
 * the user picks it. Both live in the assets, written by
 * `packages/android/build/generate-catalogs.mjs`.
 */
class CatalogStore(context: Context) {

    private val assets = context.applicationContext.assets
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(locale: String): Catalog {
        val english = read("en")
        val selected = read(locale) ?: english
        selected.fallback = english
        return selected
    }

    private suspend fun read(locale: String): Catalog? = withContext(Dispatchers.IO) {
        runCatching {
            assets.open("i18n/$locale.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val parsed = json.parseToJsonElement(text) as? JsonObject ?: return@use null
                parsed.entries.associate { (key, value) ->
                    key to value.jsonPrimitive.content
                }
            }
        }.getOrNull()?.let { Catalog(it) }
    }
}
