package com.goldrate.widget.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateDates
import com.goldrate.widget.data.withLanguage

/** Which way the rate moved against the previous day. */
enum class RateDirection { UP, DOWN, FLAT }

/** One line of the history table. */
data class HistoryRow(
    val date: String,
    val rate: String,
    val change: String,
    val direction: RateDirection,
)

/**
 * The history of the selected metal, as a table.
 *
 * The table has three columns - the day, the rate and the change - for one metal at a time, so
 * it is never ambiguous what the numbers are. The rates are the tola rates, the same ones the
 * widget shows, formatted with the same helpers, so the two surfaces cannot disagree.
 */
object HistoryTable {

    /** How the three columns share the width. The heading and every row use the same weights. */
    private const val WEIGHT_DATE = 1.0f
    private const val WEIGHT_RATE = 1.1f
    private const val WEIGHT_CHANGE = 0.9f

    /**
     * The rows of the table, newest day first.
     *
     * Pure on purpose: no Android types, so it can be unit tested on the JVM.
     */
    fun rows(days: List<DayRates>, metal: Metal, lang: AppLang): List<HistoryRow> =
        days.map { day ->
            val quote = day.quotes.firstOrNull { metal.matches(it) }
            val change = quote?.change ?: Double.NaN
            HistoryRow(
                date = dateLabel(day.date, lang),
                rate = quote?.let { Format.amount(it.today, lang) } ?: Format.EM_DASH,
                change = Format.signed(change, lang),
                direction = when {
                    change.isNaN() -> RateDirection.FLAT
                    change > 0 -> RateDirection.UP
                    change < 0 -> RateDirection.DOWN
                    else -> RateDirection.FLAT
                },
            )
        }

    /** The short form of an ISO date, in the language of the screen. */
    private fun dateLabel(date: String, lang: AppLang): String {
        val day = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
        return if (day == null) date else RateDates.label(day, lang)
    }

    /**
     * Builds the table: the heading, then one row per day.
     *
     * The heading and the rows use the same column weights, so the numbers line up in a
     * straight column however long the amounts are.
     */
    fun build(context: Context, rows: List<HistoryRow>, lang: AppLang): View {
        val localized = context.withLanguage(lang)
        val table = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        table.addView(
            headingRow(
                context,
                listOf(
                    localized.getString(R.string.config_table_date),
                    localized.getString(R.string.config_table_rate),
                    localized.getString(R.string.config_table_change),
                ),
            ),
        )

        rows.forEachIndexed { index, row ->
            if (index > 0) table.addView(divider(context))
            table.addView(
                dataRow(
                    context = context,
                    date = row.date,
                    rate = row.rate,
                    change = row.change,
                    direction = row.direction,
                    highlighted = index == 0,
                ),
            )
        }

        return table
    }

    /** The band that holds the column headings. */
    private fun headingRow(context: Context, labels: List<String>): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.ui_table_header)
            val padding = context.resources.getDimensionPixelSize(R.dimen.ui_gap_small)
            setPadding(padding * 2, padding, padding * 2, padding)
        }
        labels.forEachIndexed { index, label ->
            row.addView(
                cell(
                    context = context,
                    text = label,
                    weight = weightFor(index),
                    textSizeSp = 10f,
                    color = ContextCompat.getColor(context, R.color.ui_on_surface_variant),
                    bold = true,
                    gravity = if (index == 0) Gravity.START else Gravity.END,
                    letterSpacing = 0.14f,
                ),
            )
        }
        return row
    }

    /** One day: the date, the rate and the change. */
    private fun dataRow(
        context: Context,
        date: String,
        rate: String,
        change: String,
        direction: RateDirection,
        highlighted: Boolean,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (highlighted) setBackgroundResource(R.drawable.ui_row_highlight)
            val padding = context.resources.getDimensionPixelSize(R.dimen.ui_gap_small)
            setPadding(padding * 2, padding + 2, padding * 2, padding + 2)
        }

        val onSurface = ContextCompat.getColor(context, R.color.ui_on_surface)
        val onVariant = ContextCompat.getColor(context, R.color.ui_on_surface_variant)

        row.addView(
            cell(
                context = context,
                text = date,
                weight = WEIGHT_DATE,
                textSizeSp = 14f,
                color = onVariant,
                bold = false,
                gravity = Gravity.START,
            ),
        )
        row.addView(
            cell(
                context = context,
                text = rate,
                weight = WEIGHT_RATE,
                textSizeSp = 13f,
                color = onSurface,
                bold = true,
                gravity = Gravity.END,
                tabular = true,
            ),
        )
        row.addView(
            cell(
                context = context,
                text = change,
                weight = WEIGHT_CHANGE,
                textSizeSp = 13f,
                color = changeColor(context, direction),
                bold = true,
                gravity = Gravity.END,
                tabular = true,
            ),
        )
        return row
    }

    /** The hairline between two rows. */
    private fun divider(context: Context): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.ui_outline_height),
            )
            setBackgroundResource(R.drawable.ui_divider)
        }

    /** One cell of a row. */
    private fun cell(
        context: Context,
        text: String,
        weight: Float,
        textSizeSp: Float,
        color: Int,
        bold: Boolean,
        gravity: Int,
        tabular: Boolean = false,
        letterSpacing: Float = 0f,
    ): TextView =
        TextView(context).apply {
            this.text = text
            this.gravity = gravity
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.letterSpacing = letterSpacing
            if (tabular) fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }

    /** The colour of the change: green when the rate rose, red when it fell. */
    private fun changeColor(context: Context, direction: RateDirection): Int =
        ContextCompat.getColor(
            context,
            when (direction) {
                RateDirection.UP -> R.color.widget_up
                RateDirection.DOWN -> R.color.widget_down
                RateDirection.FLAT -> R.color.ui_on_surface_variant
            },
        )

    private fun weightFor(index: Int): Float =
        when (index) {
            0 -> WEIGHT_DATE
            1 -> WEIGHT_RATE
            else -> WEIGHT_CHANGE
        }
}
