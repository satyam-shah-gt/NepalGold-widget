package com.goldrate.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateDates
import com.goldrate.widget.data.RateSeries
import com.goldrate.widget.data.RateStore
import com.goldrate.widget.data.Transparency
import com.goldrate.widget.data.withLanguage
import com.goldrate.widget.ui.ConfigActivity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Everything the widget needs to draw itself. */
data class WidgetState(
    val lang: AppLang,
    val metal: Metal,
    val today: DayRates?,
    val history: List<DayRates>,
    val lastSuccessAt: Long,
    val showSpinner: Boolean = false,
    val stale: Boolean = false,
    val transparency: Transparency = Transparency.BALANCED,
)

/**
 * Builds and pushes the widget's [RemoteViews].
 *
 * The renderer only ever reads local storage, so the widget can be drawn instantly without a
 * network round trip. Background work repaints it after a successful fetch.
 */
object WidgetRenderer {

    /** The ids of the widget instances currently placed on a home screen. */
    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, GoldRateWidgetProvider::class.java),
        )

    /** Repaints every placed widget. */
    suspend fun renderAll(context: Context, showSpinner: Boolean = false) {
        val ids = widgetIds(context)
        if (ids.isEmpty()) return
        render(context, ids, showSpinner)
    }

    /** Repaints the given widget instances. */
    suspend fun render(context: Context, ids: IntArray, showSpinner: Boolean = false) {
        val store = RateStore(context)
        val state = WidgetState(
            lang = store.currentLanguage(),
            metal = store.currentMetal(),
            today = store.currentHistory().firstOrNull(),
            history = store.currentHistory(),
            lastSuccessAt = store.lastSuccessAt.first(),
            showSpinner = showSpinner,
            stale = store.isStale(),
            transparency = store.currentTransparency(),
        )
        val (chartWidthPx, chartHeightPx) = chartSizePx(context, ids)
        val views = build(context, state, chartWidthPx, chartHeightPx)
        AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
    }

    /**
     * Builds the RemoteViews tree.
     *
     * Exposed internally so the rendered tree can be asserted on in instrumented tests.
     */
    internal fun build(
        context: Context,
        state: WidgetState,
        chartWidthPx: Int,
        chartHeightPx: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_gold_rate)
        val localized = context.withLanguage(state.lang)

        val onSurface = ContextCompat.getColor(context, R.color.widget_on_surface)
        val onSurfaceVariant = ContextCompat.getColor(context, R.color.widget_on_surface_variant)
        val grid = ContextCompat.getColor(context, R.color.widget_chart_grid)

        val accentRes = if (state.metal == Metal.GOLD) {
            R.color.widget_accent_gold
        } else {
            R.color.widget_accent_silver
        }
        val accent = ContextCompat.getColor(context, accentRes)

        // ---- The surface the widget is drawn on -------------------------------------------
        // The launcher draws the widget, and RemoteViews can only call methods that take an int,
        // so the transparency is a drawable that is swapped rather than an alpha that is
        // applied at runtime.
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            surfaceFor(state.transparency),
        )

        // Every string is set from the localised context. The launcher resolves the strings that
        // are baked into the layout against the *device* locale, so they would ignore the
        // language the user picked in the settings screen.
        views.setTextViewText(R.id.widget_rate_label, localized.getString(R.string.widget_rate_label))
        views.setTextViewText(R.id.widget_toggle_gold, localized.getString(R.string.toggle_gold))
        views.setTextViewText(R.id.widget_toggle_silver, localized.getString(R.string.toggle_silver))
        views.setTextViewText(R.id.widget_refresh, localized.getString(R.string.action_refresh))
        views.setContentDescription(
            R.id.widget_chart,
            localized.getString(R.string.widget_chart_description),
        )

        // ---- GOLD / SILVER switch ----------------------------------------------------
        val goldSelected = state.metal == Metal.GOLD
        val selectedBackground = if (goldSelected) {
            R.drawable.widget_toggle_selected_gold
        } else {
            R.drawable.widget_toggle_selected_silver
        }
        views.setInt(
            R.id.widget_toggle_gold,
            "setBackgroundResource",
            if (goldSelected) selectedBackground else R.drawable.widget_toggle_unselected,
        )
        views.setInt(
            R.id.widget_toggle_silver,
            "setBackgroundResource",
            if (goldSelected) R.drawable.widget_toggle_unselected else selectedBackground,
        )
        views.setTextColor(
            R.id.widget_toggle_gold,
            if (goldSelected) 0xFFFFFFFF.toInt() else onSurfaceVariant,
        )
        views.setTextColor(
            R.id.widget_toggle_silver,
            if (goldSelected) onSurfaceVariant else 0xFFFFFFFF.toInt(),
        )
        views.setOnClickPendingIntent(R.id.widget_toggle_gold, metalPendingIntent(context, Metal.GOLD))
        views.setOnClickPendingIntent(
            R.id.widget_toggle_silver,
            metalPendingIntent(context, Metal.SILVER),
        )

        // ---- Chart -------------------------------------------------------------------
        // The last few calendar days, each in its own column. A day the publisher did not
        // publish keeps its column but carries no point, so the chart is a real time axis.
        val window = RateSeries.tolaWindow(state.history, state.metal, CHART_DAYS)
        val points = window.map { point ->
            if (point.value.isNaN()) null else point.value
        }
        val known = points.filterNotNull()
        val chart = RateChart.render(
            context = context,
            values = points,
            widthPx = chartWidthPx,
            heightPx = chartHeightPx,
            lineColor = accent,
            fillColor = accent,
            gridColor = grid,
        )
        views.setImageViewBitmap(R.id.widget_chart, chart)

        // ---- The chart's labels ------------------------------------------------------
        // They are regular views, not part of the bitmap: the launcher reports the widget's
        // minimum size rather than the size it really gave the widget, so the drawing is
        // always stretched to fit - and text inside the bitmap would be stretched with it.
        views.setTextViewText(
            R.id.widget_axis_currency,
            localized.getString(R.string.currency_symbol),
        )
        views.setTextViewText(
            R.id.widget_axis_high,
            if (known.isEmpty()) "" else Format.axisLabel(known.max(), state.lang),
        )
        views.setTextViewText(
            R.id.widget_axis_low,
            if (known.isEmpty()) "" else Format.axisLabel(known.min(), state.lang),
        )

        // One column per calendar day, the same order as the drawing. A day without a
        // publication still gets its label, so the gap is readable.
        DAY_LABEL_IDS.forEachIndexed { index, viewId ->
            val point = window.getOrNull(index)
            if (point == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, dayLabel(point.date, state.lang))
            }
        }

        // ---- Today's rate ------------------------------------------------------------
        val quote = RateSeries.tolaQuote(state.today, state.metal)
        if (quote == null) {
            views.setTextViewText(R.id.widget_rate_symbol, "")
            views.setTextViewText(R.id.widget_rate_value, localized.getString(R.string.widget_no_data))
            views.setTextViewText(R.id.widget_rate_unit, "")
            views.setTextViewText(R.id.widget_rate_change, "")
            views.setTextViewText(R.id.widget_rate_date, "")
        } else {
            val change = quote.change
            val arrow = when {
                change > 0 -> "▲ "
                change < 0 -> "▼ "
                else -> ""
            }
            val changeColor = when {
                change > 0 -> ContextCompat.getColor(context, R.color.widget_up)
                change < 0 -> ContextCompat.getColor(context, R.color.widget_down)
                else -> onSurfaceVariant
            }

            views.setTextViewText(
                R.id.widget_rate_symbol,
                localized.getString(R.string.currency_symbol),
            )
            views.setTextViewText(R.id.widget_rate_value, Format.amount(quote.today, state.lang))
            views.setTextViewText(R.id.widget_rate_unit, localized.getString(R.string.rate_unit_tola))
            views.setTextViewText(
                R.id.widget_rate_change,
                buildString {
                    append(Format.signedPercent(change, quote.yesterday, state.lang))
                    append(' ')
                    append(arrow)
                    append('(')
                    append(Format.signed(change, state.lang))
                    append(')')
                },
            )
            views.setTextColor(R.id.widget_rate_change, changeColor)

            // The publisher updates once a day in the late morning, so the rate on the widget
            // is often yesterday's. The day it belongs to is printed next to the change.
            val day = state.today?.date?.let { date ->
                runCatching { java.time.LocalDate.parse(date) }.getOrNull()
            }
            views.setTextViewText(
                R.id.widget_rate_date,
                day?.let { RateDates.label(it, state.lang) } ?: "",
            )
        }

        // ---- Footer ------------------------------------------------------------------
        views.setTextViewText(
            R.id.widget_updated,
            localized.getString(
                R.string.widget_updated_at,
                relativeTime(localized, state.lastSuccessAt),
            ),
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_root, settingsPendingIntent(context))

        return views
    }

    /** The glass surface that belongs to a transparency level. */
    private fun surfaceFor(transparency: Transparency): Int = when (transparency) {
        Transparency.SUBTLE -> R.drawable.widget_glass_subtle
        Transparency.BALANCED -> R.drawable.widget_glass_balanced
        Transparency.SOLID -> R.drawable.widget_glass_solid
    }

    /**
     * The calendar day of an ISO date, as it is written on the chart's X axis.
     *
     * Nepali mode uses Devanagari digits, so the axis matches the rest of the widget.
     */
    private fun dayLabel(date: String, lang: AppLang): String {
        val day = runCatching {
            java.time.LocalDate.parse(date).dayOfMonth.toString()
        }.getOrNull() ?: return ""
        return if (lang == AppLang.NE) Format.toDevanagari(day) else day
    }

    /** A short "how long ago" phrase for the footer. */
    private fun relativeTime(context: Context, lastSuccessAt: Long): String {
        if (lastSuccessAt <= 0L) return context.getString(R.string.time_unknown)
        val minutes = Duration.between(Instant.ofEpochMilli(lastSuccessAt), Instant.now()).toMinutes()
        return when {
            minutes < 1 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes, minutes.toInt())
            minutes < 60 * 24 -> context.getString(R.string.time_hours, (minutes / 60).toInt())
            else -> {
                val date = Instant.ofEpochMilli(lastSuccessAt)
                    .atZone(ZoneId.of("Asia/Kathmandu"))
                    .toLocalDate()
                DateTimeFormatter.ISO_LOCAL_DATE.format(date)
            }
        }
    }

    /**
     * The pixel size the chart should be drawn at.
     *
     * The launcher reports the widget's current size through the minimum width and height
     * options, which is what the chart is drawn for. The `OPTION_APPWIDGET_WIDTH` constant
     * exists only in the system API, so it cannot be used from an app.
     */
    private fun chartSizePx(context: Context, ids: IntArray): Pair<Int, Int> {
        val (widthDp, heightDp) = widgetSizeDp(context, ids)
        val (chartWidthDp, chartHeightDp) = chartSizeDp(widthDp, heightDp)
        val density = context.resources.displayMetrics.density
        return (chartWidthDp * density).roundToInt().coerceAtLeast(1) to
            (chartHeightDp * density).roundToInt().coerceAtLeast(1)
    }

    /**
     * The size the launcher granted the widget, in dp.
     *
     * `OPTION_APPWIDGET_MIN_WIDTH` / `MIN_HEIGHT` are what an app can read; the exact width and
     * height constants exist only in the system API. The debug harness uses this too, so the
     * preview is drawn at the size the launcher really gives the widget.
     */
    internal fun widgetSizeDp(context: Context, ids: IntArray): Pair<Int, Int> {
        val manager = AppWidgetManager.getInstance(context)
        var widthDp = DEFAULT_WIDTH_DP
        var heightDp = DEFAULT_HEIGHT_DP

        ids.firstOrNull()?.let { id ->
            val options = manager.getAppWidgetOptions(id)
            widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP)
            heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)
        }

        return widthDp to heightDp
    }

    /** The chart size for a widget of [widthDp] x [heightDp], in dp. */
    internal fun chartSizeDp(widthDp: Int, heightDp: Int): Pair<Int, Int> {
        val chartWidthDp = (widthDp - HORIZONTAL_PADDING_DP).coerceIn(MIN_CHART_DP, 360)
        val chartHeightDp = (heightDp - CHROME_DP).coerceIn(MIN_CHART_DP, 200)
        return chartWidthDp to chartHeightDp
    }

    private fun metalPendingIntent(context: Context, metal: Metal): PendingIntent {
        val intent = Intent(context, ToggleReceiver::class.java)
            .putExtra(ToggleReceiver.EXTRA_METAL, metal.code)
        return PendingIntent.getBroadcast(
            context,
            metal.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshReceiver::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun settingsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ConfigActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    const val ACTION_REFRESH = "com.goldrate.widget.action.REFRESH"

    /** The day labels, one per column of the chart. */
    private val DAY_LABEL_IDS = intArrayOf(
        R.id.widget_day_0,
        R.id.widget_day_1,
        R.id.widget_day_2,
        R.id.widget_day_3,
        R.id.widget_day_4,
        R.id.widget_day_5,
        R.id.widget_day_6,
    )

    private const val DEFAULT_WIDTH_DP = 250
    private const val DEFAULT_HEIGHT_DP = 150

    /**
     * The vertical space the layout needs for everything except the chart.
     *
     * Card padding (20 dp) + the rate row (5 dp margin, ~21 dp) + the footer (5 dp margin,
     * ~17 dp). The chart gets whatever is left.
     */
    private const val CHROME_DP = 66

    /** The horizontal padding of the widget card. */
    private const val HORIZONTAL_PADDING_DP = 20

    private const val MIN_CHART_DP = 40

    /** How many days the chart plots. */
    private const val CHART_DAYS = 7
}
