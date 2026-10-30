package com.goldrate.widget.data

/**
 * Turns the endpoint's Devanagari `rateType` labels into readable English.
 *
 * The mapping is done by inspecting the label's parts rather than by matching whole strings, so
 * it keeps working if the endpoint reorders or adds entries.
 *
 * The endpoint currently publishes four labels:
 *   छापावाल सुन (१० ग्राम)  ->  Printed Gold (10 gram)
 *   छापावाल सुन (१ तोला)   ->  Printed Gold (1 tola)
 *   असली चाँदी दर (१० ग्राम) ->  Real Silver (10 gram)
 *   असली चाँदी दर (१ तोला)  ->  Real Silver (1 tola)
 */
object RateLabels {

    fun display(rawType: String, lang: AppLang): String {
        // The endpoint already speaks Nepali - use its label verbatim.
        if (lang == AppLang.NE) return rawType

        val metal = when {
            rawType.contains("सुन") -> "Gold"
            rawType.contains("चाँदी") -> "Silver"
            else -> return rawType
        }
        val qualifier = when {
            rawType.contains("छापावाल") -> "Printed"
            rawType.contains("असली") -> "Real"
            else -> null
        }
        val unit = when {
            rawType.contains("ग्राम") -> gramUnit(rawType)
            rawType.contains("तोला") -> tolaUnit(rawType)
            else -> null
        }

        val parts = listOfNotNull(qualifier, metal)
        if (parts.isEmpty()) return rawType
        return if (unit == null) parts.joinToString(" ") else "${parts.joinToString(" ")} ($unit)"
    }

    private fun gramUnit(raw: String): String {
        val match = Regex("([०-९0-9]+)\\s*ग्राम").find(raw) ?: return "gram"
        return "${Format.toLatinDigits(match.groupValues[1])} gram"
    }

    private fun tolaUnit(raw: String): String {
        val match = Regex("([०-९0-9]+)\\s*तोला").find(raw) ?: return "tola"
        return "${Format.toLatinDigits(match.groupValues[1])} tola"
    }
}
