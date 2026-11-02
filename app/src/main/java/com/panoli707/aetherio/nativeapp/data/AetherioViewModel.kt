package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class AetherioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AetherioRepository()
    private val library = LocalLibraryStore(application)
    private val history = WatchHistoryRepository.get(application)
    private val sourceRepository = SourceRepository()
    private val desktopSourceRepository = DesktopSourceRepository(sourceRepository)
    private val sourceSettings = SourceSettingsStore(application)
    private val addonStore = AddonStore(application)
    private val mutableState = MutableStateFlow(AppState(savedItems = library.savedItems, profileName = library.profileName,
        enabledDesktopSources = sourceSettings.enabledDesktopSources))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var homeJob: Job? = null
    private var detailJob: Job? = null
    private var seasonJob: Job? = null
    private var searchJob: Job? = null
    private var sourceJob: Job? = null
    private var addonRestoreJob: Job? = null
    private var playbackQuery: SourceQuery? = null

    fun rememberPlaybackQuery(fromSources: Boolean) {
        playbackQuery = state.value.sourceQuery.takeIf { fromSources }
    }

    fun reopenPlaybackSources() {
        playbackQuery?.let { requestSources(it.item, it.season, it.episode) }
    }

    fun resume(progress: WatchProgress) {
        val target = progress.target
        requestSources(target.item, target.season, target.episode)
    }

    fun toggleWatched(item: MediaItem, episode: EpisodeItem) {
        val target = WatchTarget(item, episode.season, episode.number, episode.title, episode.imageUrl)
        history.setWatched(target, state.value.watchProgress.none { it.target.key == target.key && it.watched })
    }

    fun playbackTarget(animeSource: Boolean): WatchTarget? {
        val query = state.value.sourceQuery ?: return null
        val episode = state.value.detail?.takeIf { it.item.key == query.item.key }?.episodes
            ?.firstOrNull { it.season == query.season && it.number == query.episode }
        return WatchTarget(query.item.copy(isAnime = query.item.isAnime || animeSource), query.season, query.episode,
            episode?.title.orEmpty(), episode?.imageUrl).takeIf(WatchProgressRules::valid)
    }

    init {
        viewModelScope.launch { history.entries.collect { entries -> mutableState.update { it.copy(watchProgress = entries) } } }
        loadHome()
        addonRestoreJob = viewModelScope.launch {
            try {
                val addons = withContext(Dispatchers.IO) { addonStore.load() }
                mutableState.update { it.copy(addons = addons, addonsLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(addonsLoading = false, addonError = "No se pudieron abrir los proveedores guardados. Vuelve a configurarlos.") }
            }
        }
    }

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
        dismissSources()
        detailJob?.cancel()
        seasonJob?.cancel()
        detailJob = viewModelScope.launch {
            mutableState.update { it.copy(selectedItem = item, detail = null, detailLoading = true, detailError = null, episodesLoading = false) }
            try {
                val fetched = repository.detail(item)
                val recentSeason = WatchProgressRules.latest(history.load(), item)?.target?.season
                val detail = if (recentSeason != null && fetched.seasons.any { it.number == recentSeason })
                    fetched.copy(selectedSeason = recentSeason,
                        episodes = if (recentSeason == fetched.selectedSeason) fetched.episodes else emptyList()) else fetched
                ensureActive()
                if (state.value.selectedItem?.key != item.key) return@launch
                mutableState.update { it.copy(selectedItem = detail.item, detail = detail, detailLoading = false) }
                if (item.source == CatalogSource.TMDB || detail.selectedSeason != fetched.selectedSeason)
                    detail.selectedSeason?.let(::selectSeason)
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

    fun addAddon(url: String) = changeAddons { existing ->
        val configured = sourceRepository.configure(url)
        existing.filterNot { it.id == configured.id } + configured
    }

    fun removeAddon(id: String) = changeAddons { existing -> existing.filterNot { it.id == id } }

    fun setDesktopSourceEnabled(id: String, enabled: Boolean) {
        if (id !in DesktopSources.defaultIds) return
        val selected = if (enabled) state.value.enabledDesktopSources + id else state.value.enabledDesktopSources - id
        sourceSettings.enabledDesktopSources = selected
        mutableState.update { it.copy(enabledDesktopSources = selected, desktopSourceErrors = it.desktopSourceErrors - id) }
        state.value.sourceQuery?.let { requestSources(it.item, it.season, it.episode) }
    }

    private fun changeAddons(change: suspend (List<AddonConfig>) -> List<AddonConfig>) {
        if (state.value.addonSaving || state.value.addonsLoading) return
        mutableState.update { it.copy(addonSaving = true, addonError = null) }
        viewModelScope.launch {
            try {
                val updated = change(state.value.addons)
                withContext(Dispatchers.IO) { addonStore.save(updated) }
                ensureActive()
                mutableState.update { it.copy(addons = updated) }
                state.value.sourceQuery?.let { requestSources(it.item, it.season, it.episode) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableState.update { it.copy(addonError = "No se pudo guardar la configuración. Revisa la URL HTTPS y la compatibilidad del proveedor.") }
            } finally {
                mutableState.update { it.copy(addonSaving = false, addonOperation = it.addonOperation + 1) }
            }
        }
    }

    fun dismissSources() {
        sourceJob?.cancel()
        mutableState.update { it.copy(sourceQuery = null, sources = emptyList(), sourcesLoading = false, sourceError = null) }
    }

    fun requestSources(item: MediaItem, season: Int?, episode: Int?) {
        sourceJob?.cancel()
        val query = SourceQuery(item, season, episode)
        mutableState.update { it.copy(sourceQuery = query, sources = emptyList(), sourcesLoading = true,
            sourceError = null, desktopSourceErrors = emptySet()) }
        sourceJob = viewModelScope.launch {
            try {
                addonRestoreJob?.join()
                ensureActive()
                val desktop = DesktopSources.providers.filter { it.id in state.value.enabledDesktopSources }
                val addons = state.value.addons.filter { item.type in it.types && desktop.none { provider -> provider.manifestUrl == it.manifestUrl } }
                if (addons.isEmpty() && desktop.isEmpty()) {
                    mutableState.update { it.copy(sourcesLoading = false, sourceError = "No hay proveedores compatibles configurados para este título.") }
                    return@launch
                }
                if (item.type == MediaType.SERIES && (season == null || episode == null || season < 0 || episode < 1)) {
                    mutableState.update { it.copy(sourcesLoading = false, sourceError = "Selecciona una temporada y un episodio.") }
                    return@launch
                }
                val media = if (item.imdbId == null && item.source == CatalogSource.TMDB) {
                    try { repository.detail(item).item }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { item }
                } else item
                ensureActive()
                val imdb = media.imdbId ?: media.id.takeIf { media.source == CatalogSource.CINEMETA }
                val hasImdb = imdb != null && imdb.matches(Regex("tt[0-9]{5,12}"))
                val compatibleDesktop = desktop.filter { it.manifestUrl == null || hasImdb }
                if (!hasImdb && compatibleDesktop.isEmpty()) {
                    mutableState.update { it.copy(sourcesLoading = false, sourceError = "No se encontró un identificador IMDb compatible para este título.") }
                    return@launch
                }
                val resolved = query.copy(item = media)
                mutableState.update { it.copy(sourceQuery = resolved) }
                var failures = 0
                var unsupported = 0
                val lookups = addons.filter { hasImdb }.map { addon -> null to suspend { sourceRepository.sources(addon, resolved) } } +
                    compatibleDesktop.map { provider -> provider.id to suspend { desktopSourceRepository.sources(provider.id, resolved) } }
                supervisorScope {
                    lookups.forEach { (desktopId, lookup) -> launch {
                        try {
                            val result = lookup()
                            ensureActive()
                            unsupported += result.unsupportedCount
                            mutableState.update { current ->
                                if (current.sourceQuery?.key != resolved.key) current else current.copy(
                                    sources = (current.sources + result.sources).distinctBy { "${it.addonId}:${it.id}" }
                                )
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            failures++
                            if (desktopId != null) mutableState.update { current ->
                                if (current.sourceQuery?.key != resolved.key) current else current.copy(
                                    desktopSourceErrors = current.desktopSourceErrors + desktopId)
                            }
                        }
                    } }
                }
                ensureActive()
                mutableState.update { current ->
                    if (current.sourceQuery?.key != resolved.key) current else current.copy(
                        sourcesLoading = false,
                        sourceError = when {
                            current.sources.isNotEmpty() && failures == 0 -> null
                            current.sources.isNotEmpty() -> "Algunos proveedores no respondieron. Las fuentes disponibles se pueden reproducir."
                            failures > 0 -> "No se pudieron consultar los proveedores. Revisa la conexión y su configuración."
                            unsupported > 0 -> "El proveedor devolvió fuentes no compatibles, como torrents, DRM o sesiones privadas."
                            else -> "Los proveedores no devolvieron fuentes para este título o episodio."
                        }
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.sourceQuery?.key == query.key) mutableState.update {
                    it.copy(sourcesLoading = false, sourceError = "No se pudo identificar el título o consultar sus fuentes. Inténtalo de nuevo.")
                }
            }
        }
    }
}
