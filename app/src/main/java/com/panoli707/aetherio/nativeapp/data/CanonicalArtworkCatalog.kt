package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

/** Human-reviewed exceptions also cover artwork OCR cannot reliably interpret (stylized logos). */
internal object CanonicalArtworkCatalog {
    val entries: Map<String, CanonicalArtwork> by lazy { load("titles") }
    val landscapes: Map<String, CanonicalArtwork> by lazy { load("landscapes") }
    /** Artwork-only verified IDs; never changes numbering or playback identity. */
    val aliases: Map<String, String> by lazy {
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream("canonical-artwork.json"))
            .bufferedReader().use { JSONObject(it.readText()) }.optJSONObject("aliases") ?: JSONObject()
        json.keys().asSequence().associateWith { key ->
            check(Regex("ANILIST:(tv|movie):[0-9]+").matches(key))
            json.getString(key).also { check(it in entries) }
        }
    }
    private fun load(section: String): Map<String, CanonicalArtwork> {
        val input = checkNotNull(javaClass.classLoader?.getResourceAsStream("canonical-artwork.json"))
        val json = input.bufferedReader().use { JSONObject(it.readText()) }
        check(json.getInt("schemaVersion") == 1)
        val rows = json.optJSONArray(section) ?: return emptyMap()
        val result = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            val key = it.getString("key")
            check(Regex("TMDB:(tv|movie):[0-9]+").matches(key))
            key to requireNotNull(CanonicalArtwork.decode(it.toString()))
        } }
        check(result.distinctBy { it.first }.size == result.size)
        return result.toMap()
    }
}
