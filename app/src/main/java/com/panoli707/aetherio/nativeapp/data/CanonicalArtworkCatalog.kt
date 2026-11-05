package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

/** Human-reviewed exceptions also cover artwork OCR cannot reliably interpret (stylized logos). */
internal object CanonicalArtworkCatalog {
    val entries: Map<String, CanonicalArtwork> by lazy {
        val input = checkNotNull(javaClass.classLoader?.getResourceAsStream("canonical-artwork.json"))
        val json = input.bufferedReader().use { JSONObject(it.readText()) }
        check(json.getInt("schemaVersion") == 1)
        val rows = json.getJSONArray("titles")
        val result = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            val key = it.getString("key")
            check(Regex("TMDB:(tv|movie):[0-9]+").matches(key))
            key to requireNotNull(CanonicalArtwork.decode(it.toString()))
        } }
        check(result.distinctBy { it.first }.size == result.size)
        result.toMap()
    }
}
