package com.goldrate.widget.ui

import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateQuote
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The row model of the history table.
 *
 * The table shows the tola rate of one metal, so it can never show a number of a different unit
 * or a different metal.
 */
class HistoryTableTest {

    private val history = listOf(
        DayRates(
            date = "2026-10-29",
            quotes = listOf(
                RateQuote("छापावाल सुन (१ तोला)", 283500.0, 283200.0),
                RateQuote("असली चाँदी दर (१ तोला)", 3930.0, 3980.0),
                RateQuote("छापावाल सुन (१० ग्राम)", 243055.0, 242800.0),
            ),
        ),
        DayRates(
            date = "2026-10-28",
            quotes = listOf(
                RateQuote("छापावाल सुन (१ तोला)", 283200.0, 282900.0),
                RateQuote("असली चाँदी दर (१ तोला)", 3980.0, 3960.0),
            ),
        ),
    )

    @Test
    fun `takes the tola rate of the selected metal`() {
        val gold = HistoryTable.rows(history, Metal.GOLD, AppLang.EN).first()
        assertEquals("283,500", gold.rate)

        val silver = HistoryTable.rows(history, Metal.SILVER, AppLang.EN).first()
        assertEquals("3,930", silver.rate)
    }

    @Test
    fun `keeps the days in the order they were stored`() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.EN)
        assertEquals(2, rows.size)
        assertEquals("Oct 29", rows[0].date)
        assertEquals("Oct 28", rows[1].date)
    }

    @Test
    fun `shows the change against the previous day`() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.EN)
        assertEquals("+300", rows[0].change)
        assertEquals(RateDirection.UP, rows[0].direction)
    }

    @Test
    fun `marks a falling rate as down`() {
        val rows = HistoryTable.rows(history, Metal.SILVER, AppLang.EN)
        assertEquals("-50", rows[0].change)
        assertEquals(RateDirection.DOWN, rows[0].direction)
    }

    @Test
    fun `uses the digits of the language`() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.NE)
        assertEquals("२,८३,५००", rows[0].rate)
        assertEquals("+३००", rows[0].change)
        assertEquals("अक्टोबर २९", rows[0].date)
    }

    @Test
    fun `shows a dash on a day the metal was not published`() {
        val day = DayRates(
            date = "2026-10-27",
            quotes = listOf(RateQuote("असली चाँदी दर (१ तोला)", 3960.0, 3950.0)),
        )
        val rows = HistoryTable.rows(listOf(day), Metal.GOLD, AppLang.EN)
        assertEquals(Format.EM_DASH, rows[0].rate)
        assertEquals(Format.EM_DASH, rows[0].change)
        assertEquals(RateDirection.FLAT, rows[0].direction)
    }
}
