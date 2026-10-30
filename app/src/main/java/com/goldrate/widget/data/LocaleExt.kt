package com.goldrate.widget.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Returns a context whose resources resolve against [lang] instead of the device locale.
 *
 * The app stores an explicit `language_preference`, so the user's choice must win over the
 * system language setting. `values-ne` therefore holds the Nepali strings.
 */
fun Context.withLanguage(lang: AppLang): Context {
    val locale = Locale(lang.code)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
