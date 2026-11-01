package com.panoli707.aetherio.nativeapp.data

import android.content.Context

internal class LocalLibraryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_library", Context.MODE_PRIVATE)
    val savedItems: List<MediaItem> get() = CatalogJson.decode(preferences.getString("saved", "[]") ?: "[]")
    var profileName: String
        get() = normalizeName(preferences.getString("profile", "Usuario").orEmpty()) ?: "Usuario"
        set(value) { normalizeName(value)?.let { preferences.edit().putString("profile", it).apply() } }
    fun save(items: List<MediaItem>) { preferences.edit().putString("saved", CatalogJson.encode(items)).apply() }
    companion object {
        fun normalizeName(value: String): String? = value.trim().takeIf { it.isNotEmpty() && it.length <= 32 && it.none(Char::isISOControl) }
    }
}
