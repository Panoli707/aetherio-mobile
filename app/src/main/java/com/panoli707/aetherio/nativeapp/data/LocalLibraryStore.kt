package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class LocalLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_library", Context.MODE_PRIVATE)
    val savedItems: List<MediaItem> get() = CatalogJson.decode(preferences.getString("saved", "[]") ?: "[]")
    val savedAddedAt: Map<String, Long> get() = runCatching {
        val json = org.json.JSONObject(preferences.getString("saved_added_at_v1", "{}").orEmpty())
        json.keys().asSequence().associate { SeriesIdentity.key(it) to json.getLong(it).coerceAtLeast(0) }
    }.getOrDefault(emptyMap())
    var profileName: String
        get() = normalizeName(preferences.getString("profile", "Usuario").orEmpty()) ?: "Usuario"
        set(value) { normalizeName(value)?.let { preferences.edit().putString("profile", it).apply() } }
    fun save(items: List<MediaItem>) {
        val edit = preferences.edit()
        val existing = savedItems.map { SeriesIdentity.item(it).key }.toSet()
        val previousTimes = savedAddedAt
        val now = System.currentTimeMillis()
        val times = org.json.JSONObject()
        items.forEach { item ->
            val key = SeriesIdentity.item(item).key
            // Old installations have no insertion dates: do not invent historical timestamps.
            times.put(key, previousTimes[key] ?: if (key in existing) 0L else now)
        }
        edit.putString("saved_added_at_v1", times.toString())
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
