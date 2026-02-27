package com.example.smartassist.understanding

data class ScreenUnderstandingResult(
    val summary: String,
    val actions: List<String>,   // 🔥 NEW
    val imageDescription: String?,
    val confidence: Float
)