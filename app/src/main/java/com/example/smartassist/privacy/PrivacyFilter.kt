package com.example.smartassist.privacy

object PrivacyFilter {

    const val SENSITIVE_FALLBACK_MESSAGE =
        "Sensitive information is visible on the screen. I will not read it aloud for privacy."

    private val labeledSensitiveValue =
        Regex(
            pattern = """(?i)\b(otp|one[- ]time password|pin|upi pin|password|passcode|cvv)\b(\s*[:=-]?\s*)([A-Za-z0-9]{3,12})"""
        )

    private val aadhaarLike =
        Regex("""\b\d{4}\s?\d{4}\s?\d{4}\b""")

    private val cardLike =
        Regex("""\b(?:\d[ -]?){13,19}\b""")

    private val bankingKeywords =
        Regex(
            pattern = """(?i)\b(bank|banking|payment|pay now|upi|card number|debit card|credit card|account number|aadhaar|aadhar)\b"""
        )

    private val longNumericValue =
        Regex("""\b\d{6,}\b""")

    fun containsSensitiveContent(text: String): Boolean {
        if (text.isBlank()) return false

        return labeledSensitiveValue.containsMatchIn(text) ||
            aadhaarLike.containsMatchIn(text) ||
            cardLike.containsMatchIn(text) ||
            (bankingKeywords.containsMatchIn(text) && longNumericValue.containsMatchIn(text))
    }

    fun maskSensitiveText(text: String): String {
        if (text.isBlank()) return text

        return text
            .replace(labeledSensitiveValue) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}[hidden for privacy]"
            }
            .replace(aadhaarLike, "[hidden for privacy]")
            .replace(cardLike, "[hidden for privacy]")
    }

    fun safeFallbackText(text: String): String {
        if (text.isBlank()) return text

        return if (containsSensitiveContent(text)) {
            SENSITIVE_FALLBACK_MESSAGE
        } else {
            maskSensitiveText(text)
        }
    }
}
