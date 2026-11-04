package com.goldrate.widget.notify

import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the daily notification says.
 *
 * The first test is the one that matters: the endpoint publishes a rate for one tola *and* for ten
 * grams, and they are different numbers, so the notification has to pick the tola one - the same
 * number the widget shows.
 */
class RateSummaryTest {

    /** The four quotes the endpoint publishes for a day. */
    private val quotes = listOf(
        RateQuote("छापावाल सुन (१० ग्राम)", 236285.0, 235770.0),
        RateQuote("छापावाल सुन (१ तोला)", 275600.0, 275000.0),
        RateQuote("असली चाँदी दर (१० ग्राम)", 3133.50, 3151.00),
        RateQuote("असली चाँदी दर (१ तोला)", 3655.0, 3675.0),
    )

    private fun goldTola() = quotes.first { it.type.contains("सुन") && it.type.contains("१ तोला") }

    private fun silverTola() =
        quotes.first { it.type.contains("चाँदी") && it.type.contains("१ तोला") }

    @Test
    fun `shows the one tola rate and not the ten gram one`() {
        assertEquals("275,600", RateSummary.rate(quotes, Metal.GOLD, AppLang.EN))
        assertEquals("3,655", RateSummary.rate(quotes, Metal.SILVER, AppLang.EN))
    }

    @Test
    fun `shows the rate in the digits of the language`() {
        assertEquals("२,७५,६००", RateSummary.rate(quotes, Metal.GOLD, AppLang.NE))
        assertEquals("३,६५५", RateSummary.rate(quotes, Metal.SILVER, AppLang.NE))
    }

    @Test
    fun `shows the change as a signed percentage`() {
        assertEquals("+0.22%", RateSummary.change(goldTola(), AppLang.EN))
        assertEquals("-0.54%", RateSummary.change(silverTola(), AppLang.EN))
        assertEquals("+०.२२%", RateSummary.change(goldTola(), AppLang.NE))
    }

    @Test
    fun `shows a dash when there is nothing to compare against`() {
        assertEquals("—", RateSummary.change(null, AppLang.EN))
    }

    @Test
    fun `shows the day the rate belongs to`() {
        val date = LocalDate.of(2026, 11, 4)
        assertEquals("Nov 4", RateSummary.day(date, AppLang.EN))
        assertEquals("नोवेम्बर ४", RateSummary.day(date, AppLang.NE))
    }

    @Test
    fun `a day the publisher has not published before is new`() {
        assertTrue("A fresh install has no day yet", RateSummary.isNewPublication(null, "2026-11-04"))
        assertTrue(
            "The next published day is new",
            RateSummary.isNewPublication("2026-11-03", "2026-11-04"),
        )
    }

    @Test
    fun `the same day is not new`() {
        // The publisher skips weekends and holidays, so the endpoint keeps serving the last
        // published payload. There is nothing new to report then.
        assertFalse(RateSummary.isNewPublication("2026-11-04", "2026-11-04"))
    }

    @Test
    fun `an older day is not new`() {
        assertFalse(RateSummary.isNewPublication("2026-11-04", "2026-11-03"))
    }
}
