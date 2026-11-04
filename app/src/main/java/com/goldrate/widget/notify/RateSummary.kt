package com.goldrate.widget.notify

import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateDates
import com.goldrate.widget.data.RateQuote
import java.time.LocalDate

/**
 * What the daily notification says.
 *
 * Pure on purpose - no Android types - so every decision the notification makes can be unit
 * tested on the JVM.
 */
object RateSummary {

    /**
     * The rate of [metal] for one tola - the same quote the widget shows.
     *
     * The endpoint publishes a rate for one tola *and* for ten grams, and the two are different
     * numbers, so the quote has to be picked by its unit rather than by its position.
     */
    fun rate(quotes: List<RateQuote>, metal: Metal, lang: AppLang): String {
        val quote = quotes.firstOrNull { metal.matches(it) }
        return quote?.let { Format.amount(it.today, lang) } ?: Format.EM_DASH
    }

    /** The signed percentage against the previous day, e.g. `-1.13%`. */
    fun change(quote: RateQuote?, lang: AppLang): String {
        if (quote == null) return Format.EM_DASH
        return Format.signedPercent(quote.change, quote.yesterday, lang)
    }

    /** The day the rate belongs to, e.g. `Oct 29` / `अक्टोबर २९`. */
    fun day(date: LocalDate, lang: AppLang): String = RateDates.label(date, lang)

    /**
     * Whether [publishedDate] is a day the publisher has not published before.
     *
     * The publisher updates once a day and skips weekends and holidays, so on those days the
     * endpoint keeps serving the last published payload. There is nothing new to report then, and
     * the dates are ISO, so they compare as they do everywhere else in the app.
     */
    fun isNewPublication(previousDate: String?, publishedDate: String): Boolean {
        if (previousDate == null) return true
        return publishedDate > previousDate
    }
}
