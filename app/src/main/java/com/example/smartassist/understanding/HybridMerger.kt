package com.example.smartassist.understanding

import org.json.JSONObject

object HybridMerger {

    fun merge(groqRaw: String?): ScreenUnderstandingResult {

        if (groqRaw.isNullOrBlank()) {
            return ScreenUnderstandingResult(
                summary = "Unable to understand screen.",
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.3f
            )
        }

        return try {
            val cleaned = groqRaw
                .replace(Regex("^\\s*```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\s*```\\s*"), "")
                .replace(Regex("\\s*```\\s*$"), "")
                .trim()

            val json = JSONObject(cleaned)

            val summary = json.optString("summary", "")
                .trim()
                .ifBlank { "No summary available." }

            val imageDescription = json.optString("imageDescription", "")
                .trim()
                .takeIf { it.isNotBlank() }

            val confidence = when {
                json.has("confidence") && !json.isNull("confidence") -> {
                    when (val rawConfidence = json.get("confidence")) {
                        is Number -> rawConfidence.toFloat()
                        is String -> rawConfidence.toFloatOrNull() ?: 0.7f
                        else -> 0.7f
                    }
                }
                else -> 0.7f
            }.coerceIn(0f, 1f)

            val actionsList = mutableListOf<String>()
            val actionsArray = json.optJSONArray("actions")

            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val action = when (val item = actionsArray.opt(i)) {
                        is String -> item.trim()
                        is JSONObject -> item.optString("label", "").trim()
                        else -> item?.toString()?.trim().orEmpty()
                    }

                    if (action.isNotBlank()) {
                        actionsList.add(action)
                    }
                }
            }

            ScreenUnderstandingResult(
                summary = summary,
                actions = actionsList.distinct(),
                imageDescription = imageDescription,
                confidence = confidence
            )

        } catch (e: Exception) {
            val fallbackSummary = groqRaw
                .replace(Regex("^\\s*```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\s*```\\s*"), "")
                .replace(Regex("\\s*```\\s*$"), "")
                .trim()
                .ifBlank { "Unable to understand screen." }

            ScreenUnderstandingResult(
                summary = fallbackSummary,
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.5f
            )
        }
    }
}
