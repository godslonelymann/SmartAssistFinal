package com.example.smartassist.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OcrEngine {

    private val latinRecognizer: TextRecognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    private val devanagariRecognizer: TextRecognizer =
        TextRecognition.getClient(
            DevanagariTextRecognizerOptions.Builder().build()
        )

    /**
     * LOCKED FUNCTION NAME
     */
    suspend fun recognize(
        frames: List<Bitmap>
    ): List<TextBlock> =
        withContext(Dispatchers.Default) {

            val results = mutableListOf<TextBlock>()

            for (frameIndex in frames.indices) {

                val bitmap = frames[frameIndex]
                val image = InputImage.fromBitmap(bitmap, 0)

                val latinBlocks =
                    runRecognizer(latinRecognizer, image)

                val devBlocks =
                    runRecognizer(devanagariRecognizer, image)

                val merged = latinBlocks + devBlocks

                for (block in merged) {

                    for (line in block.lines) {

                        val rect = line.boundingBox ?: continue
                        val cleaned = line.text.trim()

                        if (cleaned.isBlank()) continue

                        val confidence = line.confidence ?: 0.0f

                        results.add(
                            TextBlock(
                                text = cleaned,
                                left = rect.left,
                                top = rect.top,
                                right = rect.right,
                                bottom = rect.bottom,
                                frameIndex = frameIndex,
                                confidence = confidence
                            )
                        )
                    }
                }
            }

            removeDuplicates(results)
        }

    private suspend fun runRecognizer(
        recognizer: TextRecognizer,
        image: InputImage
    ) =
        try {
            recognizer.process(image).await().textBlocks
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Removes duplicate OCR blocks across frames
     */
    private fun removeDuplicates(
        blocks: List<TextBlock>
    ): List<TextBlock> {

        val seen = mutableSetOf<String>()
        val unique = mutableListOf<TextBlock>()

        for (block in blocks) {

            val key = block.text.lowercase()

            if (!seen.contains(key)) {
                seen.add(key)
                unique.add(block)
            }
        }

        return unique
    }
}