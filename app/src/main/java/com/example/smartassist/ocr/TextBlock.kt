package com.example.smartassist.ocr

data class TextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val frameIndex: Int,
    val confidence: Float
)