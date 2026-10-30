package com.goldrate.widget.data

import java.time.LocalDate

/**
 * Formats the day a rate was published.
 *
 * The publisher updates once a day, in the late morning Nepal time, so the rate the widget
 * shows is often *yesterday's* - the widget labels it "latest" and prints the day it belongs
 * to, instead of pretending it is today's price.
 */
object RateDates {

    private val ENGLISH_MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /** The Gregorian month names as they are written in Nepali. */
    private val NEPALI_MONTHS = arrayOf(
        "जनवरी", "फेब्रुअरी", "मार्च", "अप्रिल", "मे", "जून",
        "जुलाई", "अगस्ट", "सेप्टेम्बर", "अक्टोबर", "नोवेम्बर", "डिसेम्बर",
    )

    /** The short form of [date]: `Oct 29` in English, `अक्टोबर २९` in Nepali. */
    fun label(date: LocalDate, lang: AppLang): String {
        val month = when (lang) {
            AppLang.EN -> ENGLISH_MONTHS[date.monthValue - 1]
            AppLang.NE -> NEPALI_MONTHS[date.monthValue - 1]
        }
        val text = "$month ${date.dayOfMonth}"
        return if (lang == AppLang.NE) Format.toDevanagari(text) else text
    }
}
