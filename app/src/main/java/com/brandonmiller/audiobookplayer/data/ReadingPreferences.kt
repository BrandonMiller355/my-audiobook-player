package com.brandonmiller.audiobookplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readingDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "reading_preferences")

/**
 * How the reader draws text, and how bright the screen is while it does.
 *
 * App-wide rather than per book (`add-ebook-companion` design D10): these describe the reader and
 * the room it is read in, not any particular book. Only the reading *position* is per book, and
 * that lives on the book's row.
 *
 * DataStore rather than Room for the same reason [SpeedPreferences] does — a handful of scalars is
 * not a table.
 */
class ReadingPreferences(context: Context) {

    private val dataStore = context.applicationContext.readingDataStore

    val settings: Flow<ReadingSettings> = dataStore.data.map { prefs ->
        ReadingSettings(
            textScale = prefs[TEXT_SCALE_KEY] ?: ReadingSettings.DEFAULT_TEXT_SCALE,
            lineSpacing = prefs[LINE_SPACING_KEY] ?: ReadingSettings.DEFAULT_LINE_SPACING,
            serif = prefs[SERIF_KEY] ?: true,
            brightness = prefs[BRIGHTNESS_KEY] ?: ReadingSettings.DEFAULT_BRIGHTNESS,
        )
    }

    suspend fun setTextScale(value: Float) = put(TEXT_SCALE_KEY, ReadingSettings.clampTextScale(value))

    suspend fun setLineSpacing(value: Float) = put(LINE_SPACING_KEY, ReadingSettings.clampLineSpacing(value))

    suspend fun setBrightness(value: Float) = put(BRIGHTNESS_KEY, ReadingSettings.clampBrightness(value))

    suspend fun setSerif(serif: Boolean) {
        dataStore.edit { it[SERIF_KEY] = serif }
    }

    private suspend fun put(key: Preferences.Key<Float>, value: Float) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
        val LINE_SPACING_KEY = floatPreferencesKey("line_spacing")
        val SERIF_KEY = booleanPreferencesKey("serif")
        val BRIGHTNESS_KEY = floatPreferencesKey("brightness")
    }
}

/**
 * The reader's typography and brightness.
 *
 * Every value is bounded, and the bounds are the requirement rather than defensive tidying: the
 * `reading-preferences` spec asks that both extremes stay readable, which means neither too small
 * to read nor so large a line holds two words.
 */
data class ReadingSettings(
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    val serif: Boolean = true,
    /** 0..1 as a fraction of the screen's own maximum, not an overlay opacity (design D9). */
    val brightness: Float = DEFAULT_BRIGHTNESS,
) {
    companion object {
        const val DEFAULT_TEXT_SCALE = 1.0f
        const val MIN_TEXT_SCALE = 0.75f
        const val MAX_TEXT_SCALE = 2.0f
        const val TEXT_SCALE_STEP = 0.125f

        const val DEFAULT_LINE_SPACING = 1.5f
        const val MIN_LINE_SPACING = 1.2f
        const val MAX_LINE_SPACING = 2.2f
        const val LINE_SPACING_STEP = 0.1f

        /**
         * Starts at the system's own brightness rather than at full: opening a reader should not
         * change the screen until the user asks it to.
         */
        const val DEFAULT_BRIGHTNESS = -1f
        const val MIN_BRIGHTNESS = 0.01f
        const val MAX_BRIGHTNESS = 1.0f
        const val BRIGHTNESS_STEP = 0.1f

        fun clampTextScale(value: Float) = value.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

        fun clampLineSpacing(value: Float) = value.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING)

        fun clampBrightness(value: Float) = value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
    }

    /** True while brightness has never been set, meaning "leave the system alone". */
    val followsSystemBrightness: Boolean get() = brightness < 0f
}
