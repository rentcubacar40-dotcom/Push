package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.User
import com.example.data.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moodgram_session")

class SessionManager(private val context: Context) {

    companion object {
        val KEY_USERNAME = stringPreferencesKey("session_username")
        val KEY_DISPLAY_NAME = stringPreferencesKey("session_display_name")
        val KEY_ROLE = stringPreferencesKey("session_role")
        val KEY_AVATAR_REF = stringPreferencesKey("session_avatar_ref")
        val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val KEY_COLOR_THEME = stringPreferencesKey("app_color_theme") // "TEAL", "VIOLET", "MAGENTA", "BLUE", "EMERALD", "AMBER"
    }

    val userSessionFlow: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val username = prefs[KEY_USERNAME]
        val displayName = prefs[KEY_DISPLAY_NAME]
        val role = prefs[KEY_ROLE] ?: "user"
        val avatarRef = prefs[KEY_AVATAR_REF] ?: ""

        if (!username.isNullOrEmpty() && !displayName.isNullOrEmpty()) {
            UserSession(
                username = username,
                displayName = displayName,
                role = role,
                avatarRef = avatarRef
            )
        } else {
            null
        }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val colorThemeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLOR_THEME] ?: "TEAL"
    }

    suspend fun saveSession(user: User) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_DISPLAY_NAME] = user.displayName
            prefs[KEY_ROLE] = user.role
            prefs[KEY_AVATAR_REF] = user.avatarRef
        }
    }

    suspend fun updateAvatar(avatarRef: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR_REF] = avatarRef
        }
    }

    suspend fun updateDisplayName(displayName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setColorTheme(colorTheme: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COLOR_THEME] = colorTheme
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_ROLE)
            prefs.remove(KEY_AVATAR_REF)
        }
    }
}
