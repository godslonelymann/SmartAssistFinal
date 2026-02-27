package com.example.smartassist.output

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TtsPlayer(
    private val context: Context
) {

    companion object {
        private const val TAG = "SmartAssistTTS"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        initialize()
    }

    private fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d(TAG, "TTS initialized successfully")

                tts?.setOnUtteranceProgressListener(object :
                    UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS completed")
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error during playback")
                    }
                })

            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
    }

    // =========================================================
    // PUBLIC SPEAK FUNCTION
    // =========================================================

    fun speak(text: String, locale: Locale) {

        if (!isReady) {
            Log.e(TAG, "TTS not ready")
            return
        }

        val result = tts?.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "Language missing or not supported: $locale")
            requestLanguageInstall()
            return
        }

        // Improve voice quality (slightly slower, more natural)
        tts?.setSpeechRate(0.95f)
        tts?.setPitch(1.0f)

        val utteranceId = UUID.randomUUID().toString()

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    // =========================================================
    // AUTO LANGUAGE INSTALL TRIGGER
    // =========================================================

    private fun requestLanguageInstall() {
        try {
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(installIntent)

            Log.d(TAG, "Opened TTS language install screen")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open TTS install screen", e)
        }
    }

    // =========================================================
    // STOP
    // =========================================================

    fun stop() {
        tts?.stop()
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d(TAG, "TTS shutdown complete")
    }
}