package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class LibraryBrowserStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_library_browser_v1", Context.MODE_PRIVATE)
    fun filters() = LibraryFilters(preferences.getString("list", "all").orEmpty(), preferences.getString("type", "all").orEmpty(),
        preferences.getString("genre", "all").orEmpty(), preferences.getString("sort", "added_desc").orEmpty())
    fun selection() = DiscoverSelection(preferences.getString("discover_type", "movie").orEmpty(),
        preferences.getString("discover_catalog", "tmdb:movie:popular").orEmpty(), preferences.getString("discover_genre", "").orEmpty())
    fun save(filters: LibraryFilters) {
        preferences.edit().putString("list", filters.list).putString("type", filters.type)
            .putString("genre", filters.genre).putString("sort", filters.sort).apply()
    }
    fun save(selection: DiscoverSelection) {
        preferences.edit().putString("discover_type", selection.type).putString("discover_catalog", selection.catalog)
            .putString("discover_genre", selection.genre).apply()
    }
}
