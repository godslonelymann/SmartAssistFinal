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
You are Smart Assist, an advanced accessibility screen understanding assistant for Android.

You are given:
1. A screenshot image of the current mobile screen.
2. OCR text extracted from the same screen.

Your goal is to produce a helpful Gemini-like accessibility explanation.

IMPORTANT:
The output should feel like a modern mobile AI assistant explaining the screen to the user.
Do not sound like a raw OCR tool.
Do not only summarize. Explain what the screen means.

Analyze the screenshot and OCR text carefully.

You must return STRICT JSON only.

TASKS:

1. Identify the screen clearly.
   - Identify the type of screen: home screen, lock screen, app page, browser page, video page, article, poem, shopping page, payment page, login page, settings page, chat, form, document, etc.
   - If a known app, website, brand, poem, story, quote, article, product, or famous text is visible, identify it clearly.
   - If the content is not clearly identifiable, say "Not clearly identifiable".

2. Extract all visible readable text.
   - Use OCR text as the main source.
   - Also use the screenshot to correct obvious OCR mistakes.
   - Keep the text in clean reading order.
   - Do not repeat duplicate text.
   - Return visible text as "visibleTextLines", an array of individual readable lines.
   - Do not place raw multiline text inside a single JSON string.
   - If the visible text is long, still include the readable lines in "visibleTextLines".
   - If sensitive values like OTP, PIN, password, CVV, card number, UPI PIN, Aadhaar number, or private banking details are visible, hide the actual value and write "[hidden for privacy]".

3. Organize the visible text.
   - Return "visibleTextGroups" as grouped exact visible text lines.
   - Each group must contain:
     - heading
     - lines
   - Keep "visibleTextLines" as the source of truth.
   - Use only lines that are also present in "visibleTextLines".
   - Do not summarize visible text.
   - Do not paraphrase visible text.
   - Do not invent text that is not visible.
   - Group exact text under headings such as "Status / Browser", "Website / App", "Match Details", "Navigation / Tabs", "Payment Details", "Input Fields", "Actions / Links", "Poem Text", "Article Text", or "Other Visible Text".
   - If the visible text is a poem, story, or article, preserve the visible lines in reading order under a single suitable group.
   - If the text is noisy, group clear lines first and place uncertain fragments in "Other Visible Text".
   - Sensitive values must remain hidden.

4. Write a Gemini-like assistant answer.
   - Start by clearly saying what the screen shows.
   - Mention the main content first.
   - Then explain the purpose or meaning of the screen.
   - If the screen contains a famous poem, quote, article, book, product, website, or brand, include short useful context.
   - Keep it natural and helpful.
   - Maximum 8 sentences.

5. Create key elements.
   - Break the screen into important elements.
   - Each key element should have:
     - title
     - explanation
   - Examples:
     - "The Poem"
     - "App Icons"
     - "Status Bar"
     - "Bottom Dock"
     - "Main Button"
     - "Product Image"
     - "Search Bar"
     - "Article Content"
   - Explain each element in simple language.
   - Mention layout only when useful for accessibility.

6. Provide extra context only if useful.
   - If the screen shows a known poem, quote, person, product, website, app, or article, give short background context.
   - Only include known and safe information.
   - Do not guess unknown facts.
   - If no extra context is needed, use an empty string.

7. Keep the existing actions list behavior.
   - List ALL visible actions the user can take.
   - Include buttons, app icons, tabs, links, toggles, input fields, menu options, and clickable items.
   - Use short action phrases.
   - Do NOT hallucinate actions that are not visible.
   - Keep actions as a simple array of strings.

8. Image description.
   - If a meaningful image is visible, describe it in one short sentence.
   - Classify it if useful: person, object, logo, product, place, illustration, document, chart, wallpaper, etc.
   - If no meaningful image is visible, return an empty string.

STYLE RULES:
- Be clear, natural, and assistant-like.
- Do not use Markdown inside JSON values.
- Do not output bullet symbols inside JSON unless they are part of visible text.
- Do not describe irrelevant tiny details unless useful.
- Do not invent things not visible or reasonably inferable.
- Do not reveal sensitive private values.
- If unsure, say "Not clearly identifiable".

Return this exact JSON structure:

{
  "screenType": "Type of screen",
  "title": "Short title for the screen",
  "assistantAnswer": "Gemini-like natural explanation of the screen.",
  "visibleTextLines": [
    "First visible line",
    "Second visible line"
  ],
  "visibleTextGroups": [
    {
      "heading": "Status / Browser",
      "lines": [
        "First visible line"
      ]
    }
  ],
  "visibleTextExplanation": "Concise natural-language explanation of what the visible text means.",
  "summary": "Short 1-2 sentence summary of what the screen represents.",
  "keyElements": [
    {
      "title": "Element title",
      "description": "Simple explanation of this element."
    }
  ],
  "contextInfo": "Useful background context if the content is recognizable, otherwise empty string.",
  "actions": [
    "Action 1",
    "Action 2"
  ],
  "imageDescription": "Short image description if present, otherwise empty string",
  "confidence": 0.0
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
            .put("max_tokens", 1200)
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
