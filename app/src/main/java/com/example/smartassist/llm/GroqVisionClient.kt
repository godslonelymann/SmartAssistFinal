package com.example.smartassist.llm

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GroqVisionClient(
    private val apiKey: String
) {

    companion object {
        private const val TAG = "GroqVisionClient"
        private const val MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    suspend fun analyzeScreen(
        screenshot: Bitmap,
        ocrText: String
    ): String? = withContext(Dispatchers.IO) {

        try {

            Log.d(TAG, "Starting Groq Vision request")

            val base64Image = bitmapToBase64(screenshot)

            val requestJson = buildRequest(base64Image, ocrText)

            val requestBody =
                requestJson
                    .toString()
                    .toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url(ENDPOINT)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .build()

            httpClient.newCall(request).execute().use { response ->

                val bodyString = response.body?.string()

                Log.d(TAG, "Response code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Groq Vision failed: ${response.code} ${response.message}")
                    return@withContext null
                }

                if (bodyString.isNullOrBlank()) return@withContext null

                extractText(bodyString)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Groq Vision exception", e)
            null
        }
    }

    private fun buildRequest(
        imageBase64: String,
        ocrText: String
    ): JSONObject {

        val prompt = """
You are an accessibility screen understanding assistant.

Analyze the screenshot and OCR text carefully.

Your task:

1. Identify what type of screen this is (settings, login, home, product, form, etc.).
2. Explain clearly what this screen is for in simple language.
3. List ALL visible actions the user can take.
   - Include buttons, menu options, toggles, links, inputs.
   - Use short action phrases.
4. If there is any image:
   - Identify what it represents.
   - Classify it as person, animal, object, product, place, logo, or illustration.
   - Describe it in ONE short sentence.
5. Do NOT repeat OCR text word-by-word.
6. Do NOT describe layout positions.
7. Keep explanation short and useful (max 4 sentences in summary).

Return STRICT JSON:

{
  "screenType": "ONE_WORD_TYPE",
  "summary": "Clear explanation of what this screen is and what the user can do.",
  "actions": [
    "Action 1",
    "Action 2"
  ],
  "imageDescription": "Short image description if present, otherwise empty string",
  "confidence": 0.0-1.0
}

OCR Text:
$ocrText
""".trimIndent()

        val contentParts = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", "data:image/png;base64,$imageBase64")
                    )
            )

        val message = JSONObject()
            .put("role", "user")
            .put("content", contentParts)

        return JSONObject()
            .put("model", MODEL)
            .put("messages", JSONArray().put(message))
            .put("temperature", 0.1)      // More deterministic
            .put("max_tokens", 400)       // Reduced for stability
    }

    private fun extractText(responseJson: String): String? {
        return try {

            val root = JSONObject(responseJson)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null

            val message =
                choices
                    .getJSONObject(0)
                    .getJSONObject("message")

            val raw = message.optString("content", null) ?: return null

            raw
                .replace("```json", "")
                .replace("```", "")
                .trim()

        } catch (e: Exception) {
            Log.e(TAG, "Groq parse error", e)
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)  // Slight compression
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}