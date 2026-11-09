package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

internal data class SeriesPart(val animeId: String, val tmdbId: String, val tmdbSeason: Int, val offset: Int, val season: Int)

/** Explicit catalog IDs and episode offsets. Titles are never used to merge identities. */
internal class SeriesIndex(rows: List<SeriesPart>, imdbIds: Map<String, String> = emptyMap(), private val legacyCombined: Set<String> = emptySet()) {
    private val imdb = imdbIds.filter { (id, value) -> id.matches(Regex("[0-9]+")) && value.matches(Regex("tt[0-9]{5,12}")) }
    fun imdbId(item: MediaItem): String? = if (item.source == CatalogSource.TMDB && item.type == MediaType.SERIES) imdb[item.id] else null
    fun knownImdbSeasons(item: MediaItem): Boolean = item.source == CatalogSource.TMDB && item.type == MediaType.SERIES && item.id in legacyCombined
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
        item.copy(id = part.tmdbId, source = CatalogSource.TMDB, seasonHint = part.season, animeEntryId = part.animeId, seriesNumbering = true, isAnime = true,
            imdbId = item.imdbId ?: imdb[part.tmdbId])
    } ?: item
    fun baseOffset(parts: List<SeriesPart>, season: Int): Int = parts.filter { it.season == season }.minOfOrNull { it.offset } ?: 0
    fun target(target: WatchTarget): WatchTarget {
        val item = target.item
        val part = part(item)
        if (part != null && target.season == 1 && target.episode != null) {
            val all = groups[part.tmdbId].orEmpty()
            return target.copy(item = canonical(item).copy(imdbNumbering = part.tmdbId in legacyCombined),
                season = part.season, episode = target.episode + part.offset - baseOffset(all, part.season))
        }
        val all = parts(item)
        if (item.imdbNumbering) return target
        if (item.seriesNumbering && item.id in legacyCombined && target.season != null && target.episode != null) {
            // Before this correction, the combined TMDB season was labelled as series numbering.
            // Convert exactly once; a pre-existing separate T2 remains T2, never a synthetic T1002.
            val part = all.filter { it.tmdbSeason == target.season && target.episode > it.offset }.maxByOrNull { it.offset }
            return if (part != null) target.copy(item = item.copy(imdbNumbering = true), season = part.season,
                episode = target.episode - baseOffset(all, part.season)) else target.copy(item = item.copy(imdbNumbering = true))
        }
        if (all.isEmpty() || item.seriesNumbering || target.episode == null || target.season == null) return target
        val matching = all.filter { it.tmdbSeason == target.season && target.episode > it.offset }.maxByOrNull { it.offset }
            ?: return if (target.season > 0 && all.none { it.tmdbSeason == target.season })
                target.copy(item = item.copy(seriesNumbering = true, isAnime = true),
                    season = if (all.any { it.season == target.season }) target.season + 1000 else target.season) else target
        return target.copy(item = item.copy(seriesNumbering = true, isAnime = true, imdbNumbering = item.id in legacyCombined), season = matching.season,
            episode = target.episode - baseOffset(all, matching.season))
    }
}

internal object SeriesIdentity {
    private val navigationAliases: Map<String, String> by lazy {
        val raw = SeriesIdentity::class.java.classLoader?.getResourceAsStream("series-navigation.json")
            ?.bufferedReader()?.use { it.readText() } ?: error("Faltan las equivalencias de navegación")
        val aliases = JSONObject(raw).getJSONObject("aliases")
        aliases.keys().asSequence().associateWith { aliases.getString(it) }
    }
    val index: SeriesIndex by lazy {
        val raw = SeriesIdentity::class.java.classLoader?.getResourceAsStream("series-mappings.json")
            ?.bufferedReader()?.use { it.readText() } ?: error("Falta la tabla de equivalencias de series")
        val document = JSONObject(raw)
        val rows = document.getJSONArray("rows")
        val imdb = document.optJSONObject("imdb") ?: JSONObject()
        val legacy = document.optJSONArray("legacyCombined")
        SeriesIndex((0 until rows.length()).map { i -> rows.getJSONArray(i).let {
            SeriesPart(it.getInt(0).toString(), it.getInt(1).toString(), it.getInt(2), it.getInt(3), it.getInt(4))
        } }, imdb.keys().asSequence().associateWith { imdb.getString(it) },
            (0 until (legacy?.length() ?: 0)).mapNotNull { legacy?.optString(it) }.toSet())
    }
    fun item(item: MediaItem): MediaItem = index.canonical(item).let { canonical ->
        if (index.parts(canonical).isNotEmpty()) canonical.copy(seriesNumbering = true, isAnime = true,
            imdbId = canonical.imdbId ?: index.imdbId(canonical)) else canonical
    }
    fun items(items: List<MediaItem>): List<MediaItem> = items.map(::item).distinctBy { it.key }
    /** A delivery may open its series without claiming an unverified episode offset. */
    fun detailItem(selected: MediaItem): MediaItem {
        val canonical = item(selected)
        if (canonical.source != CatalogSource.ANILIST || canonical.type != MediaType.SERIES) return canonical
        val alias = navigationAliases[selected.id] ?: return canonical
        val destination = item(selected.copy(id = alias))
        return if (destination.source == CatalogSource.TMDB) destination else canonical
    }
    fun key(raw: String): String {
        val match = Regex("ANILIST:tv:([0-9]+)").matchEntire(raw) ?: return raw
        return item(MediaItem(match.groupValues[1], MediaType.SERIES, "Serie", CatalogSource.ANILIST)).key
    }
    fun progress(entries: List<WatchProgress>): List<WatchProgress> = entries.sortedByDescending { it.updatedAt }
        .map { entry -> entry.copy(target = index.target(entry.target)) }.distinctBy { it.target.key }
    fun externalTarget(target: WatchTarget): WatchTarget {
        if (!target.item.seriesNumbering || target.item.imdbNumbering || index.knownImdbSeasons(target.item) || target.season == null || target.episode == null) return target
        val parts = index.parts(target.item)
        if (parts.isEmpty()) return target
        val group = parts.filter { it.season == target.season }
        if (group.isNotEmpty()) return target.copy(season = group.first().tmdbSeason,
            episode = target.episode + group.minOf { it.offset })
        return if (target.season >= 1000) target.copy(season = target.season - 1000) else target
    }
}
