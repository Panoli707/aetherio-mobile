package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class LocalLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_library", Context.MODE_PRIVATE)
    val savedItems: List<MediaItem> get() = CatalogJson.decode(preferences.getString("saved", "[]") ?: "[]")
    var profileName: String
        get() = normalizeName(preferences.getString("profile", "Usuario").orEmpty()) ?: "Usuario"
        set(value) { normalizeName(value)?.let { preferences.edit().putString("profile", it).apply() } }
    fun save(items: List<MediaItem>) {
        val edit = preferences.edit()
        if (!preferences.contains("saved_before_series_v1")) edit.putString("saved_before_series_v1", preferences.getString("saved", "[]"))
        edit.putString("saved", CatalogJson.encode(items)).apply()
    }
    var watchingAnime: List<MediaItem>
        get() = CatalogJson.decode(preferences.getString("watchingAnime", "[]").orEmpty())
        set(value) { preferences.edit().putString("watchingAnime", CatalogJson.encode(value.distinctBy { it.key }.take(200))).apply() }
    var hiddenWatchingAnime: Set<String>
        get() = preferences.getStringSet("hiddenWatchingAnime", emptySet()).orEmpty().toSet()
        set(value) { preferences.edit().putStringSet("hiddenWatchingAnime", value.toSet()).apply() }
    companion object {
        fun normalizeName(value: String): String? = value.trim().takeIf { it.isNotEmpty() && it.length <= 32 && it.none(Char::isISOControl) }
    }
}
