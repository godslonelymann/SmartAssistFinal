package com.example.smartassist.understanding

import com.example.smartassist.ocr.TextBlock
import org.json.JSONObject

object HybridMerger {

    fun merge(
        ocrBlocks: List<TextBlock>,
        groqRaw: String?
    ): ScreenUnderstandingResult {

        val ocrText =
            ocrBlocks
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")

        // 🔹 If Groq fails → fallback cleanly
        if (groqRaw.isNullOrBlank()) {
            return ScreenUnderstandingResult(
                summary = if (ocrText.isNotBlank()) {
                    ocrText
                } else {
                    "No visible text detected."
                },
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.5f
            )
        }

        return try {

            val json = JSONObject(groqRaw)


            val summary =
                json.optString("summary", "")
                    .trim()

            val imageDescription =
                json.optString("imageDescription", "")
                    .takeIf { it.isNotBlank() }

            val confidence =
                json.optDouble("confidence", 0.7)
                    .toFloat()
                    .coerceIn(0f, 1f)

            // 🔥 Parse actions array
            val actionsList = mutableListOf<String>()

            val actionsArray = json.optJSONArray("actions")
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val action =
                        actionsArray.optString(i).trim()
                    if (action.isNotBlank()) {
                        actionsList.add(action)
                    }
                }
            }

            ScreenUnderstandingResult(

                summary = summary,
                actions = actionsList,
                imageDescription = imageDescription,
                confidence = confidence
            )

        } catch (e: Exception) {

            // 🔹 JSON parsing failed → fallback safely
            ScreenUnderstandingResult(

                summary = groqRaw.trim(),
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.6f
            )
        }
    }
}