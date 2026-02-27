package com.example.smartassist.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineTranslator {

    companion object {
        private const val TAG = "OfflineTranslator"
    }

    private var translator: Translator? = null
    private var currentTargetLang: String? = null

    // Prevent concurrent initialization/download
    private val initMutex = Mutex()

    // =========================================================
    // PUBLIC TRANSLATION
    // =========================================================

    /**
     * Translate text from English → target language.
     * Assumes source language is English.
     */
    suspend fun translate(
        text: String,
        targetLanguageTag: String
    ): String = withContext(Dispatchers.IO) {

        if (text.isBlank()) return@withContext text

        try {

            val mlKitLang =
                TranslateLanguage.fromLanguageTag(targetLanguageTag)
                    ?: return@withContext text

            ensureTranslatorInitialized(mlKitLang)

            translator?.translate(text)?.await() ?: text

        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            text
        }
    }

    // =========================================================
    // PRELOAD SINGLE LANGUAGE
    // =========================================================

    /**
     * Preloads translation model for target language.
     * Works over Wi-Fi and mobile data.
     */
    suspend fun preloadLanguage(
        targetLanguageTag: String
    ): Boolean = withContext(Dispatchers.IO) {

        try {

            val mlKitLang =
                TranslateLanguage.fromLanguageTag(targetLanguageTag)
                    ?: return@withContext false

            // No need to preload English
            if (mlKitLang == TranslateLanguage.ENGLISH) {
                return@withContext true
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(mlKitLang)
                .build()

            val tempTranslator = Translation.getClient(options)

            try {

                // 🔥 No requireWifi() → Works on Wi-Fi + Mobile Data
                val conditions = DownloadConditions.Builder().build()

                tempTranslator
                    .downloadModelIfNeeded(conditions)
                    .await()

                Log.d(TAG, "Preloaded model for $targetLanguageTag")

            } finally {
                tempTranslator.close()
            }

            true

        } catch (e: Exception) {
            Log.e(TAG, "Preload failed for $targetLanguageTag", e)
            false
        }
    }

    // =========================================================
    // PRELOAD MULTIPLE LANGUAGES
    // =========================================================

    suspend fun preloadLanguages(
        targetLanguageTags: List<String>
    ) {
        for (tag in targetLanguageTags.distinct()) {
            preloadLanguage(tag)
        }
    }

    /**
     * Call during onboarding or first app launch
     * to ensure zero delay later.
     */
    suspend fun preloadCommonLanguages() {
        preloadLanguages(
            listOf(
                "hi",  // Hindi
                "mr"   // Marathi
            )
        )
    }

    // =========================================================
    // ENSURE INITIALIZED (Used by translate())
    // =========================================================

    private suspend fun ensureTranslatorInitialized(
        targetLang: String
    ) {

        initMutex.withLock {

            if (currentTargetLang == targetLang && translator != null) {
                return
            }

            translator?.close()
            translator = null

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLang)
                .build()

            translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder().build()

            translator
                ?.downloadModelIfNeeded(conditions)
                ?.await()

            currentTargetLang = targetLang
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    fun close() {
        try {
            translator?.close()
        } catch (_: Exception) {}
        translator = null
        currentTargetLang = null
    }
}