package com.example.smartassist.output

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SarvamTtsClient(
    private val apiKey: String
) {

    companion object {
        private const val TAG = "SarvamTtsClient"
        private const val ENDPOINT =
            "https://api.sarvam.ai/text-to-speech"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Calls Sarvam TTS API
     * Returns decoded audio bytes (MP3/WAV) ready for MediaPlayer
     */
    suspend fun synthesize(
        text: String,
        language: String
    ): ByteArray? = withContext(Dispatchers.IO) {

        try {

            Log.d(TAG, "Starting Sarvam TTS")

            // Build JSON body
            val requestJson = JSONObject()
                .put("text", text)
                .put("voice", getVoice(language))
                .put("model", "bulbul:v2")

            Log.d(TAG, "Request JSON: $requestJson")

            val requestBody =
                requestJson.toString()
                    .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->

                val bodyString = response.body?.string()

                Log.d(TAG, "Response code: ${response.code}")

                if (!response.isSuccessful || bodyString == null) {
                    Log.e(TAG, "Sarvam request failed")
                    return@withContext null
                }

                Log.d(TAG, "Raw response: $bodyString")

                // 🔥 Parse JSON response
                val json = JSONObject(bodyString)

                if (!json.has("audios")) {
                    Log.e(TAG, "No 'audios' field in response")
                    return@withContext null
                }

                val audios = json.getJSONArray("audios")

                if (audios.length() == 0) {
                    Log.e(TAG, "Empty audio array")
                    return@withContext null
                }

                val base64Audio = audios.getString(0)

                if (base64Audio.isBlank()) {
                    Log.e(TAG, "Base64 audio empty")
                    return@withContext null
                }

                // 🔥 Decode Base64 to real audio bytes
                val audioBytes =
                    Base64.decode(base64Audio, Base64.DEFAULT)

                Log.d(TAG, "Decoded audio bytes size: ${audioBytes.size}")

                audioBytes
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sarvam TTS error", e)
            null
        }
    }

    /**
     * Maps language code to Sarvam voice
     */
    private fun getVoice(lang: String): String {
        return when (lang) {
            "hi" -> "hi-IN-bulbul"
            "mr" -> "mr-IN-bulbul"
            else -> "en-IN-bulbul"
        }
    }
}