package com.example.smartassist.settings

import android.content.Context

object UserPreferences {

    private const val PREF_NAME = "smart_assist_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_MODEL_DOWNLOADED_PREFIX = "model_downloaded_"

    fun getSelectedLanguage(context: Context): String {
        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setSelectedLanguage(
        context: Context,
        languageTag: String
    ) {
        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString(KEY_LANGUAGE, languageTag)
            .apply()
    }

    fun isModelDownloaded(context: Context, languageTag: String): Boolean {
        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return prefs.getBoolean(
            KEY_MODEL_DOWNLOADED_PREFIX + languageTag,
            false
        )
    }

    fun setModelDownloaded(context: Context, languageTag: String) {
        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putBoolean(
                KEY_MODEL_DOWNLOADED_PREFIX + languageTag,
                true
            )
            .apply()
    }
}