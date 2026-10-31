package com.goldrate.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.data.Transparency
import com.goldrate.widget.widget.GoldRateWidgetProvider
import com.goldrate.widget.widget.RateChart
import com.goldrate.widget.widget.WidgetRenderer
import com.goldrate.widget.widget.WidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the widget's RemoteViews tree and its registration with the system.
 *
 * The tree is inflated the same way the launcher inflates it, so it catches view classes that
 * RemoteViews rejects, missing ids and wrong text.
 */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
        DayRates(
            date = "2026-10-27",
            quotes = listOf(
                RateQuote("छापावाल सुन (१ तोला)", 282900.0, 282700.0),
                RateQuote("असली चाँदी दर (१ तोला)", 3960.0, 3950.0),
            ),
        ),
    )

    private fun state(metal: Metal = Metal.GOLD) = WidgetState(
        lang = AppLang.EN,
        metal = metal,
        today = history.first(),
        history = history,
        lastSuccessAt = System.currentTimeMillis(),
    )

    @Test
    fun theChartIsRendered() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        val chart = root.findViewById<ImageView>(R.id.widget_chart)
        assertNotNull("The chart must be part of the widget", chart)
        assertNotNull("The chart must have a bitmap", chart.drawable)
    }

    @Test
    fun theChartBitmapStaysInsideTheMemoryBudget() {
        val chart = RateChart.render(
            context = context,
            values = listOf(283500.0, 283200.0, 283900.0),
            widthPx = 1000,
            heightPx = 400,
            lineColor = 0xFFB8860B.toInt(),
            fillColor = 0xFFB8860B.toInt(),
            gridColor = 0x2E7C818C,
        )
        val cap = RateChart.memoryCapBytes(context)
        assertTrue(
            "The chart bitmap must stay inside the RemoteViews budget",
            chart.byteCount.toLong() <= cap,
        )
    }

    @Test
    fun theRateBlockShowsTheTolaRate() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        assertEquals(
            "Rs.",
            root.findViewById<TextView>(R.id.widget_rate_symbol).text.toString(),
        )
        assertEquals(
            "283,500",
            root.findViewById<TextView>(R.id.widget_rate_value).text.toString(),
        )
        assertEquals(
            "/ tola",
            root.findViewById<TextView>(R.id.widget_rate_unit).text.toString(),
        )
        assertEquals(
            "+0.11% ▲ (+300)",
            root.findViewById<TextView>(R.id.widget_rate_change).text.toString(),
        )
        assertEquals(
            "A rising rate is shown in the up colour",
            ContextCompat.getColor(context, R.color.widget_up),
            root.findViewById<TextView>(R.id.widget_rate_change).currentTextColor,
        )
    }

    @Test
    fun theSilverSideShowsTheSilverRate() {
        val views = WidgetRenderer.build(
            context,
            state(Metal.SILVER),
            chartWidthPx = 600,
            chartHeightPx = 200,
        )
        val root = views.apply(context, FrameLayout(context))

        assertEquals(
            "3,930",
            root.findViewById<TextView>(R.id.widget_rate_value).text.toString(),
        )
        assertEquals(
            "A falling rate is shown in the down colour",
            ContextCompat.getColor(context, R.color.widget_down),
            root.findViewById<TextView>(R.id.widget_rate_change).currentTextColor,
        )
    }

    @Test
    fun theNepaliRateUsesDevanagariDigits() {
        val nepali = state().copy(lang = AppLang.NE)
        val views = WidgetRenderer.build(context, nepali, chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        assertEquals(
            "रु.",
            root.findViewById<TextView>(R.id.widget_rate_symbol).text.toString(),
        )
        assertEquals(
            "२,८३,५००",
            root.findViewById<TextView>(R.id.widget_rate_value).text.toString(),
        )
    }

    @Test
    fun theRefreshControlIsPartOfTheWidget() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        assertNotNull(
            "The refresh control must be part of the widget",
            root.findViewById<View>(R.id.widget_refresh),
        )
        assertNotNull(
            "The metal switch must be part of the widget",
            root.findViewById<View>(R.id.widget_toggle),
        )
    }

    @Test
    fun theChartLabelsAreViewsAndNotPartOfTheBitmap() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        // The value labels live in the layout: a launcher reports the widget's minimum size,
        // not the size it really gave the widget, so a bitmap is always stretched to fit and
        // text drawn into it would be stretched with it.
        assertEquals(
            "Rs.",
            root.findViewById<TextView>(R.id.widget_axis_currency).text.toString(),
        )
        // The lowest and the highest value of the series.
        assertEquals(
            "283k",
            root.findViewById<TextView>(R.id.widget_axis_low).text.toString(),
        )
        assertEquals(
            "284k",
            root.findViewById<TextView>(R.id.widget_axis_high).text.toString(),
        )
    }

    @Test
    fun theDayLabelsMatchThePoints() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        // The chart covers the last seven calendar days, so every column is labelled even on
        // the days the publisher did not publish - that is what makes the gap readable.
        assertEquals(
            "23",
            root.findViewById<TextView>(R.id.widget_day_0).text.toString(),
        )
        assertEquals(
            "29",
            root.findViewById<TextView>(R.id.widget_day_6).text.toString(),
        )
        assertEquals(
            View.VISIBLE,
            root.findViewById<View>(R.id.widget_day_6).visibility,
        )
    }

    @Test
    fun theRateShowsTheDayItWasPublished() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        // The publisher updates once a day in the late morning, so the rate on the widget is
        // often yesterday's. The widget says "latest" and prints the day.
        assertEquals(
            "LATEST",
            root.findViewById<TextView>(R.id.widget_rate_label).text.toString(),
        )
        assertEquals(
            "Oct 29",
            root.findViewById<TextView>(R.id.widget_rate_date).text.toString(),
        )
    }

    @Test
    fun theTransparencyChangesTheSurface() {
        // Every level is its own drawable: the launcher draws the widget and RemoteViews can only
        // call methods that take an int, so the opacity cannot be applied at runtime.
        val surfaces = Transparency.entries.map { level ->
            val views = WidgetRenderer.build(
                context,
                state().copy(transparency = level),
                chartWidthPx = 600,
                chartHeightPx = 200,
            )
            val root = views.apply(context, FrameLayout(context))
            root.findViewById<View>(R.id.widget_root).background?.constantState
        }

        assertEquals(
            "Every level must draw its own surface",
            Transparency.entries.size,
            surfaces.toSet().size,
        )
    }

    @Test
    fun theWidgetShipsWithTheBalancedSurface() {
        val views = WidgetRenderer.build(context, state(), chartWidthPx = 600, chartHeightPx = 200)
        val root = views.apply(context, FrameLayout(context))

        assertEquals(
            "The widget must keep the surface it ships with",
            context.getDrawable(R.drawable.widget_glass_balanced)?.constantState,
            root.findViewById<View>(R.id.widget_root).background?.constantState,
        )
    }

    @Test
    fun theSolidSurfaceIsCompletelyOpaque() {
        // The level is called Solid, so nothing of the wallpaper may show through its fill.
        // The check holds in the day and at night, because both variants are opaque.
        val drawable = context.getDrawable(R.drawable.widget_glass_solid)

        assertNotNull("The solid surface must exist", drawable)
        val colours = (drawable as GradientDrawable).colors
        assertNotNull("The solid surface must have colours", colours)
        assertTrue(
            "The solid surface must not let the wallpaper through",
            colours!!.all { Color.alpha(it) == 255 },
        )
    }

    @Test
    fun providerInfoIsValidForTheSystem() {
        // Reading the installed providers is exactly what made the launcher crash when an integer
        // attribute was declared as a dimension, so this guards that regression.
        val providers = AppWidgetManager.getInstance(context).getInstalledProviders()
        val ours = providers.firstOrNull {
            it.provider == ComponentName(context, GoldRateWidgetProvider::class.java)
        }
        assertNotNull("The widget provider must be registered with the system", ours)
        assertEquals(
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            ours!!.widgetCategory,
        )
        assertTrue("minWidth must be a real size", ours.minWidth > 0)
        assertTrue("minHeight must be a real size", ours.minHeight > 0)
        assertTrue("The initial layout must be set", ours.initialLayout != 0)
        assertTrue("The preview layout must be set", ours.previewLayout != 0)
    }
}
