package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class SourceSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_source_settings", Context.MODE_PRIVATE)

    fun loadPreferences(): SourcePreferences = SourcePreferences(
        language = runCatching { SourceLanguage.valueOf(preferences.getString("language_v1", "ANY")!!) }.getOrDefault(SourceLanguage.ANY),
        quality = runCatching { SourceQuality.valueOf(preferences.getString("quality_v1", "ANY")!!) }.getOrDefault(SourceQuality.ANY),
        favoritesFirst = runCatching { preferences.getBoolean("favorites_first_v1", true) }.getOrDefault(true),
        favoriteSources = fingerprints("favorite_sources_v1"), favoriteProviders = fingerprints("favorite_providers_v1"))

    private fun fingerprints(key: String): Set<String> = runCatching {
        preferences.getStringSet(key, emptySet()).orEmpty().filter { it.matches(Regex("[a-f0-9]{64}")) }.toSet()
    }.getOrDefault(emptySet())

    fun savePreferences(value: SourcePreferences) {
        preferences.edit().putString("language_v1", value.language.name).putString("quality_v1", value.quality.name)
            .putBoolean("favorites_first_v1", value.favoritesFirst)
            .putStringSet("favorite_sources_v1", value.favoriteSources.toSet())
            .putStringSet("favorite_providers_v1", value.favoriteProviders.toSet()).apply()
    }

    var enabledDesktopSources: Set<String>
        // Enable bundled sources when no selection exists. A saved empty set is an explicit
        // opt-out and must survive updates, just like any other saved selection.
        get() = runCatching {
            preferences.getStringSet("hub_optional_sources_v1", DesktopSources.defaultIds).orEmpty().intersect(DesktopSources.knownIds)
        }.getOrDefault(emptySet())
        set(value) {
            preferences.edit().putStringSet("hub_optional_sources_v1", value.intersect(DesktopSources.knownIds)).apply()
        }
}
