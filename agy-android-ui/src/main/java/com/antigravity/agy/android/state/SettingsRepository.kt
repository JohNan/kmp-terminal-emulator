package com.antigravity.agy.android.state

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Extension property for DataStore delegate on Context.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "agy_settings")

/**
 * Data class representing all terminal preferences.
 */
data class TerminalSettings(
    val proxyHostUrl: String = SettingsRepository.DEFAULT_PROXY_HOST_URL,
    val terminalTheme: String = SettingsRepository.DEFAULT_TERMINAL_THEME,
    val fontSize: Float = SettingsRepository.DEFAULT_FONT_SIZE,
)

/**
 * Repository for managing application settings stored in Jetpack DataStore Preferences.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Secondary constructor taking Android [Context].
     */
    constructor(context: Context) : this(context.settingsDataStore)

    companion object {
        const val DEFAULT_PROXY_HOST_URL: String = "wss://proxy.example.com/ws"
        const val DEFAULT_TERMINAL_THEME: String = "default"
        const val DEFAULT_FONT_SIZE: Float = 14f

        val KEY_PROXY_HOST_URL = stringPreferencesKey("proxy_host_url")
        val KEY_TERMINAL_THEME = stringPreferencesKey("terminal_theme")
        val KEY_FONT_SIZE = floatPreferencesKey("font_size")
    }

    /**
     * Flow of the configured Proxy Host URL.
     */
    val proxyHostUrl: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[KEY_PROXY_HOST_URL] ?: DEFAULT_PROXY_HOST_URL
            }

    /**
     * Flow of the configured Terminal Theme ID.
     */
    val terminalTheme: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[KEY_TERMINAL_THEME] ?: DEFAULT_TERMINAL_THEME
            }

    /**
     * Flow of the configured Terminal Font Size in sp.
     */
    val fontSize: Flow<Float> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE
            }

    /**
     * Combined flow of all [TerminalSettings].
     */
    val settingsFlow: Flow<TerminalSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                TerminalSettings(
                    proxyHostUrl = preferences[KEY_PROXY_HOST_URL] ?: DEFAULT_PROXY_HOST_URL,
                    terminalTheme = preferences[KEY_TERMINAL_THEME] ?: DEFAULT_TERMINAL_THEME,
                    fontSize = preferences[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE,
                )
            }

    /**
     * Updates the Proxy Host URL.
     */
    suspend fun setProxyHostUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PROXY_HOST_URL] = url
        }
    }

    /**
     * Updates the Terminal Theme ID.
     */
    suspend fun setTerminalTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[KEY_TERMINAL_THEME] = theme
        }
    }

    /**
     * Updates the Terminal Font Size.
     */
    suspend fun setFontSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = size
        }
    }

    /**
     * Updates the Terminal Font Size from an integer value.
     */
    suspend fun setFontSize(size: Int) {
        setFontSize(size.toFloat())
    }

    /**
     * Updates all settings atomically.
     */
    suspend fun updateSettings(settings: TerminalSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_PROXY_HOST_URL] = settings.proxyHostUrl
            preferences[KEY_TERMINAL_THEME] = settings.terminalTheme
            preferences[KEY_FONT_SIZE] = settings.fontSize
        }
    }

    /**
     * Resets all settings to their default values.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences[KEY_PROXY_HOST_URL] = DEFAULT_PROXY_HOST_URL
            preferences[KEY_TERMINAL_THEME] = DEFAULT_TERMINAL_THEME
            preferences[KEY_FONT_SIZE] = DEFAULT_FONT_SIZE
        }
    }
}
