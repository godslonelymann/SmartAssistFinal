package com.example.smartassist.understanding

import android.graphics.Bitmap
import com.example.smartassist.llm.GroqVisionClient
import com.example.smartassist.ocr.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenUnderstandingModule(
    private val groqVisionClient: GroqVisionClient
) {

    suspend fun understand(
        screenshot: Bitmap,
        ocrBlocks: List<TextBlock>
    ): ScreenUnderstandingResult =
        withContext(Dispatchers.IO) {

            // Convert OCR blocks to plain text
            val ocrText = ocrBlocks
                .map { it.text.trim() }
                .distinct()
                .joinToString("\n")

            // Call Groq with OCR text
            val groqResponse =
                groqVisionClient.analyzeScreen(
                    screenshot = screenshot,
                    ocrText = ocrText
                )

            // Merge OCR + Vision output
            HybridMerger.merge(
                ocrBlocks = ocrBlocks,
                groqRaw = groqResponse
            )
        }
}