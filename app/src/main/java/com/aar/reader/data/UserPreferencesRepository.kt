package com.aar.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aar_user_preferences")

data class UserPreferences(
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val language: String = "AUTO",
    val highlightEnabled: Boolean = true,
    val scrollMode: String = "NODE_PRIORITY",
    val scrollPercent: Float = 0.65f,
    val pauseBetweenSentencesMs: Long = 150L
)

interface IUserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun updateSpeechRate(rate: Float)
    suspend fun updateSpeechPitch(pitch: Float)
    suspend fun updateLanguage(lang: String)
    suspend fun updateHighlightEnabled(enabled: Boolean)
    suspend fun updateScrollMode(mode: String)
    suspend fun updateScrollPercent(percent: Float)
    suspend fun updatePauseDuration(pauseMs: Long)
}

class UserPreferencesRepository(private val context: Context) : IUserPreferencesRepository {

    private object PreferencesKeys {
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        val LANGUAGE = stringPreferencesKey("language")
        val HIGHLIGHT_ENABLED = booleanPreferencesKey("highlight_enabled")
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")
        val SCROLL_PERCENT = floatPreferencesKey("scroll_percent")
        val PAUSE_DURATION = longPreferencesKey("pause_duration")
    }

    override val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            speechRate = prefs[PreferencesKeys.SPEECH_RATE] ?: 1.0f,
            speechPitch = prefs[PreferencesKeys.SPEECH_PITCH] ?: 1.0f,
            language = prefs[PreferencesKeys.LANGUAGE] ?: "AUTO",
            highlightEnabled = prefs[PreferencesKeys.HIGHLIGHT_ENABLED] ?: true,
            scrollMode = prefs[PreferencesKeys.SCROLL_MODE] ?: "NODE_PRIORITY",
            scrollPercent = prefs[PreferencesKeys.SCROLL_PERCENT] ?: 0.65f,
            pauseBetweenSentencesMs = prefs[PreferencesKeys.PAUSE_DURATION] ?: 150L
        )
    }

    override suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { it[PreferencesKeys.SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun updateSpeechPitch(pitch: Float) {
        context.dataStore.edit { it[PreferencesKeys.SPEECH_PITCH] = pitch.coerceIn(0.5f, 1.5f) }
    }

    override suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { it[PreferencesKeys.LANGUAGE] = lang }
    }

    override suspend fun updateHighlightEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIGHLIGHT_ENABLED] = enabled }
    }

    override suspend fun updateScrollMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.SCROLL_MODE] = mode }
    }

    override suspend fun updateScrollPercent(percent: Float) {
        context.dataStore.edit { it[PreferencesKeys.SCROLL_PERCENT] = percent.coerceIn(0.4f, 0.9f) }
    }

    override suspend fun updatePauseDuration(pauseMs: Long) {
        context.dataStore.edit { it[PreferencesKeys.PAUSE_DURATION] = pauseMs.coerceIn(0L, 500L) }
    }
}
