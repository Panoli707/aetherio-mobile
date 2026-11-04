package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Publishes a resolved initial cover, then changes artwork only for an explicit season selection. */
internal class DetailLoader(
    private val scope: CoroutineScope,
    private val fetchDetail: suspend (MediaItem) -> MediaDetail,
    private val fetchSeason: suspend (MediaItem, Int) -> SeasonContent,
    private val recentSeason: suspend (MediaItem) -> Int?,
    private val state: () -> AppState,
    private val update: ((AppState) -> AppState) -> Unit
) {
    private var job: Job? = null
    private var generation = 0L
    private var failedSeason: Int? = null
    private val seasons = linkedMapOf<Int, SeasonContent>()

    fun open(item: MediaItem) {
        val request = ++generation
        job?.cancel(); seasons.clear(); failedSeason = null
        update { it.copy(selectedItem = item, detail = null, detailLoading = true, detailError = null,
            episodesLoading = false, pendingSeason = null) }
        job = scope.launch {
            try {
                val fetched = fetchDetail(item)
                val preferred = item.seasonHint ?: recentSeason(item)
                val selected = preferred?.takeIf { number -> fetched.seasons.any { it.number == number } } ?: fetched.selectedSeason
                val base = fetched.artworkBase ?: fetched.item
                val content = if (selected != null && (base.source == CatalogSource.TMDB || selected != fetched.selectedSeason))
                    fetchSeason(base, selected) else SeasonContent(base, fetched.episodes)
                ensureActive()
                if (generation != request) return@launch
                if (selected != null) seasons[selected] = content
                val detail = fetched.copy(item = content.item, artworkBase = base, selectedSeason = selected, episodes = content.episodes)
                update { it.copy(selectedItem = content.item, detail = detail, detailLoading = false) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == request) update { it.copy(detailLoading = false,
                    detailError = "No se pudo cargar la información. Inténtalo de nuevo.") }
            }
        }
    }

    fun select(number: Int, retry: Boolean = false) {
        val current = state()
        val detail = current.detail ?: return
        if (detail.seasons.none { it.number == number }) return
        if (!retry && (current.pendingSeason == number ||
                (current.pendingSeason == null && detail.selectedSeason == number && current.detailError == null))) return
        val request = ++generation
        job?.cancel(); failedSeason = null
        val base = detail.artworkBase ?: detail.item
        // Keep the committed cover/season together. The UI hides old episodes and disables play while pending.
        update { it.copy(episodesLoading = true, pendingSeason = number, detailError = null) }
        job = scope.launch {
            try {
                val content = if (retry) fetchSeason(base, number) else seasons[number] ?: fetchSeason(base, number)
                ensureActive()
                if (generation != request) return@launch
                if (seasons.size >= 8) seasons.remove(seasons.keys.first())
                seasons[number] = content
                update { it.copy(selectedItem = content.item,
                    detail = detail.copy(item = content.item, artworkBase = base, selectedSeason = number, episodes = content.episodes),
                    episodesLoading = false, pendingSeason = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == request) {
                    failedSeason = number
                    update { it.copy(episodesLoading = false, pendingSeason = null,
                        detailError = "No se pudo cargar la temporada. Se mantiene la anterior; puedes reintentar.") }
                }
            }
        }
    }

    fun retry() {
        val number = failedSeason
        if (number != null && state().detail != null) select(number, retry = true)
        else state().selectedItem?.let(::open)
    }
}
