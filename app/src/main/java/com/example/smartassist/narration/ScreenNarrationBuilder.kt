package com.example.smartassist.narration

import android.content.Context
import com.example.smartassist.understanding.ScreenUnderstandingResult

object ScreenNarrationBuilder {

    fun build(
        context: Context,
        result: ScreenUnderstandingResult
    ): String {

        return buildString {



            // 2️⃣ Main explanation
            if (result.summary.isNotBlank()) {
                appendLine(result.summary.trim())
                appendLine()
            }

            // 3️⃣ Image description
            result.imageDescription?.let { image ->
                if (image.isNotBlank()) {
                    appendLine("Image Description")
                    appendLine(image.trim())
                    appendLine()
                }
            }

            // 4️⃣ All available actions
            if (result.actions.isNotEmpty()) {

                appendLine("Actions")

                result.actions.forEach { action ->
                    appendLine("• ${action.trim()}")
                }
            }
        }.trim()
    }
}
