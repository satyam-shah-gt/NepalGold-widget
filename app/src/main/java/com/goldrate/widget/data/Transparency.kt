package com.goldrate.widget.data

/**
 * How much of the wallpaper shows through the widget's glass surface.
 *
 * The launcher draws the widget, and `RemoteViews` can only call methods that take an int, so the
 * level cannot be an alpha that is applied at runtime: every level is its own drawable and the
 * renderer swaps the background. These codes are what the store keeps.
 */
enum class Transparency(val code: String) {
    /** The most transparent level: the most wallpaper shows through. */
    SUBTLE("subtle"),

    /** The level the widget ships with. */
    BALANCED("balanced"),

    /** The most solid level: the least wallpaper shows through. */
    SOLID("solid");

    companion object {
        /** The level that is used when the user has not picked one. */
        val DEFAULT = BALANCED

        fun from(code: String?): Transparency =
            entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
