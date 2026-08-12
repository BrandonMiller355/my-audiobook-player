package com.brandonmiller.audiobookplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.speedDataStore: DataStore<Preferences> by preferencesDataStore(name = "speed_preferences")

/**
 * The single most-recently-used playback speed, shared across every book. A dedicated Room table
 * would be one row holding one value — `config.yaml` calls out DataStore as the better fit for
 * exactly this shape, so a per-book column (Room, see [AudiobookEntity.playbackSpeed]) is paired
 * with this one global fallback rather than a second table.
 */
class SpeedPreferences(context: Context) {

    private val dataStore = context.applicationContext.speedDataStore

    /** Falls back to 1.0x if no speed has ever been used (PRD §9). */
    suspend fun lastUsedSpeed(): Float = dataStore.data.first()[LAST_USED_SPEED_KEY] ?: 1.0f

    suspend fun setLastUsedSpeed(speed: Float) {
        dataStore.edit { it[LAST_USED_SPEED_KEY] = speed }
    }

    private companion object {
        val LAST_USED_SPEED_KEY = floatPreferencesKey("last_used_speed")
    }
}
