package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Metadata is usable before episodes; season artwork and episode numbering still commit together. */
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
                val usable = LoadTiming.start(LoadTiming.Stage.DETAIL_USABLE)
                val fetched = LoadTiming.measure(LoadTiming.Stage.DETAIL_METADATA) { fetchDetail(item) }
                val preferred = item.seasonHint ?: recentSeason(item)
                val selected = preferred?.takeIf { number -> fetched.seasons.any { it.number == number } } ?: fetched.selectedSeason
                val base = fetched.artworkBase ?: fetched.item
                val needsSeason = selected != null && (base.source == CatalogSource.TMDB || selected != fetched.selectedSeason)
                ensureActive()
                if (generation != request) return@launch
                val provisional = fetched.copy(artworkBase = base, selectedSeason = selected,
                    episodes = if (needsSeason) emptyList() else fetched.episodes)
                update { it.copy(selectedItem = base, detail = provisional, detailLoading = false,
                    episodesLoading = needsSeason, pendingSeason = selected.takeIf { needsSeason }) }
                usable.finish()
                val content = if (needsSeason) try {
                    LoadTiming.measure(LoadTiming.Stage.SEASON) { fetchSeason(base, requireNotNull(selected)) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    if (generation == request) {
                        failedSeason = selected
                        update { it.copy(episodesLoading = false, pendingSeason = null,
                            detailError = "No se pudo cargar la temporada. La ficha sigue disponible; puedes reintentar.") }
                    }
                    return@launch
                } else SeasonContent(base, fetched.episodes)
                ensureActive()
                if (generation != request) return@launch
                if (selected != null) seasons[selected] = content
                val detail = fetched.copy(item = content.item, artworkBase = base, selectedSeason = selected, episodes = content.episodes)
                update { it.copy(selectedItem = content.item, detail = detail, detailLoading = false,
                    episodesLoading = false, pendingSeason = null) }
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
                val content = LoadTiming.measure(LoadTiming.Stage.SEASON) {
                    if (retry) fetchSeason(base, number) else seasons[number] ?: fetchSeason(base, number)
                }
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
