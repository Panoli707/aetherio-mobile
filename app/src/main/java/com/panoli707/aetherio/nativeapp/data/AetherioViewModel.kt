package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panoli707.aetherio.nativeapp.BuildConfig
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
    private val animeRepository = AnimeSeasonRepository()
    private val repository = AetherioRepository(animeRepository = animeRepository)
    private val library = LocalLibraryStore(application)
    private val history = WatchHistoryRepository.get(application)
    private val sourceRepository = SourceRepository()
    private val hubRepository = SourceRepository(requestTimeoutMs = 45_000, attempts = 2)
    private val providerDefaults = ProviderDefaultsStore(application)
    private val desktopSourceRepository = DesktopSourceRepository(sourceRepository)
    private val sourceSettings = SourceSettingsStore(application)
    private val addonStore = AddonStore(application)
    private val mutableState = MutableStateFlow(AppState(savedItems = SeriesIdentity.items(library.savedItems), profileName = library.profileName,
        enabledDesktopSources = sourceSettings.enabledDesktopSources))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var homeJob: Job? = null
    private val detailLoader = DetailLoader(viewModelScope, repository::detail, repository::seasonContent,
        { item -> WatchProgressRules.latest(history.load(), item)?.target?.season }, { state.value },
        { change -> mutableState.update(change) })
    private var searchJob: Job? = null
    private var sourceJob: Job? = null
    private var addonRestoreJob: Job? = null
    private var playbackQuery: SourceQuery? = null
    private val animeSeasonLoader = AnimeSeasonLoader(viewModelScope, { animeRepository.season(it) },
        { change -> mutableState.update { it.copy(animeSeason = change(it.animeSeason)) } },
        { error -> if (BuildConfig.DEBUG) Log.w("AnimeCalendar", (error as? AnimeCalendarException)?.diagnostic ?: "kind=UNKNOWN") })
    private var addonRestoreFailed = false

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

    fun markWatched(item: MediaItem, episode: EpisodeItem, scope: EpisodeWatchScope, watched: Boolean) {
        val detail = state.value.detail?.takeIf { it.item.key == item.key } ?: return
        if (state.value.episodesLoading) return
        val episodes = EpisodeWatchRules.targets(detail.episodes, episode, scope)
        history.setWatched(episodes.map { WatchTarget(item, it.season, it.number, it.title, it.imageUrl) }, watched)
    }

    private fun watching(entries: List<WatchProgress>): List<MediaItem> {
        val hidden = library.hiddenWatchingAnime.map(SeriesIdentity::key).toSet()
        return SeriesIdentity.items(library.watchingAnime + entries.sortedByDescending { it.updatedAt }.map { it.target.item })
            .filter { it.isAnime && it.type == MediaType.SERIES && it.key !in hidden }
    }

    fun toggleWatching(selected: MediaItem) {
        val item = SeriesIdentity.item(selected)
        if (!item.isAnime || item.type != MediaType.SERIES) return
        val remove = state.value.watchingAnime.any { it.key == item.key }
        val hidden = library.hiddenWatchingAnime.map(SeriesIdentity::key).toSet()
        library.hiddenWatchingAnime = if (remove) hidden + item.key else hidden - item.key
        library.watchingAnime = if (remove) SeriesIdentity.items(library.watchingAnime).filterNot { it.key == item.key }
            else listOf(item) + SeriesIdentity.items(library.watchingAnime).filterNot { it.key == item.key }
        mutableState.update { it.copy(watchingAnime = watching(it.watchProgress)) }
    }

    fun loadAnimeSeason() {
        animeSeasonLoader.load()
    }

    fun playbackTarget(animeSource: Boolean): WatchTarget? {
        val query = state.value.sourceQuery ?: return null
        val episode = state.value.detail?.takeIf { it.item.key == query.item.key }?.episodes
            ?.firstOrNull { it.season == query.season && it.number == query.episode }
        return WatchTarget(query.item.copy(isAnime = query.item.isAnime || animeSource), query.season, query.episode,
            episode?.title.orEmpty(), episode?.imageUrl).takeIf(WatchProgressRules::valid)
    }

    init {
        viewModelScope.launch { history.entries.collect { entries -> mutableState.update { it.copy(watchProgress = entries, watchingAnime = watching(entries)) } } }
        loadHome()
        addonRestoreJob = viewModelScope.launch {
            try {
                val addons = withContext(Dispatchers.IO) {
                    providerDefaults.initialize(emptyList())
                    ProviderHub.withDefault(addonStore.load())
                }
                mutableState.update { it.copy(addons = addons, addonsLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                addonRestoreFailed = true
                mutableState.update { it.copy(addonsLoading = false, addonError = "No se pudieron abrir los proveedores guardados. Se conservan sin sobrescribir; reinicia la app para reintentar. Provider Hub sigue integrado.") }
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
    fun selectMedia(selected: MediaItem) {
        val item = SeriesIdentity.item(selected)
        dismissSources()
        detailLoader.open(item)
    }
    fun retryDetail() = detailLoader.retry()
    fun selectSeason(number: Int) = detailLoader.select(number)
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
        if (id !in DesktopSources.knownIds) return
        val selected = if (enabled) state.value.enabledDesktopSources + id else state.value.enabledDesktopSources - id
        sourceSettings.enabledDesktopSources = selected
        mutableState.update { it.copy(enabledDesktopSources = selected, desktopSourceErrors = it.desktopSourceErrors - id) }
        state.value.sourceQuery?.let { requestSources(it.item, it.season, it.episode) }
    }

    private fun changeAddons(change: suspend (List<AddonConfig>) -> List<AddonConfig>) {
        if (state.value.addonSaving || state.value.addonsLoading || addonRestoreFailed) return
        mutableState.update { it.copy(addonSaving = true, addonError = null) }
        viewModelScope.launch {
            try {
                val updated = ProviderHub.withDefault(change(state.value.addons))
                withContext(Dispatchers.IO) { addonStore.save(updated.filterNot(ProviderHub::isHub)) }
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
                val canonical = SeriesIdentity.item(item)
                val media = if (canonical.imdbId == null && canonical.source == CatalogSource.TMDB) {
                    try { repository.detail(canonical).item }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { canonical }
                } else canonical
                ensureActive()
                val imdb = media.imdbId ?: media.id.takeIf { media.source == CatalogSource.CINEMETA }
                val hasImdb = imdb != null && imdb.matches(Regex("tt[0-9]{5,12}"))
                val compatibleDesktop = desktop.filter { it.manifestUrl == null || hasImdb }
                if (!hasImdb && compatibleDesktop.isEmpty()) {
                    mutableState.update { it.copy(sourcesLoading = false,
                        sourceError = "Falta una correspondencia IMDb válida para consultar Provider Hub. No se inventará un identificador. AnimeAV1 nativo permanece como opción explícita en Ajustes.") }
                    return@launch
                }
                val resolved = query.copy(item = media)
                mutableState.update { it.copy(sourceQuery = resolved) }
                var failures = 0
                var unsupported = 0
                val lookups = addons.filter { hasImdb }.map { addon -> null to suspend {
                    (if (ProviderHub.isHub(addon)) hubRepository else sourceRepository).sources(addon, repository.providerQuery(resolved, false)) } } +
                    compatibleDesktop.map { provider -> provider.id to suspend {
                        desktopSourceRepository.sources(provider.id, repository.providerQuery(resolved, provider.id == "animeav1")) } }
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
                            failures > 0 -> "Provider Hub o un proveedor opcional no respondió. Reintenta la consulta; no se activarán otras fuentes automáticamente."
                            unsupported > 0 -> "Se rechazaron resultados con URL malformada, DRM o datos no compatibles. Recibir un resultado no garantiza reproducción."
                            else -> null // A successful empty response uses the distinct "Sin resultados" UI state.
                        }
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (state.value.sourceQuery?.key in setOf(query.key, query.copy(item = SeriesIdentity.item(item)).key)) mutableState.update {
                    it.copy(sourcesLoading = false, sourceError = "No se pudo identificar el título o consultar sus fuentes. Inténtalo de nuevo.")
                }
            }
        }
    }
}
