package com.example.smartassist.understanding

data class ScreenKeyElement(
    val title: String,
    val description: String
)

data class VisibleTextGroup(
    val heading: String = "",
    val lines: List<String> = emptyList()
)

data class ScreenUnderstandingResult(
    val summary: String,
    val actions: List<String>,   // 🔥 NEW
    val imageDescription: String?,
    val confidence: Float,
    val screenType: String = "unknown",
    val title: String = "",
    val assistantAnswer: String = "",
    val visibleTextLines: List<String> = emptyList(),
    val visibleTextExplanation: String = "",
    val visibleTextGroups: List<VisibleTextGroup> = emptyList(),
    val keyElements: List<ScreenKeyElement> = emptyList(),
    val contextInfo: String = ""
)
