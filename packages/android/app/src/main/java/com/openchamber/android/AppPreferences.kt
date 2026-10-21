package com.openchamber.android

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openchamber.android.core.design.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "openchamber.app")

/**
 * The settings that live on the phone.
 *
 * Everything else is server state. The phone keeps what the server cannot
 * know: how the user wants the app to look, what they wrote but did not send,
 * and where they were.
 */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.preferences

    val themeMode: Flow<ThemeMode> = store.data.map { preferences ->
        when (preferences[THEME_MODE]) {
            ThemeMode.Light.name -> ThemeMode.Light
            ThemeMode.Dark.name -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    val lastSessionID: Flow<String?> = store.data.map { it[LAST_SESSION] }

    val lastDirectory: Flow<String?> = store.data.map { it[LAST_DIRECTORY] }

    val draft: Flow<String> = store.data.map { it[DRAFT].orEmpty() }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLastSession(sessionID: String?) {
        store.edit { preferences ->
            if (sessionID == null) preferences.remove(LAST_SESSION) else preferences[LAST_SESSION] = sessionID
        }
    }

    suspend fun setLastDirectory(directory: String?) {
        store.edit { preferences ->
            if (directory == null) {
                preferences.remove(LAST_DIRECTORY)
            } else {
                preferences[LAST_DIRECTORY] = directory
            }
        }
    }

    suspend fun setDraft(text: String) {
        store.edit { preferences ->
            if (text.isEmpty()) preferences.remove(DRAFT) else preferences[DRAFT] = text
        }
    }

    private companion object {
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("themeMode")
        val LAST_SESSION: Preferences.Key<String> = stringPreferencesKey("lastSessionID")
        val LAST_DIRECTORY: Preferences.Key<String> = stringPreferencesKey("lastDirectory")
        val DRAFT: Preferences.Key<String> = stringPreferencesKey("draft")
    }
}
