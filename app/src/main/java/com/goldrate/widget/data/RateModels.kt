package com.goldrate.widget.data

import org.json.JSONArray
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * A single published rate.
 *
 * NOTE ON THE API SCHEMA: the endpoint names its field `todayBaseRatePerGram`, but the value it
 * carries is the price *for the unit named in [type]* (for example "printed gold, 1 tola" is
 * reported as 283500, not a per-gram figure). Always render [today] against the unit carried by
 * [type] - never hard-code "per gram" in the UI.
 */
data class RateQuote(
    val type: String,
    val today: Double,
    val yesterday: Double,
) {
    /** Signed difference against yesterday's rate. */
    val change: Double get() = today - yesterday
}

/** Every quote published for a single calendar day. */
data class DayRates(
    val date: String,
    val quotes: List<RateQuote>,
)

/** The outcome of one successful endpoint read. */
data class ParsedRates(
    val date: LocalDate,
    val quotes: List<RateQuote>,
    /** The day before [date], which the endpoint publishes alongside today's rates. */
    val previousDate: LocalDate? = null,
) {
    /**
     * The history entries this fetch contributes: the previous day and today.
     *
     * The endpoint only publishes today and yesterday, but yesterday's rates are in the payload
     * as the `yesterday` value of every quote. Storing them as their own day means the chart
     * has a history from the very first fetch instead of growing one day at a time.
     */
    fun toHistoryDays(): List<DayRates> {
        val today = DayRates(date = date.toString(), quotes = quotes)

        val previous = previousDate?.takeIf { it != date }?.let { day ->
            DayRates(
                date = day.toString(),
                quotes = quotes.map { quote ->
                    // The day before yesterday is not published, so this day's change is
                    // unknown and reported as no change.
                    RateQuote(
                        type = quote.type,
                        today = quote.yesterday,
                        yesterday = quote.yesterday,
                    )
                },
            )
        }

        return listOfNotNull(previous, today)
    }
}

object RateParser {

    /** fenegosida.org publishes in Nepal local time (UTC+05:45). */
    val NEPAL_ZONE: ZoneId = ZoneId.of("Asia/Kathmandu")

    /**
     * Parses the endpoint payload.
     *
     * The payload is an array of objects, one per published rate. All entries share the same
     * publication timestamp; the first usable one wins.
     */
    fun parse(body: String, fallbackDate: LocalDate = LocalDate.now(NEPAL_ZONE)): ParsedRates {
        val array = JSONArray(body)
        val quotes = ArrayList<RateQuote>(array.length())
        var date: LocalDate? = null
        var previousDate: LocalDate? = null

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val type = obj.optString("rateType").takeIf { it.isNotBlank() } ?: continue

            quotes += RateQuote(
                type = type,
                today = obj.optDouble("todayBaseRatePerGram", Double.NaN),
                yesterday = obj.optDouble("yestardayBaseRatePerGram", Double.NaN),
            )

            if (date == null) {
                date = parseDate(obj.optString("todayDate"))
                    ?: parseDate(obj.optString("yestardayDate"))
            }
            if (previousDate == null) {
                previousDate = parseDate(obj.optString("yestardayDate"))
            }
        }

        return ParsedRates(
            date = date ?: fallbackDate,
            quotes = quotes,
            previousDate = previousDate,
        )
    }

    /**
     * Parses the payload of the month history endpoint.
     *
     * The payload holds one entry per published day and rate type:
     * `{"id":16087,"todayDate":"2026-10-01","rateType":"...","baseRatePerGram":251460.0}`.
     *
     * The entries are grouped into days, and each day's "yesterday" rate is taken from the
     * previous day of the same series, which is how the daily change is worked out.
     */
    fun parseMonthHistory(body: String): List<DayRates> {
        val array = JSONArray(body)

        // date -> rate type -> the published rate
        val byDate = LinkedHashMap<LocalDate, MutableMap<String, Double>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val type = obj.optString("rateType").takeIf { it.isNotBlank() } ?: continue
            val date = parseDate(obj.optString("todayDate")) ?: continue
            val value = obj.optDouble("baseRatePerGram", Double.NaN)
            if (value.isNaN()) continue
            byDate.getOrPut(date) { LinkedHashMap() }[type] = value
        }

        val dates = byDate.keys.sorted()
        return dates.mapIndexed { index, date ->
            val values = byDate.getValue(date)
            val previous = dates.getOrNull(index - 1)?.let { byDate.getValue(it) }
            DayRates(
                date = date.toString(),
                quotes = values.map { (type, value) ->
                    RateQuote(
                        type = type,
                        today = value,
                        // The day before the first published day is not in the payload.
                        yesterday = previous?.get(type) ?: value,
                    )
                },
            )
        }
    }

    /** Converts an endpoint timestamp such as `2026-10-29T04:48:34.614+00:00` to a Nepal date. */
    fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(raw).atZoneSameInstant(NEPAL_ZONE).toLocalDate()
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(raw.take(10))
            } catch (e2: DateTimeException) {
                null
            }
        }
    }
}
