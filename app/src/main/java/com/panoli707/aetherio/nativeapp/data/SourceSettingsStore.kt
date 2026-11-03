package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class SourceSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_source_settings", Context.MODE_PRIVATE)

    var enabledDesktopSources: Set<String>
        // Old "desktop" selections have no automatic/user provenance. Preserve them untouched,
        // but require a new explicit opt-in instead of duplicating the Hub's server-side fan-out.
        get() = runCatching {
            preferences.getStringSet("hub_optional_sources_v1", emptySet()).orEmpty().intersect(DesktopSources.knownIds)
        }.getOrDefault(emptySet())
        set(value) {
            preferences.edit().putStringSet("hub_optional_sources_v1", value.intersect(DesktopSources.knownIds)).apply()
        }
}
