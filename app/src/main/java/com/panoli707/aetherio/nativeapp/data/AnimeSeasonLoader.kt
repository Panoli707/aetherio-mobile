package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Coordinates only the seasonal state; injectable without starting the rest of the catalogue. */
internal class AnimeSeasonLoader(
    private val scope: CoroutineScope,
    private val fetch: suspend (AnimeSeason) -> List<SeasonalAnime>,
    private val update: ((AnimeSeasonState) -> AnimeSeasonState) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    private var job: Job? = null

    fun load(season: AnimeSeason = AnimeSeason.current()) {
        if (job?.isActive == true) return
        job = scope.launch {
            update { if (it.season == season) it.copy(loading = true, error = null) else AnimeSeasonState(season, loading = true) }
            try {
                val entries = fetch(season)
                ensureActive()
                update { AnimeSeasonState(season, entries, loaded = true) }
            } catch (error: CancellationException) {
                update { it.copy(loading = false) }
                throw error
            } catch (error: Exception) {
                onError(error)
                val message = (error as? AnimeCalendarException)?.userMessage()
                    ?: "No se pudo actualizar la temporada de anime. Inténtalo de nuevo."
                update { it.copy(loading = false, error = message) }
            }
        }
    }
}
