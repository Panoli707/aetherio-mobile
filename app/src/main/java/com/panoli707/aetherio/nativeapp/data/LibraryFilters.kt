package com.panoli707.aetherio.nativeapp.data

import java.text.Collator
import java.util.Locale

data class LibraryFilters(val list: String = "all", val type: String = "all", val genre: String = "all", val sort: String = "added_desc")
internal data class LibraryEntry(val item: MediaItem, val addedAt: Long, val list: String, val label: String)
internal data class LibraryFacet(val key: String, val label: String, val count: Int)
internal data class FilteredLibrary(val entries: List<LibraryEntry>, val lists: List<LibraryFacet>,
    val types: List<LibraryFacet>, val genres: List<LibraryFacet>, val filters: LibraryFilters)

/** Desktop libraryLogic semantics, preserving mobile's in-progress and followed-anime library. */
internal object LibraryFiltering {
    private fun genreKey(value: String) = value.trim().lowercase(Locale.ROOT)

    fun entries(state: AppState): List<LibraryEntry> {
        val entries = linkedMapOf<String, LibraryEntry>()
        fun add(item: MediaItem, time: Long, list: String, label: String) {
            val canonical = SeriesIdentity.item(item)
            val old = entries[canonical.key]
            val enriched = canonical.copy(genres = (canonical.genres + old?.item?.genres.orEmpty()).distinct())
            entries[canonical.key] = LibraryEntry(enriched, time, list, label)
        }
        state.watchProgress.filter { it.watched || it.positionMs > 0 }.sortedBy { it.updatedAt }.forEach {
            add(it.target.item, it.updatedAt, "activity", "Actividad")
        }
        state.watchingAnime.forEach { if (SeriesIdentity.item(it).key !in entries) add(it, 0, "following", "En seguimiento") }
        // Connected lists are read-only here: browsing never imports or overwrites local progress.
        state.animeTracking.connections[AnimeService.ANILIST]?.let { connection ->
            connection.entries.forEach { entry -> entry.item()?.let {
                add(it.copy(genres = listOf("Anime")), connection.updatedAt, "anilist:${entry.status?.name ?: "UNKNOWN"}", "${entry.status?.label ?: "Sin estado"} en AniList")
            } }
        }
        state.savedItems.forEachIndexed { index, item -> add(item,
            state.savedAddedAt[SeriesIdentity.item(item).key]?.takeIf { it > 0 } ?: -index.toLong(), "saved", "Mi lista") }
        return entries.values.toList()
    }

    fun filter(entries: List<LibraryEntry>, filters: LibraryFilters): FilteredLibrary {
        val list = filters.list.takeIf { it == "all" || entries.any { entry -> entry.list == it } } ?: "all"
        val base = entries.filter { list == "all" || it.list == list }
        val type = filters.type.takeIf { it == "all" || base.any { entry -> entry.item.type.path == it } } ?: "all"
        val genre = filters.genre.takeIf { it == "all" || base.any { entry -> entry.item.genres.any { name -> genreKey(name) == it } } } ?: "all"
        val normalized = filters.copy(list = list, type = type, genre = genre,
            sort = filters.sort.takeIf { it in setOf("added_desc", "added_asc", "title_asc", "title_desc") } ?: "added_desc")
        fun matchesType(entry: LibraryEntry) = type == "all" || entry.item.type.path == type
        fun matchesGenre(entry: LibraryEntry) = genre == "all" || entry.item.genres.any { genreKey(it) == genre }
        val collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }
        val titles = Comparator<LibraryEntry> { a, b ->
            fun title(entry: LibraryEntry) = entry.item.title.trim().replace(Regex("^(the|an|a|el|la|los|las|un|una)\\s+", RegexOption.IGNORE_CASE), "")
            collator.compare(title(a), title(b))
        }
        val ordering = when (normalized.sort) {
            "added_asc" -> compareBy<LibraryEntry> { it.addedAt }.then(titles)
            "title_asc" -> titles
            "title_desc" -> titles.reversed()
            else -> compareByDescending<LibraryEntry> { it.addedAt }.then(titles)
        }
        val lists = entries.groupBy { it.list }.map { (key, values) -> LibraryFacet(key, values.first().label, values.size) }
            .sortedBy { when (it.key) { "saved" -> 0; "activity" -> 1; else -> 2 } }
        val typeBase = base.filter(::matchesGenre)
        val genreBase = base.filter(::matchesType)
        val types = MediaType.entries.filter { candidate -> base.any { it.item.type == candidate } }.map { candidate ->
            LibraryFacet(candidate.path, if (candidate == MediaType.MOVIE) "Películas" else "Series y anime", typeBase.count { it.item.type == candidate })
        }
        val genres = base.flatMap { it.item.genres }.filter(String::isNotBlank).associateBy(::genreKey).map { (key, label) ->
            LibraryFacet(key, label, genreBase.count { entry -> entry.item.genres.any { genreKey(it) == key } })
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        return FilteredLibrary(base.filter { matchesType(it) && matchesGenre(it) }.sortedWith(ordering),
            listOf(LibraryFacet("all", "Todo", entries.size)) + lists,
            listOf(LibraryFacet("all", "Todos", typeBase.size)) + types,
            listOf(LibraryFacet("all", "Todos los géneros", genreBase.size)) + genres, normalized)
    }
}
