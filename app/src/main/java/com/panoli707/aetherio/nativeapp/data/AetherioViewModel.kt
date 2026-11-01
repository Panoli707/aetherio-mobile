package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AetherioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AetherioRepository()
    private val library = LocalLibraryStore(application)
    private val mutableState = MutableStateFlow(AppState(savedItems = library.savedItems, profileName = library.profileName))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var homeJob: Job? = null
    private var detailJob: Job? = null
    private var seasonJob: Job? = null
    private var searchJob: Job? = null

    init { loadHome() }

    fun loadHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            mutableState.update { it.copy(homeLoading = true, homeError = null) }
            try {
                val home = repository.home()
                ensureActive()
                mutableState.update { it.copy(sections = home.sections, heroes = home.heroes, homeLoading = false,
                    homeError = if (home.partial) "Algunas secciones no pudieron cargarse. Puedes reintentar sin perder el resto del catálogo." else null) }
                val heroes = repository.heroes(home.heroes)
                ensureActive()
                mutableState.update { it.copy(heroes = heroes) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(homeLoading = false, homeError = "No se pudo cargar el catálogo. Inténtalo de nuevo.") } }
        }
    }
    fun selectMedia(item: MediaItem) {
        detailJob?.cancel()
        seasonJob?.cancel()
        detailJob = viewModelScope.launch {
            mutableState.update { it.copy(selectedItem = item, detail = null, detailLoading = true, detailError = null, episodesLoading = false) }
            try {
                val detail = repository.detail(item)
                ensureActive()
                if (state.value.selectedItem?.key != item.key) return@launch
                mutableState.update { it.copy(selectedItem = detail.item, detail = detail, detailLoading = false) }
                if (item.source == CatalogSource.TMDB) detail.selectedSeason?.let(::selectSeason)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.selectedItem?.key == item.key) mutableState.update { it.copy(detailLoading = false, detailError = "No se pudo cargar la información. Inténtalo de nuevo.") }
            }
        }
    }
    fun retryDetail() {
        val current = state.value
        val detail = current.detail
        if (detail?.selectedSeason != null && current.detailError != null) selectSeason(detail.selectedSeason)
        else current.selectedItem?.let(::selectMedia)
    }
    fun selectSeason(number: Int) {
        val detail = state.value.detail ?: return
        if (detail.seasons.none { it.number == number }) return
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            mutableState.update { it.copy(detail = detail.copy(selectedSeason = number, episodes = emptyList()), episodesLoading = true, detailError = null) }
            try {
                val episodes = repository.episodes(detail.item, number)
                ensureActive()
                if (state.value.detail?.item?.key != detail.item.key || state.value.detail?.selectedSeason != number) return@launch
                mutableState.update { it.copy(detail = it.detail?.copy(episodes = episodes), episodesLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.detail?.item?.key == detail.item.key && state.value.detail?.selectedSeason == number)
                    mutableState.update { it.copy(episodesLoading = false, detailError = "No se pudieron cargar los episodios. Inténtalo de nuevo.") }
            }
        }
    }
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val trimmed = query.trim()
            mutableState.update { it.copy(searchQuery = query, searchResults = emptyList(), searchLoading = trimmed.isNotEmpty(), searchError = null) }
            if (trimmed.isEmpty()) return@launch
            delay(350)
            try {
                val results = repository.search(trimmed)
                ensureActive()
                if (state.value.searchQuery == query) mutableState.update { it.copy(searchResults = results, searchLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.searchQuery == query) mutableState.update { it.copy(searchLoading = false, searchError = "No se pudo completar la búsqueda. Inténtalo de nuevo.") }
            }
        }
    }
    fun toggleSaved(item: MediaItem) {
        viewModelScope.launch {
            val current = state.value.savedItems
            val items = if (current.any { it.key == item.key }) current.filterNot { it.key == item.key } else listOf(item) + current
            mutableState.update { it.copy(savedItems = items) }
            library.save(items)
        }
    }
    fun renameProfile(name: String) {
        val normalized = LocalLibraryStore.normalizeName(name) ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(profileName = normalized) }
            library.profileName = normalized
        }
    }
}
