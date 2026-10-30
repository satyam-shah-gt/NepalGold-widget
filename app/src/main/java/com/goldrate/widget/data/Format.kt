package com.goldrate.widget.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Number formatting for the widget and the settings screen.
 *
 * Two conventions are implemented:
 *  - English uses western 3-3-3 grouping: `243,055`.
 *  - Nepali uses the South Asian 2-2-3 grouping that Nepal shares with India
 *    (`2,43,055` - the last three digits form one group, every group above that is two digits),
 *    and renders the digits in Devanagari: `२,४३,०५५`.
 *
 * The grouping is implemented here rather than delegated to `DecimalFormat` because Java's
 * `DecimalFormat` cannot express the 2-2-3 pattern and the platform locale data does not
 * provide it for Nepali either.
 */
object Format {

    private val DEVANAGARI_DIGITS = charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')

    /** Replaces ASCII digits with Devanagari digits. */
    fun toDevanagari(value: String): String = value.map { c ->
        if (c in '0'..'9') DEVANAGARI_DIGITS[c - '0'] else c
    }.joinToString("")

    /** Replaces Devanagari digits with ASCII digits. */
    fun toLatinDigits(value: String): String = value.map { c ->
        when (c) {
            in '०'..'९' -> '0' + (c - '०')
            else -> c
        }
    }.joinToString("")

    /**
     * Formats a monetary amount with thousands separators.
     *
     * Whole amounts lose the decimal part; fractional amounts keep two decimals so silver rates
     * such as 3369.50 stay exact.
     */
    fun amount(value: Double, lang: AppLang): String {
        if (value.isNaN() || value.isInfinite()) return EM_DASH

        val decimals = if (value == floor(value)) 0 else 2
        val plain = BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .toPlainString()

        val dot = plain.indexOf('.')
        val integerPart = if (dot < 0) plain else plain.substring(0, dot)
        val fractionPart = if (dot < 0) "" else plain.substring(dot)

        val grouped = when (lang) {
            AppLang.EN -> groupWestern(integerPart)
            AppLang.NE -> groupSouthAsian(integerPart)
        }

        val text = grouped + fractionPart
        return if (lang == AppLang.NE) toDevanagari(text) else text
    }

    /** Formats a difference against yesterday, keeping its sign. */
    fun signed(value: Double, lang: AppLang): String {
        if (value.isNaN() || value.isInfinite()) return EM_DASH
        val sign = when {
            value > 0 -> "+"
            value < 0 -> "-"
            else -> ""
        }
        return sign + amount(abs(value), lang)
    }

    /** Formats a percentage change against yesterday, keeping its sign. */
    fun signedPercent(change: Double, base: Double, lang: AppLang): String {
        if (change.isNaN() || base.isNaN() || base == 0.0) return EM_DASH
        val sign = when {
            change > 0 -> "+"
            change < 0 -> "-"
            else -> ""
        }
        val text = String.format(Locale.US, "%.2f", abs(change / base * 100.0))
        // The percent sign belongs to both languages - only the digits change.
        return sign + (if (lang == AppLang.NE) toDevanagari(text) else text) + "%"
    }

    /**
     * Formats a rounded, grouped whole number - the chart's axis labels.
     *
     * Unlike [amount] it never keeps decimals: an axis label has to stay short enough for the
     * gutter, and the extremes have to read differently from each other.
     */
    fun whole(value: Double, lang: AppLang): String {
        if (value.isNaN() || value.isInfinite()) return EM_DASH
        val rounded = BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP)
        val sign = if (rounded.signum() < 0) "-" else ""
        val digits = rounded.abs().toPlainString()
        val grouped = when (lang) {
            AppLang.EN -> groupWestern(digits)
            AppLang.NE -> groupSouthAsian(digits)
        }
        val text = sign + grouped
        return if (lang == AppLang.NE) toDevanagari(text) else text
    }

    /** Formats a number with a fixed number of decimals, in the language's digits. */
    fun decimal(value: Double, decimals: Int, lang: AppLang): String {
        if (value.isNaN() || value.isInfinite()) return EM_DASH
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (lang == AppLang.NE) toDevanagari(text) else text
    }

    /**
     * Formats the chart's Y axis labels.
     *
     * Large values are shortened with a `k` suffix so the label stays narrow; smaller ones keep
     * their full precision, otherwise the extremes would read identically and the axis would
     * look broken. The digits follow the selected language.
     */
    fun axisLabel(value: Double, lang: AppLang): String {
        val magnitude = abs(value)
        return when {
            magnitude >= 100000 -> decimal(value / 1000.0, 0, lang) + "k"
            magnitude >= 10000 -> decimal(value / 1000.0, 1, lang) + "k"
            else -> whole(value, lang)
        }
    }

    /** Groups digits in threes from the right: `123456789` -> `123,456,789`. */
    private fun groupWestern(digits: String): String {
        if (digits.length <= 3) return digits
        val groups = ArrayList<String>()
        var rest = digits
        while (rest.length > 3) {
            groups.add(0, rest.takeLast(3))
            rest = rest.dropLast(3)
        }
        if (rest.isNotEmpty()) groups.add(0, rest)
        return groups.joinToString(",")
    }

    /**
     * Groups digits the South Asian way: the last three digits form one group and every group
     * above that holds two digits. `243055` -> `2,43,055`.
     */
    private fun groupSouthAsian(digits: String): String {
        if (digits.length <= 3) return digits
        val groups = ArrayList<String>()
        var rest = digits.dropLast(3)
        while (rest.length > 2) {
            groups.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.add(0, rest)
        groups.add(digits.takeLast(3))
        return groups.joinToString(",")
    }

    const val EM_DASH = "—"
}
