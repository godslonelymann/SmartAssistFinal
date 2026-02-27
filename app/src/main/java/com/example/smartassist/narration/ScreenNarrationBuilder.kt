package com.example.smartassist.narration

import android.content.Context
import com.example.smartassist.settings.UserPreferences
import com.example.smartassist.understanding.ScreenUnderstandingResult

object ScreenNarrationBuilder {

    fun build(
        context: Context,
        result: ScreenUnderstandingResult
    ): String {

        val language =
            UserPreferences.getSelectedLanguage(context)

        return buildString {



            // 2️⃣ Main explanation
            if (result.summary.isNotBlank()) {
                appendLine(result.summary.trim())
                appendLine()
            }

            // 3️⃣ Image description
            result.imageDescription?.let { image ->
                if (image.isNotBlank()) {
                    when (language) {
                        "hi" -> appendLine("स्क्रीन पर एक छवि दिखाई दे रही है।")
                        "mr" -> appendLine("स्क्रीनवर एक प्रतिमा दिसत आहे.")
                        else -> appendLine("There is an image visible on the screen.")
                    }
                    appendLine(image.trim())
                    appendLine()
                }
            }

            // 4️⃣ All available actions
            if (result.actions.isNotEmpty()) {

                when (language) {
                    "hi" -> appendLine("आप निम्नलिखित कार्य कर सकते हैं:")
                    "mr" -> appendLine("आपण खालील क्रिया करू शकता:")
                    else -> appendLine("You can perform the following actions:")
                }

                result.actions.forEach { action ->
                    appendLine("• ${action.trim()}")
                }
            }
        }.trim()
    }
}