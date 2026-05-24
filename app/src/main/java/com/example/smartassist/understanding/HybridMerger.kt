package com.example.smartassist.understanding

import android.util.Log
import com.example.smartassist.narration.VisibleTextFormatter
import com.example.smartassist.ocr.TextBlock
import com.example.smartassist.privacy.PrivacyFilter
import org.json.JSONArray
import org.json.JSONObject

object HybridMerger {

    private const val TAG = "HybridMerger"

    // Keeps old calls working: HybridMerger.merge(groqRaw)
    fun merge(groqRaw: String?): ScreenUnderstandingResult {
        return merge(emptyList(), groqRaw)
    }

    // New function for your current FloatingService call:
    // HybridMerger.merge(ocrBlocks, groqResponse)
    fun merge(
        ocrBlocks: List<TextBlock>,
        groqRaw: String?
    ): ScreenUnderstandingResult {

        val ocrText = ocrBlocks
            .joinToString("\n") { it.text }
            .trim()

        if (groqRaw.isNullOrBlank()) {
            val safeFallback = safeOcrFallback(ocrText)
            val fallbackVisibleLines =
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    emptyList()
                } else {
                    formatVisibleTextLines(safeFallback.lines())
                }

            val fallbackSummary =
                if (safeFallback.isNotBlank()) {
                    if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                        safeFallback
                    } else {
                        "I could not get the AI explanation, but I found this visible text on the screen:\n\n$safeFallback"
                    }
                } else {
                    "I could not understand the screen right now. Please try again."
                }

            val fallbackVisibleExplanation =
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE
                } else {
                    ""
                }

            return ScreenUnderstandingResult(
                summary = fallbackSummary,
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.3f,
                screenType = "unknown",
                assistantAnswer = fallbackSummary,
                visibleTextLines = fallbackVisibleLines,
                visibleTextExplanation = fallbackVisibleExplanation,
                visibleTextGroups = VisibleTextFormatter.groupVisibleText(
                    fallbackVisibleLines
                )
            )
        }

        return try {
            val cleaned = cleanJson(groqRaw)
            val json = JSONObject(cleaned)

            val assistantAnswer = json.optString("assistantAnswer", "")
                .trim()

            val normalSummary = json.optString("summary", "")
                .trim()

            val screenType = json.optString("screenType", "unknown")
                .trim()
                .ifBlank { "unknown" }

            val title = json.optString("title", "")
                .trim()

            val visibleTextLines = parseVisibleTextLines(
                linesArray = json.optJSONArray("visibleTextLines"),
                visibleText = json.optString("visibleText", ""),
                fallbackText = safeOcrFallback(ocrText)
            )

            val visibleTextExplanation = parseVisibleTextExplanation(
                rawExplanation = json.optString("visibleTextExplanation", ""),
                visibleTextLines = visibleTextLines,
                fallbackText = safeOcrFallback(ocrText)
            )

            val aiVisibleTextGroups = parseVisibleTextGroups(
                groupsArray = json.optJSONArray("visibleTextGroups"),
                visibleTextLines = visibleTextLines
            )

            val visibleTextGroups =
                if (aiVisibleTextGroups.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "Visible text lines=${visibleTextLines.size}, groups=${aiVisibleTextGroups.size}, source=ai"
                    )
                    aiVisibleTextGroups
                } else {
                    val localGroups =
                        VisibleTextFormatter.groupVisibleText(
                            lines = visibleTextLines,
                            screenType = screenType,
                            title = title
                        )
                    Log.d(
                        TAG,
                        "Visible text lines=${visibleTextLines.size}, groups=${localGroups.size}, source=local"
                    )
                    localGroups
                }

            val keyElements = parseKeyElements(json.optJSONArray("keyElements"))

            val contextInfo = json.optString("contextInfo", "")
                .trim()

            val summary = buildGeminiStyleSummary(
                assistantAnswer = assistantAnswer,
                normalSummary = normalSummary,
                visibleTextLines = visibleTextLines,
                visibleTextGroups = visibleTextGroups,
                keyElements = keyElements,
                contextInfo = contextInfo,
                ocrText = ocrText
            )

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

            val actionsList = parseActions(json.optJSONArray("actions"))

            ScreenUnderstandingResult(
                summary = summary,
                actions = actionsList.distinct(),
                imageDescription = imageDescription,
                confidence = confidence,
                screenType = screenType,
                title = title,
                assistantAnswer = assistantAnswer.ifBlank { normalSummary },
                visibleTextLines = visibleTextLines,
                visibleTextExplanation = visibleTextExplanation,
                visibleTextGroups = visibleTextGroups,
                keyElements = keyElements,
                contextInfo = contextInfo
            )

        } catch (e: Exception) {
            Log.w(TAG, "Groq response parsing failed: ${e.message}")

            val safeFallback = safeOcrFallback(ocrText)
            val fallbackSummary = if (safeFallback.isNotBlank()) {
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    safeFallback
                } else {
                    "I could not read the AI response correctly, but I found this visible text on the screen:\n\n$safeFallback"
                }
            } else {
                "I could not understand the screen right now. Please try again."
            }

            val fallbackVisibleLines =
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    emptyList()
                } else {
                    formatVisibleTextLines(safeFallback.lines())
                }

            val fallbackVisibleExplanation =
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE
                } else {
                    ""
                }

            ScreenUnderstandingResult(
                summary = fallbackSummary,
                actions = emptyList(),
                imageDescription = null,
                confidence = 0.5f,
                screenType = "unknown",
                assistantAnswer = fallbackSummary,
                visibleTextLines = fallbackVisibleLines,
                visibleTextExplanation = fallbackVisibleExplanation,
                visibleTextGroups = VisibleTextFormatter.groupVisibleText(
                    fallbackVisibleLines
                )
            )
        }
    }

    private fun cleanJson(raw: String): String {
        var cleaned = raw
            .replace(Regex("^\\s*```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*```\\s*"), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()

        val start = cleaned.indexOf("{")
        val end = cleaned.lastIndexOf("}")

        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start, end + 1)
        }

        return cleaned
    }

    private fun buildGeminiStyleSummary(
        assistantAnswer: String,
        normalSummary: String,
        visibleTextLines: List<String>,
        visibleTextGroups: List<VisibleTextGroup>,
        keyElements: List<ScreenKeyElement>,
        contextInfo: String,
        ocrText: String
    ): String {

        val parts = mutableListOf<String>()

        val mainAnswer = assistantAnswer.ifBlank {
            normalSummary
        }

        if (mainAnswer.isNotBlank()) {
            parts.add(
                buildString {
                    append("Assistant Answer\n")
                    append(mainAnswer)
                }
            )
        }

        val visibleTextContent =
            if (visibleTextGroups.isNotEmpty()) {
                VisibleTextFormatter.formatGroupsForDisplay(visibleTextGroups)
            } else {
                visibleTextLines.joinToString("\n")
            }

        if (visibleTextContent.isNotBlank()) {
            parts.add(
                buildString {
                    append("Visible Text\n")
                    append(visibleTextContent)
                }
            )
        }

        if (keyElements.isNotEmpty()) {
            val keyElementText = StringBuilder()
            keyElementText.append("Key Elements on the Screen\n")

            keyElements.forEach { item ->
                val title = item.title
                val description = item.description

                if (title.isNotBlank() || description.isNotBlank()) {
                    keyElementText.append("• ")

                    if (title.isNotBlank()) {
                        keyElementText.append(title)
                    }

                    if (title.isNotBlank() && description.isNotBlank()) {
                        keyElementText.append(": ")
                    }

                    if (description.isNotBlank()) {
                        keyElementText.append(description)
                    }

                    keyElementText.append("\n")
                }
            }

            val finalKeyElements = keyElementText.toString().trim()

            if (finalKeyElements != "Key Elements on the Screen") {
                parts.add(finalKeyElements)
            }
        }

        if (contextInfo.isNotBlank()) {
            parts.add(
                buildString {
                    append("Context\n")
                    append(contextInfo)
                }
            )
        }

        if (parts.isEmpty()) {
            val safeFallback = safeOcrFallback(ocrText)
            return if (safeFallback.isNotBlank()) {
                if (safeFallback == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
                    safeFallback
                } else {
                    "I found this visible text on the screen:\n\n$safeFallback"
                }
            } else {
                "No clear screen information found."
            }
        }

        return parts.joinToString("\n\n").trim()
    }

    private fun parseActions(actionsArray: JSONArray?): List<String> {
        if (actionsArray == null) return emptyList()

        val actionsList = mutableListOf<String>()

        for (i in 0 until actionsArray.length()) {
            val action = when (val item = actionsArray.opt(i)) {
                is String -> item.trim()

                is JSONObject -> {
                    item.optString("label", "")
                        .ifBlank { item.optString("action", "") }
                        .ifBlank { item.optString("title", "") }
                        .trim()
                }

                else -> item?.toString()?.trim().orEmpty()
            }

            if (action.isNotBlank()) {
                actionsList.add(action)
            }
        }

        return actionsList
    }

    private fun parseVisibleTextLines(
        linesArray: JSONArray?,
        visibleText: String,
        fallbackText: String
    ): List<String> {
        val sourceLines =
            when {
                linesArray != null && linesArray.length() > 0 -> {
                    buildList {
                        for (i in 0 until linesArray.length()) {
                            add(linesArray.optString(i, ""))
                        }
                    }
                }
                visibleText.isNotBlank() -> visibleText.lines()
                fallbackText.isNotBlank() -> fallbackText.lines()
                else -> emptyList()
            }

        return formatVisibleTextLines(sourceLines)
    }

    private fun parseVisibleTextExplanation(
        rawExplanation: String,
        visibleTextLines: List<String>,
        fallbackText: String
    ): String {
        if (fallbackText == PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE) {
            return PrivacyFilter.SENSITIVE_FALLBACK_MESSAGE
        }

        val cleanedExplanation = rawExplanation.trim()
        if (cleanedExplanation.isNotBlank()) {
            return PrivacyFilter.maskSensitiveText(cleanedExplanation)
        }

        return visibleTextLines.joinToString("\n")
    }

    private fun parseVisibleTextGroups(
        groupsArray: JSONArray?,
        visibleTextLines: List<String>
    ): List<VisibleTextGroup> {
        if (groupsArray == null || visibleTextLines.isEmpty()) return emptyList()

        val validLines = visibleTextLines.toSet()
        val groups = mutableListOf<VisibleTextGroup>()

        for (i in 0 until groupsArray.length()) {
            val item = groupsArray.optJSONObject(i) ?: continue
            val heading = item.optString("heading", "").trim()
            val linesArray = item.optJSONArray("lines") ?: continue
            val groupLines = mutableListOf<String>()

            for (lineIndex in 0 until linesArray.length()) {
                val line =
                    PrivacyFilter.maskSensitiveText(
                        linesArray.optString(lineIndex, "").trim()
                    )

                if (line.isNotBlank() && line in validLines) {
                    groupLines.add(line)
                }
            }

            if (heading.isNotBlank() && groupLines.isNotEmpty()) {
                groups.add(
                    VisibleTextGroup(
                        heading = heading,
                        lines = formatVisibleTextLines(groupLines)
                    )
                )
            }
        }

        val groupedLines =
            groups
                .flatMap { it.lines }
                .toSet()

        val unmatchedLines =
            visibleTextLines.filterNot { it in groupedLines }

        if (unmatchedLines.isNotEmpty()) {
            groups.add(
                VisibleTextGroup(
                    heading = "Other Visible Text",
                    lines = unmatchedLines
                )
            )
        }

        return groups
    }

    private fun formatVisibleTextLines(sourceLines: List<String>): List<String> {
        val formattedLines = mutableListOf<String>()

        sourceLines.forEach { rawLine ->
            val normalizedLine =
                PrivacyFilter.maskSensitiveText(rawLine)
                    .trim()
                    .replace(Regex("\\s+"), " ")

            if (normalizedLine.isBlank()) return@forEach

            if (formattedLines.lastOrNull() != normalizedLine) {
                formattedLines.add(normalizedLine)
            }
        }

        return formattedLines
    }

    private fun parseKeyElements(keyElementsArray: JSONArray?): List<ScreenKeyElement> {
        if (keyElementsArray == null) return emptyList()

        val keyElements = mutableListOf<ScreenKeyElement>()

        for (i in 0 until keyElementsArray.length()) {
            val item = keyElementsArray.optJSONObject(i) ?: continue
            val title = item.optString("title", "").trim()
            val description = item.optString("description", "").trim()

            if (title.isNotBlank() || description.isNotBlank()) {
                keyElements.add(
                    ScreenKeyElement(
                        title = title,
                        description = description
                    )
                )
            }
        }

        return keyElements
    }

    private fun safeOcrFallback(ocrText: String): String {
        if (ocrText.isBlank()) return ""

        if (PrivacyFilter.containsSensitiveContent(ocrText)) {
            Log.w(TAG, "Privacy-safe OCR fallback triggered")
        }

        return PrivacyFilter.safeFallbackText(ocrText)
    }
}
