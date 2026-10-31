package com.goldrate.widget

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.ui.HistoryTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings screen.
 *
 * The screen is a plain Activity, so it is inflated here the way the Activity inflates it.
 * That catches a missing resource or a view that does not exist - the kind of mistake the
 * JVM tests cannot see.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val history = listOf(
        DayRates(
            date = "2026-10-29",
            quotes = listOf(
                RateQuote("छापावाल सुन (१ तोला)", 283500.0, 283200.0),
                RateQuote("असली चाँदी दर (१ तोला)", 3930.0, 3980.0),
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
    fun theScreenInflates() {
        val themed = ContextThemeWrapper(context, R.style.Theme_Fenegosidawidget)
        val root = LayoutInflater.from(themed).inflate(R.layout.activity_config, null)

        assertNotNull("The rate header must be part of the screen", root.findViewById<View>(R.id.headerCard))
        assertNotNull("The language control must be part of the screen", root.findViewById<View>(R.id.languageControl))
        assertNotNull("The metal control must be part of the screen", root.findViewById<View>(R.id.metalControl))
        assertNotNull("The table must be part of the screen", root.findViewById<View>(R.id.historyContainer))
        assertNotNull("The action must be part of the screen", root.findViewById<View>(R.id.refreshButton))
    }

    @Test
    fun theTableHasAHeadingAndARowPerDay() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.EN)
        val table = HistoryTable.build(context, rows, AppLang.EN) as LinearLayout

        // The heading, then a row per day with a divider between the rows.
        assertEquals(1 + rows.size + (rows.size - 1), table.childCount)
        assertEquals("283,500", (table.getChildAt(1) as LinearLayout).let { row ->
            (row.getChildAt(1) as TextView).text.toString()
        })
    }

    @Test
    fun theTableLinesItsColumnsUp() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.EN)
        val table = HistoryTable.build(context, rows, AppLang.EN) as LinearLayout

        val heading = table.getChildAt(0) as LinearLayout
        val firstRow = table.getChildAt(1) as LinearLayout

        // The heading and the rows have to share the column weights, otherwise the numbers do
        // not sit in a straight column.
        val headingWeights = weightsOf(heading)
        val rowWeights = weightsOf(firstRow)
        assertEquals(headingWeights, rowWeights)
        assertEquals(3, headingWeights.size)
    }

    @Test
    fun theRateAndTheChangeAreRightAligned() {
        val rows = HistoryTable.rows(history, Metal.GOLD, AppLang.EN)
        val table = HistoryTable.build(context, rows, AppLang.EN) as LinearLayout
        val firstRow = table.getChildAt(1) as LinearLayout

        val rate = firstRow.getChildAt(1) as TextView
        val change = firstRow.getChildAt(2) as TextView

        // TextView.setGravity adds TOP when no vertical gravity is given, so only the
        // horizontal part is compared here.
        assertEquals(Gravity.END, rate.gravity and Gravity.END)
        assertEquals(Gravity.END, change.gravity and Gravity.END)
        assertEquals(
            "A rising rate is shown in the up colour",
            ContextCompat.getColor(context, R.color.widget_up),
            change.currentTextColor,
        )
    }

    private fun weightsOf(row: LinearLayout): List<Float> =
        (0 until row.childCount).map { index ->
            (row.getChildAt(index).layoutParams as LinearLayout.LayoutParams).weight
        }
}
