package com.goldrate.widget.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.goldrate.widget.R

/**
 * The segmented control of the settings screen.
 *
 * The widget already switches between gold and silver with a pill, so the settings screen uses
 * the same control for the language and for the metal: two equal segments inside a rounded
 * track, with the selected one on an accent coloured pill.
 *
 * The labels are set in code, never read from the layout, because the launcher resolves the
 * strings that are baked into a layout against the *device* locale. The screen picks the
 * language itself, so the control has to follow it.
 */
class SegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val optionViews = mutableListOf<TextView>()
    private var accentColor = 0
    private var onSelected: ((Int) -> Unit)? = null

    /** The index of the segment that is currently selected. */
    var selectedIndex: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        setBackgroundResource(R.drawable.ui_segment_container)
        val padding = resources.getDimensionPixelSize(R.dimen.ui_gap_small) / 2
        setPadding(padding, padding, padding, padding)
    }

    /**
     * Sets the segments.
     *
     * @param labels the segment labels, in the language the screen is showing.
     * @param accent the colour of the selected segment.
     * @param onSelected called with the new index when the user picks another segment.
     */
    fun setOptions(labels: List<String>, accent: Int, onSelected: (Int) -> Unit) {
        this.accentColor = accent
        this.onSelected = onSelected

        removeAllViews()
        optionViews.clear()

        labels.forEachIndexed { index, label ->
            val option = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                minHeight = resources.getDimensionPixelSize(R.dimen.ui_touch_target) -
                    resources.getDimensionPixelSize(R.dimen.ui_gap_small)
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = paddingHalf()
                    marginEnd = paddingHalf()
                }
                setOnClickListener { select(index, notify = true) }
            }
            optionViews.add(option)
            addView(option)
        }

        applySelection()
    }

    /** Selects a segment without telling the listener - used when the state is restored. */
    fun select(index: Int, notify: Boolean = false) {
        if (index !in optionViews.indices) return
        selectedIndex = index
        applySelection()
        if (notify) onSelected?.invoke(index)
    }

    private fun applySelection() {
        optionViews.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            option.isSelected = selected
            option.setTextColor(
                if (selected) {
                    ContextCompat.getColor(context, R.color.ui_on_accent)
                } else {
                    ContextCompat.getColor(context, R.color.ui_on_surface_variant)
                },
            )
            option.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (selected) {
                option.setBackgroundResource(R.drawable.ui_segment_selected)
                option.backgroundTintList = ColorStateList.valueOf(accentColor)
            } else {
                option.background = null
            }
        }
    }

    /** The gap between two segments: half of the track's inner padding. */
    private fun paddingHalf(): Int =
        resources.getDimensionPixelSize(R.dimen.ui_gap_small) / 2
}
