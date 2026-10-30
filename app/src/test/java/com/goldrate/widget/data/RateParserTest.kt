package com.goldrate.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RateParserTest {

    private val payload = """
        [
          {
            "id": 16167,
            "todayDate": "2026-10-29T04:48:34.614+00:00",
            "yestardayDate": "2026-10-28T04:50:23.062+00:00",
            "rateType": "छापावाल सुन (१० ग्राम)",
            "todayBaseRatePerGram": 243055.00,
            "yestardayBaseRatePerGram": 242800.00
          },
          {
            "id": 16168,
            "todayDate": "2026-10-29T04:48:34.614+00:00",
            "yestardayDate": "2026-10-28T04:50:23.062+00:00",
            "rateType": "छापावाल सुन (१ तोला)",
            "todayBaseRatePerGram": 283500.00,
            "yestardayBaseRatePerGram": 283200.00
          },
          {
            "id": 16169,
            "todayDate": "2026-10-29T04:48:34.614+00:00",
            "yestardayDate": "2026-10-28T04:50:23.062+00:00",
            "rateType": "असली चाँदी दर (१० ग्राम)",
            "todayBaseRatePerGram": 3369.50,
            "yestardayBaseRatePerGram": 3412.00
          }
        ]
    """.trimIndent()

    @Test
    fun `parses every published rate`() {
        val parsed = RateParser.parse(payload)

        assertEquals(3, parsed.quotes.size)
        assertEquals(243055.00, parsed.quotes[0].today, 0.001)
        assertEquals("छापावाल सुन (१० ग्राम)", parsed.quotes[0].type)
        assertEquals(3369.50, parsed.quotes[2].today, 0.001)
    }

    @Test
    fun `converts the publication timestamp to the Nepal date`() {
        val parsed = RateParser.parse(payload)

        // 04:48 UTC is 10:48 in Nepal, so the Nepal date is still the 29th.
        assertEquals(LocalDate.of(2026, 10, 29), parsed.date)
    }

    @Test
    fun `falls back to the supplied date when the payload has no timestamp`() {
        val fallback = LocalDate.of(2026, 1, 2)
        val parsed = RateParser.parse("""[{"rateType":"x","todayBaseRatePerGram":1.0}]""", fallback)

        assertEquals(fallback, parsed.date)
        assertEquals(1, parsed.quotes.size)
    }

    @Test
    fun `skips entries without a rate type`() {
        val parsed = RateParser.parse("""[{"todayBaseRatePerGram":1.0}]""")

        assertTrue(parsed.quotes.isEmpty())
    }
}

class RateStorePruneTest {

    private fun day(date: String) = DayRates(date, emptyList())

    @Test
    fun `keeps only days inside the retention window`() {
        val today = LocalDate.of(2026, 10, 29)
        val days = (0 until 45).map { day(today.minusDays(it.toLong()).toString()) }

        val pruned = RateStore.pruneHistory(days, today)

        assertEquals(RateStore.HISTORY_DAYS, pruned.size)
        assertEquals("2026-10-29", pruned.first().date)
        assertEquals("2026-09-30", pruned.last().date)
    }

    @Test
    fun `drops days that fall outside the window`() {
        val today = LocalDate.of(2026, 10, 29)
        val days = listOf(
            day("2026-10-29"),
            day("2026-10-15"),
            day("2026-01-01"),
        )

        val pruned = RateStore.pruneHistory(days, today)

        assertEquals(listOf("2026-10-29", "2026-10-15"), pruned.map { it.date })
    }
}

class HistoryHarvestTest {

    private val payload = """
        [
          {
            "todayDate": "2026-10-29T04:48:34.614+00:00",
            "yestardayDate": "2026-10-28T04:50:23.062+00:00",
            "rateType": "छापावाल सुन (१ तोला)",
            "todayBaseRatePerGram": 283500.00,
            "yestardayBaseRatePerGram": 283200.00
          },
          {
            "todayDate": "2026-10-29T04:48:34.614+00:00",
            "yestardayDate": "2026-10-28T04:50:23.062+00:00",
            "rateType": "असली चाँदी दर (१ तोला)",
            "todayBaseRatePerGram": 3930.00,
            "yestardayBaseRatePerGram": 3980.00
          }
        ]
    """.trimIndent()

    @Test
    fun `keeps the day before today that the endpoint publishes`() {
        val parsed = RateParser.parse(payload)

        assertEquals(LocalDate.of(2026, 10, 29), parsed.date)
        assertEquals(LocalDate.of(2026, 10, 28), parsed.previousDate)
    }

    @Test
    fun `turns one fetch into two history days`() {
        val parsed = RateParser.parse(payload)
        val days = parsed.toHistoryDays()

        assertEquals(2, days.size)
        assertEquals("2026-10-28", days[0].date)
        assertEquals("2026-10-29", days[1].date)
    }

    @Test
    fun `the previous day carries the yesterday rates`() {
        val parsed = RateParser.parse(payload)
        val previous = parsed.toHistoryDays().first()

        assertEquals(283200.00, previous.quotes[0].today, 0.001)
        assertEquals(3980.00, previous.quotes[1].today, 0.001)
    }

    @Test
    fun `plots the tola rates of the chosen metal with their dates`() {
        val parsed = RateParser.parse(payload)
        val days = parsed.toHistoryDays()

        val gold = RateSeries.tolaPoints(days, Metal.GOLD)
        assertEquals(listOf("2026-10-28", "2026-10-29"), gold.map { it.date })
        assertEquals(listOf(283200.0, 283500.0), gold.map { it.value })

        val silver = RateSeries.tolaPoints(days, Metal.SILVER)
        assertEquals(listOf(3980.0, 3930.0), silver.map { it.value })
    }
}

class MonthHistoryTest {

    private val payload = """
        [
          {
            "id": 16087,
            "todayDate": "2026-10-01",
            "rateType": "छापावाल सुन (१ तोला)",
            "baseRatePerGram": 251460.00
          },
          {
            "id": 16088,
            "todayDate": "2026-10-02",
            "rateType": "छापावाल सुन (१ तोला)",
            "baseRatePerGram": 252000.00
          },
          {
            "id": 16089,
            "todayDate": "2026-10-02",
            "rateType": "असली चाँदी दर (१ तोला)",
            "baseRatePerGram": 3900.00
          }
        ]
    """.trimIndent()

    @Test
    fun `groups the month payload into days`() {
        val days = RateParser.parseMonthHistory(payload)

        assertEquals(2, days.size)
        assertEquals("2026-10-01", days[0].date)
        assertEquals("2026-10-02", days[1].date)
        assertEquals(1, days[0].quotes.size)
        assertEquals(2, days[1].quotes.size)
    }

    @Test
    fun `takes the change from the previous published day`() {
        val days = RateParser.parseMonthHistory(payload)

        assertEquals(251460.00, days[0].quotes[0].today, 0.001)
        // The first day of the payload has no predecessor, so it reports no change.
        assertEquals(251460.00, days[0].quotes[0].yesterday, 0.001)
        assertEquals(252000.00, days[1].quotes[0].today, 0.001)
        assertEquals(251460.00, days[1].quotes[0].yesterday, 0.001)
        assertEquals(540.00, days[1].quotes[0].change, 0.001)
    }

    @Test
    fun `the chart window is a calendar, not a list of published days`() {
        val days = RateParser.parseMonthHistory(payload)
        val window = RateSeries.tolaWindow(days, Metal.GOLD, 7)

        assertEquals(7, window.size)
        // The window ends on the latest published day.
        assertEquals("2026-10-02", window.last().date)
        assertEquals("2026-09-26", window.first().date)
        // Only the published days carry a rate.
        assertEquals(251460.0, window[5].value, 0.001)
        assertEquals(252000.0, window[6].value, 0.001)
        assertTrue(window[0].value.isNaN())
    }
}
