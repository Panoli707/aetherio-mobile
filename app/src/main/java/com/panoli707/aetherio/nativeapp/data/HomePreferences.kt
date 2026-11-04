package com.panoli707.aetherio.nativeapp.data

import android.content.Context

enum class HomeContentPreference { MOVIES_SERIES, ANIME, BOTH }

data class HomePreferences(
    val content: HomeContentPreference = HomeContentPreference.BOTH,
    val animeFirst: Boolean = false,
    val horizontalPosters: Boolean = false,
    val catalogOrder: List<String> = emptyList(),
    val hiddenCatalogIds: Set<String> = emptySet()
) {
    fun arranged(sections: List<CatalogSection>): List<CatalogSection> {
        val indexes = catalogOrder.withIndex().associate { it.value to it.index }
        return sections.distinctBy { it.id }.withIndex().sortedWith(
            compareBy<IndexedValue<CatalogSection>> { indexes[it.value.id] ?: Int.MAX_VALUE }
                .thenBy { if (content == HomeContentPreference.ANIME || (content == HomeContentPreference.BOTH && animeFirst)) {
                    if (it.value.id.startsWith("mal.") || it.value.id.startsWith("jikan.")) 0 else 1
                } else { if (it.value.id.startsWith("mal.") || it.value.id.startsWith("jikan.")) 1 else 0 } }
                .thenBy { it.index }
        ).map { it.value }
    }

    fun ordered(sections: List<CatalogSection>): List<CatalogSection> =
        arranged(sections).filterNot { it.id in hiddenCatalogIds }

    fun move(id: String, direction: Int, sections: List<CatalogSection>): HomePreferences {
        if (direction != -1 && direction != 1) return this
        val order = arranged(sections).map { it.id }.toMutableList()
        val index = order.indexOf(id)
        val target = index + direction
        if (index < 0 || target !in order.indices) return this
        order.add(target, order.removeAt(index))
        return copy(catalogOrder = order)
    }

    fun visibleHeroes(heroes: List<MediaItem>): List<MediaItem> = when (content) {
        HomeContentPreference.ANIME -> heroes.sortedByDescending { it.isAnime }
        HomeContentPreference.MOVIES_SERIES -> heroes.sortedBy { it.isAnime }
        HomeContentPreference.BOTH -> if (animeFirst) heroes.sortedByDescending { it.isAnime } else heroes.sortedBy { it.isAnime }
    }
}

internal class HomePreferencesStore(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences("aetherio_home_design_v1", Context.MODE_PRIVATE)
    fun load(): HomePreferences = runCatching { HomePreferences(
        content = HomeContentPreference.entries.firstOrNull { it.name == storage.getString("content", null) } ?: HomeContentPreference.BOTH,
        animeFirst = storage.getBoolean("anime_first", false),
        horizontalPosters = storage.getBoolean("horizontal_posters", false),
        catalogOrder = storage.getString("order", "").orEmpty().split('|').filter(String::isNotBlank).distinct().take(100),
        hiddenCatalogIds = storage.getStringSet("hidden", emptySet()).orEmpty().toSet()
    ) }.getOrDefault(HomePreferences())
    fun save(value: HomePreferences): Boolean = runCatching { storage.edit().putString("content", value.content.name)
        .putBoolean("anime_first", value.animeFirst).putBoolean("horizontal_posters", value.horizontalPosters)
        .putString("order", value.catalogOrder.distinct().take(100).joinToString("|"))
        .putStringSet("hidden", value.hiddenCatalogIds.toSet()).commit() }.getOrDefault(false)
}
