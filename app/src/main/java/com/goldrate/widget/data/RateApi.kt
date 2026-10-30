package com.goldrate.widget.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth

/**
 * Thin HTTP client for the fenegosida.org dashboard endpoints.
 *
 * Uses [HttpURLConnection] rather than a third-party client so the app carries no networking
 * dependency of its own.
 */
object RateApi {

    const val ENDPOINT = "https://api.fenegosida.org/api/website/v1/Dashboard/today"

    /**
     * The endpoint that publishes a whole month of rates.
     *
     * `?date=2026-10` returns every published day of that month, one entry per rate type. It is
     * what fills the history on a fresh install, instead of waiting a month for the app to
     * collect a day at a time.
     */
    const val MONTH_HISTORY_ENDPOINT =
        "https://api.fenegosida.org/api/website/v1/Dashboard/monthwisehistory"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    /**
     * Performs the fetch and parses the payload.
     *
     * The endpoint sits behind Cloudflare Bot Management, which serves a challenge page instead
     * of JSON to requests that do not look browser-shaped. The four headers below are the minimal
     * set that keeps the edge from blocking an automated background request.
     *
     * @throws IOException on a transport error or a non-2xx status.
     */
    fun fetch(): ParsedRates {
        val body = get(ENDPOINT)
        val parsed = RateParser.parse(body)
        if (parsed.quotes.isEmpty()) {
            throw IOException("Empty rate payload from $ENDPOINT")
        }
        return parsed
    }

    /**
     * Fetches the published rates of a month.
     *
     * @throws IOException on a transport error or a non-2xx status.
     */
    fun fetchMonth(month: YearMonth): List<DayRates> {
        val url = "$MONTH_HISTORY_ENDPOINT?date=${month.year}-${"%02d".format(month.monthValue)}"
        return RateParser.parseMonthHistory(get(url))
    }

    /** Fetches the month of [month] and the one before it. */
    fun fetchRecentHistory(month: YearMonth): List<DayRates> {
        val days = ArrayList<DayRates>()
        days += fetchMonth(month)
        days += fetchMonth(month.minusMonths(1))
        return days
    }

    /**
     * Performs a GET against [url] and returns the body.
     *
     * @throws IOException on a transport error or a non-2xx status.
     */
    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0",
            )
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Cache-Control", "no-cache")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("HTTP $status from $url")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
