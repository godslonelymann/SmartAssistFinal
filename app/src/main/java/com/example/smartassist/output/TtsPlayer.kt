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

    // 🎛 Adjustable values
    private var speechRate: Float = 0.92f   // default more natural
    private var pitch: Float = 1.05f        // slight warmth

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
    // 🎛 PUBLIC CONFIGURATION METHODS
    // =========================================================

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(speechRate)
    }

    fun setPitch(newPitch: Float) {
        pitch = newPitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun getSpeechRate(): Float = speechRate
    fun getPitch(): Float = pitch

    // =========================================================
    // 🔊 SPEAK
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
            requestLanguageInstall()
            return
        }

        // Apply current tuning values
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)

        val utteranceId = UUID.randomUUID().toString()

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    // =========================================================
    // INSTALL LANGUAGE
    // =========================================================

    private fun requestLanguageInstall() {
        try {
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(installIntent)
        } catch (_: Exception) {}
    }

    // =========================================================
    // STOP & CLEANUP
    // =========================================================

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}