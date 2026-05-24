package com.example.smartassist.narration

import com.example.smartassist.privacy.PrivacyFilter
import com.example.smartassist.understanding.VisibleTextGroup

object VisibleTextFormatter {

    private val timePattern = Regex("""^\d{1,2}:\d{2}$""")
    private val domainPattern =
        Regex("""(?i)^(?:https?://)?(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/\S*)?$""")

    private val knownBrands =
        setOf(
            "cricbuzz",
            "cb",
            "youtube",
            "reddit",
            "brave",
            "google",
            "instagram",
            "whatsapp"
        )

    private val navigationLabels =
        setOf(
            "home",
            "menu",
            "search",
            "explore",
            "back",
            "next",
            "settings",
            "profile",
            "hot takes",
            "news",
            "videos",
            "live",
            "more",
            "login",
            "sign in",
            "sign up"
        )

    private val teamAbbreviations =
        setOf("csk", "lsg", "mi", "rcb", "ind", "aus")

    private val sportsKeywords =
        listOf("match", "ipl", "league", "won by", "wickets", "wkts", "score", "overs", "innings")

    private val paymentKeywords =
        listOf("total", "amount", "₹", "pay", "upi", "card", "coupon", "discount", "order", "proceed")

    private val formFieldLabels =
        setOf("email", "password", "phone", "username", "otp")

    private val formActionLabels =
        setOf("login", "create account", "forgot password", "submit", "continue")

    fun groupVisibleText(
        lines: List<String>,
        screenType: String? = null,
        title: String? = null
    ): List<VisibleTextGroup> {
        val cleanedLines = cleanLines(lines)
        if (cleanedLines.isEmpty()) return emptyList()

        if (isLongFormText(cleanedLines, screenType, title)) {
            val heading =
                when {
                    screenType.orEmpty().contains("poem", ignoreCase = true) ||
                        title.orEmpty().contains("poem", ignoreCase = true) -> "Poem Text"
                    screenType.orEmpty().contains("article", ignoreCase = true) -> "Article Text"
                    else -> "Main Text"
                }

            return listOf(
                VisibleTextGroup(
                    heading = heading,
                    lines = cleanedLines
                )
            )
        }

        val groups = linkedMapOf<String, MutableList<String>>()

        fun add(heading: String, line: String) {
            groups.getOrPut(heading) { mutableListOf() }.add(line)
        }

        val hasSportsContext =
            cleanedLines.any { line ->
                val lower = line.lowercase()
                lower in teamAbbreviations ||
                    sportsKeywords.any { keyword -> lower.contains(keyword) }
            }

        cleanedLines.forEach { line ->
            val lower = line.lowercase()

            when {
                timePattern.matches(line) || domainPattern.matches(line) ->
                    add("Status / Browser", line)

                hasSportsContext && (
                    lower in teamAbbreviations ||
                        sportsKeywords.any { keyword -> lower.contains(keyword) }
                    ) ->
                    add("Match Details", line)

                paymentKeywords.any { keyword -> lower.contains(keyword) } ->
                    if (lower.contains("pay") || lower.contains("coupon") || lower.contains("proceed")) {
                        add("Payment Actions", line)
                    } else {
                        add("Payment Details", line)
                    }

                lower in formFieldLabels ->
                    add("Input Fields", line)

                lower in formActionLabels ->
                    add("Actions / Links", line)

                lower in navigationLabels ->
                    add("Navigation / Tabs", line)

                lower in knownBrands ->
                    add("Website / App", line)

                else ->
                    add("Other Visible Text", line)
            }
        }

        return groups.map { (heading, groupedLines) ->
            VisibleTextGroup(
                heading = heading,
                lines = groupedLines
            )
        }
    }

    fun formatGroupsForDisplay(groups: List<VisibleTextGroup>): String {
        return groups
            .filter { it.lines.isNotEmpty() }
            .joinToString("\n\n") { group ->
                buildString {
                    if (group.heading.isNotBlank()) {
                        appendLine(group.heading)
                    }
                    append(group.lines.joinToString("\n"))
                }.trim()
            }
    }

    private fun cleanLines(lines: List<String>): List<String> {
        val cleaned = mutableListOf<String>()

        lines.forEach { rawLine ->
            val normalized =
                PrivacyFilter.maskSensitiveText(rawLine)
                    .trim()
                    .replace(Regex("\\s+"), " ")

            if (normalized.isBlank()) return@forEach

            if (cleaned.lastOrNull() != normalized) {
                cleaned.add(normalized)
            }
        }

        return cleaned
    }

    private fun isLongFormText(
        lines: List<String>,
        screenType: String?,
        title: String?
    ): Boolean {
        if (
            screenType.orEmpty().contains("poem", ignoreCase = true) ||
            screenType.orEmpty().contains("article", ignoreCase = true) ||
            title.orEmpty().contains("poem", ignoreCase = true)
        ) {
            return true
        }

        val longSentenceLikeLines =
            lines.count { line ->
                line.length > 20 &&
                    !domainPattern.matches(line) &&
                    !timePattern.matches(line)
            }

        val uiLikeLines =
            lines.count { line ->
                val lower = line.lowercase()
                lower in navigationLabels ||
                    lower in formFieldLabels ||
                    lower in formActionLabels ||
                    paymentKeywords.any { keyword -> lower.contains(keyword) }
            }

        return longSentenceLikeLines >= 3 && longSentenceLikeLines > uiLikeLines
    }

}
