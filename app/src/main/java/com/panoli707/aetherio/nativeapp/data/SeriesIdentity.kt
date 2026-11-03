package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

internal data class SeriesPart(val animeId: String, val tmdbId: String, val tmdbSeason: Int, val offset: Int, val season: Int)

/** Explicit catalog IDs and episode offsets. Titles are never used to merge identities. */
internal class SeriesIndex(rows: List<SeriesPart>) {
    private val ambiguousIds = rows.groupBy { it.animeId }.filterValues { parts -> parts.map { it.tmdbId }.distinct().size > 1 }.keys
    val groups: Map<String, List<SeriesPart>> = rows.groupBy { it.tmdbId }.filterValues { parts ->
        parts.all { it.animeId !in ambiguousIds && it.animeId.matches(Regex("[0-9]+")) && it.tmdbId.matches(Regex("[0-9]+")) &&
            it.season in 1..1000 && it.tmdbSeason in 1..1000 && it.offset in 0..10000 } &&
            parts.distinctBy { it.animeId }.size == parts.size &&
            parts.distinctBy { it.tmdbSeason to it.offset }.size == parts.size &&
            parts.groupBy { it.tmdbSeason }.values.all { group -> group.minOf { it.offset } == 0 } &&
            parts.groupBy { it.season }.values.all { group -> group.map { it.tmdbSeason }.distinct().size == 1 } &&
            parts.groupBy { it.tmdbSeason }.values.all { group ->
                val ordered = group.sortedBy { it.offset }.map { it.season }
                ordered.zipWithNext().all { (a, b) -> a <= b }
            }
    }.mapValues { (_, parts) -> parts.sortedWith(compareBy<SeriesPart> { it.season }.thenBy { it.offset }) }
    private val anime = groups.values.flatten().groupBy { it.animeId }.filterValues { it.size == 1 }.mapValues { it.value.single() }
    fun part(item: MediaItem): SeriesPart? = if (item.source == CatalogSource.ANILIST && item.type == MediaType.SERIES) anime[item.id] else null
    fun parts(item: MediaItem): List<SeriesPart> = if (item.type == MediaType.SERIES && item.source == CatalogSource.TMDB) groups[item.id].orEmpty() else emptyList()
    fun canonical(item: MediaItem): MediaItem = part(item)?.let { part ->
        item.copy(id = part.tmdbId, source = CatalogSource.TMDB, seasonHint = part.season, animeEntryId = part.animeId, seriesNumbering = true, isAnime = true)
    } ?: item
    fun baseOffset(parts: List<SeriesPart>, season: Int): Int = parts.filter { it.season == season }.minOfOrNull { it.offset } ?: 0
    fun target(target: WatchTarget): WatchTarget {
        val item = target.item
        val part = part(item)
        if (part != null && target.season == 1 && target.episode != null) {
            val all = groups[part.tmdbId].orEmpty()
            return target.copy(item = canonical(item), season = part.season, episode = target.episode + part.offset - baseOffset(all, part.season))
        }
        val all = parts(item)
        if (all.isEmpty() || item.seriesNumbering || target.episode == null || target.season == null) return target
        val matching = all.filter { it.tmdbSeason == target.season && target.episode > it.offset }.maxByOrNull { it.offset }
            ?: return if (target.season > 0 && all.none { it.tmdbSeason == target.season })
                target.copy(item = item.copy(seriesNumbering = true, isAnime = true),
                    season = if (all.any { it.season == target.season }) target.season + 1000 else target.season) else target
        return target.copy(item = item.copy(seriesNumbering = true, isAnime = true), season = matching.season,
            episode = target.episode - baseOffset(all, matching.season))
    }
}

internal object SeriesIdentity {
    val index: SeriesIndex by lazy {
        val raw = SeriesIdentity::class.java.classLoader?.getResourceAsStream("series-mappings.json")
            ?.bufferedReader()?.use { it.readText() } ?: error("Falta la tabla de equivalencias de series")
        val rows = JSONObject(raw).getJSONArray("rows")
        SeriesIndex((0 until rows.length()).map { i -> rows.getJSONArray(i).let {
            SeriesPart(it.getInt(0).toString(), it.getInt(1).toString(), it.getInt(2), it.getInt(3), it.getInt(4))
        } })
    }
    fun item(item: MediaItem): MediaItem = index.canonical(item).let { canonical ->
        if (index.parts(canonical).isNotEmpty()) canonical.copy(seriesNumbering = true, isAnime = true) else canonical
    }
    fun items(items: List<MediaItem>): List<MediaItem> = items.map(::item).distinctBy { it.key }
    fun key(raw: String): String {
        val match = Regex("ANILIST:tv:([0-9]+)").matchEntire(raw) ?: return raw
        return item(MediaItem(match.groupValues[1], MediaType.SERIES, "Serie", CatalogSource.ANILIST)).key
    }
    fun progress(entries: List<WatchProgress>): List<WatchProgress> = entries.sortedByDescending { it.updatedAt }
        .map { entry -> entry.copy(target = index.target(entry.target)) }.distinctBy { it.target.key }
    fun externalTarget(target: WatchTarget): WatchTarget {
        if (!target.item.seriesNumbering || target.season == null || target.episode == null) return target
        val parts = index.parts(target.item)
        if (parts.isEmpty()) return target
        val group = parts.filter { it.season == target.season }
        if (group.isNotEmpty()) return target.copy(season = group.first().tmdbSeason,
            episode = target.episode + group.minOf { it.offset })
        return if (target.season >= 1000) target.copy(season = target.season - 1000) else target
    }
}
