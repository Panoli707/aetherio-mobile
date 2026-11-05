package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

/** Audited transparent resources. Identity and season are explicit, never inferred from display text. */
internal object AnimeLogoCatalog {
    internal data class Entry(val animeId: String, val tmdbId: String, val type: MediaType, val season: Int?, val asset: String)

    internal val entries: List<Entry> by lazy {
        val raw = checkNotNull(AnimeLogoCatalog::class.java.classLoader?.getResourceAsStream("anime-logo-catalog.json"))
            .bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        check(json.getInt("schemaVersion") == 1)
        val rows = json.getJSONArray("logos")
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { row ->
            Entry(row.getString("aniListId"), row.getString("tmdbId"), MediaType.valueOf(row.getString("type")),
                if (row.isNull("season")) null else row.getInt("season"), row.getString("asset"))
        } }.also { list ->
            check(list.distinctBy { it.animeId }.size == list.size)
            check(list.all { it.animeId.matches(Regex("[0-9]+")) && it.tmdbId.matches(Regex("[0-9]+")) &&
                it.asset.matches(Regex("anilist_[0-9]+\\.png")) &&
                (if (it.type == MediaType.MOVIE) it.season == null else it.season in 1..1000) })
        }
    }

    fun logo(item: MediaItem): String? {
        val candidates = when (item.source) {
            CatalogSource.ANILIST -> entries.filter { it.animeId == item.id && it.type == item.type }
            CatalogSource.TMDB -> entries.filter { it.tmdbId == item.id && it.type == item.type &&
                (item.type == MediaType.MOVIE || it.season == (item.seasonHint ?: 1)) &&
                (item.animeEntryId == null || item.animeEntryId == it.animeId) }
            else -> emptyList()
        }
        // Multiple cours sharing a TMDB season need an explicit animeEntryId to choose a brand.
        return candidates.singleOrNull()?.let { "file:///android_asset/anime-logos/${it.asset}" }
    }
}
