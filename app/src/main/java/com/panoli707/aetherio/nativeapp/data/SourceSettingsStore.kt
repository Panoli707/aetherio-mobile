package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class SourceSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_source_settings", Context.MODE_PRIVATE)

    var enabledDesktopSources: Set<String>
        get() = if (!preferences.contains("desktop")) DesktopSources.defaultIds else runCatching {
            preferences.getStringSet("desktop", emptySet()).orEmpty().intersect(DesktopSources.defaultIds)
        }.getOrDefault(emptySet())
        set(value) {
            preferences.edit().putStringSet("desktop", value.intersect(DesktopSources.defaultIds)).apply()
        }
}
