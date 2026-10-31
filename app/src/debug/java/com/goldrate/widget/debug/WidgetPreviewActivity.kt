package com.goldrate.widget.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateApi
import com.goldrate.widget.data.RateParser
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.data.RateStore
import com.goldrate.widget.widget.WidgetRenderer
import com.goldrate.widget.widget.WidgetState
import com.goldrate.widget.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only harness that renders the widget's RemoteViews in an Activity.
 *
 * It is the quickest way to see the widget exactly as the launcher draws it - same layout, same
 * colours, same chart - without having to place it on the home screen. It lives in the debug
 * source set, so it is never part of the release APK.
 */
class WidgetPreviewActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this).apply {
            id = R.id.preview_container
            setBackgroundColor(0xFFEFE9DF.toInt())
            setPadding(24, 24, 24, 24)
        }
        setContentView(container)

        scope.launch {
            val store = RateStore(applicationContext)

            // Allows previewing a specific language, e.g. `am start ... --es lang ne`.
            intent?.getStringExtra("lang")?.let { code ->
                store.setLanguage(AppLang.from(code))
            }

            // Allows previewing the other metal, e.g. `--es metal silver`.
            intent?.getStringExtra("metal")?.let { code ->
                store.setMetal(Metal.from(code))
            }

            // Allows enqueuing a manual sync from adb, e.g. `--ez enqueueRefresh true`.
            if (intent?.getBooleanExtra("enqueueRefresh", false) == true) {
                WorkScheduler.enqueueManual(applicationContext)
            }

            // Fills the history with plausible sample values so the chart can be reviewed before
            // the app has collected real data. Debug builds only.
            if (intent?.getBooleanExtra("seedDemo", false) == true) {
                seedSampleHistory(store)
            }

            var history = store.currentHistory()
            if (history.isEmpty()) {
                // Fetch live data so the preview shows the real thing.
                runCatching { RateApi.fetch() }
                    .onSuccess { parsed ->
                        store.appendDays(parsed.toHistoryDays())
                        history = store.currentHistory()
                    }
            }

            val state = WidgetState(
                lang = store.currentLanguage(),
                metal = store.currentMetal(),
                today = history.firstOrNull(),
                history = history,
                lastSuccessAt = System.currentTimeMillis(),
                transparency = store.currentTransparency(),
            )
            // Size the card like the launcher would: a four by two grid of the default six row
            // home screen, so the preview reflects the real proportions.
            // The card is drawn at the size the launcher really gives the widget, so what is
            // reviewed here is what the user gets. When a widget is placed, its reported size is
            // used; otherwise the measured size of a four by two widget is assumed.
            val density = resources.displayMetrics.density

            // The size a four by two widget really gets on this device. The launcher's
            // reported options are the widget's *minimum* size, not the size it was given, so
            // they cannot be used to preview the widget.
            val (widthDp, heightDp) = WIDGET_SIZE_DP

            val widgetWidth = (widthDp * density).toInt()
            val widgetHeight = (heightDp * density).toInt()

            // The chart bitmap is drawn at the size the chart will actually occupy, so the
            // preview is not a stretched version of a differently sized drawing.
            val (chartWidthDp, chartHeightDp) = WidgetRenderer.chartSizeDp(widthDp, heightDp)
            val chartWidthPx = (chartWidthDp * density).toInt()
            val chartHeightPx = (chartHeightDp * density).toInt()

            Log.i(
                TAG,
                "preview card ${widthDp}x${heightDp}dp " +
                    "(${widgetWidth}x${widgetHeight}px), chart ${chartWidthDp}x${chartHeightDp}dp",
            )

            val views = WidgetRenderer.build(
                context = this@WidgetPreviewActivity,
                state = state,
                chartWidthPx = chartWidthPx.coerceAtLeast(1),
                chartHeightPx = chartHeightPx.coerceAtLeast(1),
            )
            // The wrapper is the widget's host, so the RemoteViews are inflated with it as the
            // parent: the widget root then picks up LinearLayout.LayoutParams, which is what the
            // wrapper expects. Passing an unrelated parent here gives the root the wrong
            // LayoutParams type and the Activity dies with a ClassCastException at measure time.
            val wrapper = LinearLayout(this@WidgetPreviewActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val rendered = views.apply(this@WidgetPreviewActivity, wrapper)
            wrapper.addView(rendered)

            container.addView(
                wrapper,
                FrameLayout.LayoutParams(
                    widgetWidth.coerceAtLeast(1),
                    widgetHeight.coerceAtLeast(1),
                ),
            )
        }
    }

    /** Inserts 30 days of synthetic tola rates, for previewing the chart only. */
    private suspend fun seedSampleHistory(store: RateStore) {
        val today = java.time.LocalDate.now(RateParser.NEPAL_ZONE)
        var gold = 279_000.0
        var silver = 3_880.0
        for (i in 29 downTo 0) {
            gold += (if (i % 3 == 0) 1_400.0 else -600.0) + (i % 5) * 90
            silver += (if (i % 4 == 0) 26.0 else -11.0) + (i % 3) * 3
            store.appendDay(
                DayRates(
                    date = today.minusDays(i.toLong()).toString(),
                    quotes = listOf(
                        RateQuote("छापावाल सुन (१ तोला)", gold, gold - 320),
                        RateQuote("असली चाँदी दर (१ तोला)", silver, silver - 22),
                        RateQuote("छापावाल सुन (१० ग्राम)", gold / 1.166, gold / 1.166 - 280),
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "FenegosidaPreview"

        /**
         * The size a four by two widget really gets on this device, in dp: the width is what
         * the launcher reports, the height was measured on the home screen of a 1080x2400
         * emulator with the launcher's five column grid.
         */
        val WIDGET_SIZE_DP = 360 to 231
    }
}
