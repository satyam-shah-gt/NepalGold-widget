package com.goldrate.widget.data

/**
 * The metal the widget is currently showing.
 *
 * Only the tola rates are published in the widget; the ten gram rates are ignored.
 */
enum class Metal(val code: String) {
    GOLD("gold"),
    SILVER("silver");

    /** Whether [quote] is the tola rate of this metal. */
    fun matches(quote: RateQuote): Boolean {
        val isTola = quote.type.contains("तोला")
        return when (this) {
            GOLD -> isTola && quote.type.contains("सुन")
            SILVER -> isTola && quote.type.contains("चाँदी")
        }
    }

    companion object {
        fun from(code: String?): Metal = if (code == "silver") SILVER else GOLD
    }
}

/** One plotted point: a rate and the calendar day it belongs to. */
data class RatePoint(val date: String, val value: Double)

object RateSeries {

    /**
     * The tola rates of [metal] over time, oldest first, ready to be plotted.
     *
     * The stored history is newest first, so it is reversed here. When only one day has been
     * stored, the "yesterday" rate that comes with every quote is used as the previous point,
     * so the chart has a line rather than a single dot.
     */
    fun tolaRates(history: List<DayRates>, metal: Metal): List<Double> =
        tolaPoints(history, metal).map { it.value }

    /**
     * The tola rates of [metal] over time together with the day each of them belongs to.
     *
     * The chart labels its X axis with those days, so the date has to travel with the value.
     */
    fun tolaPoints(history: List<DayRates>, metal: Metal): List<RatePoint> {
        val days = history.sortedBy { it.date }
        val points = days.mapNotNullTo(mutableListOf()) { day ->
            day.quotes.firstOrNull { metal.matches(it) }
                ?.takeIf { !it.today.isNaN() }
                ?.let { RatePoint(day.date, it.today) }
        }

        if (points.size == 1) {
            val previous = days.firstOrNull()
                ?.quotes?.firstOrNull { metal.matches(it) }
                ?.yesterday
            if (previous != null && !previous.isNaN()) {
                // The extra point belongs to the day before the only stored day.
                points.add(0, RatePoint(previousDay(points.first().date), previous))
            }
        }
        return points
    }

    /** The ISO date before [date], or the date itself when it cannot be parsed. */
    private fun previousDay(date: String): String =
        runCatching { java.time.LocalDate.parse(date).minusDays(1).toString() }.getOrDefault(date)

    /** The tola quote for [metal] on [day], if it was published. */
    fun tolaQuote(day: DayRates?, metal: Metal): RateQuote? =
        day?.quotes?.firstOrNull { metal.matches(it) }

    /**
     * The last [days] calendar days up to the latest published day, with the tola rate of
     * [metal] on each of them - `null` on the days the publisher did not publish.
     *
     * The window is a calendar, not a list of published days, so the chart is a real time
     * axis: a gap in the line means the publisher did not publish that day, and the labels
     * show which days those were.
     */
    fun tolaWindow(history: List<DayRates>, metal: Metal, days: Int): List<RatePoint> {
        val byDate = history.associateBy { it.date }
        val latest = byDate.keys.maxOrNull() ?: return emptyList()
        val end = runCatching { java.time.LocalDate.parse(latest) }.getOrNull()
            ?: return emptyList()

        return (days - 1 downTo 0).map { back ->
            val date = end.minusDays(back.toLong())
            val day = byDate[date.toString()]
            val value = day?.quotes?.firstOrNull { metal.matches(it) }?.takeIf { !it.today.isNaN() }
            RatePoint(date.toString(), value?.today ?: Double.NaN)
        }
    }
}
