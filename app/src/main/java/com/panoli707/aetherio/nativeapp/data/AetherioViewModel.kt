package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panoli707.aetherio.nativeapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    private val app = application as com.panoli707.aetherio.nativeapp.AetherioApplication
    private val profileStore = ProfileStore(application)
    private val tracking = app.animeTracking
    private val downloads = app.mediaDownloads
    private val animeRepository = AnimeSeasonRepository()
    private val repository = AetherioRepository(animeRepository = animeRepository,
        homeCache = HomeCatalogCache(java.io.File(application.cacheDir, "desktop-home-53a9276")))
    private val library = LocalLibraryStore(application)
    private val libraryBrowser = LibraryBrowserStore(application)
    private val homePreferencesStore = HomePreferencesStore(application)
    private val history = WatchHistoryRepository.get(application)
    private val sourceRepository = SourceRepository()
    private val hubRepository = SourceRepository(requestTimeoutMs = 45_000, attempts = 2)
    private val hubDirectoryPreferences = application.getSharedPreferences("aetherio_hub_directory", 0)
    private val hubDirectory = ProviderHubDirectoryRepository(
        readCache = { hubDirectoryPreferences.getString("public_directory_v1", null) },
        writeCache = { hubDirectoryPreferences.edit().putString("public_directory_v1", it).apply() },
        readBrandingCache = { hubDirectoryPreferences.getString("public_manifest_v1", null) },
        writeBrandingCache = { hubDirectoryPreferences.edit().putString("public_manifest_v1", it).apply() })
    private val providerDefaults = ProviderDefaultsStore(application)
    private val desktopSourceRepository = DesktopSourceRepository(sourceRepository)
    private val sourceSettings = SourceSettingsStore(application)
    private val addonStore = AddonStore(application)
    private val mutableState = MutableStateFlow(AppState(savedItems = SeriesIdentity.items(library.savedItems), profileName = library.profileName,
        savedAddedAt = library.savedAddedAt, mediaStates = library.mediaStates, hiddenLibraryItems = library.hiddenLibraryItems,
        syncServices = library.syncServices(),
        librarySeasonCards = library.librarySeasonCards,
        libraryFilters = libraryBrowser.filters(), discover = DiscoverState(selection = libraryBrowser.selection()),
        homePreferences = homePreferencesStore.load(), profileAppearance = profileStore.load(),
        sourcePreferences = sourceSettings.loadPreferences(),
        enabledDesktopSources = sourceSettings.enabledDesktopSources))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private val animeCalendarLoader = WatchingCalendarLoader(viewModelScope, { WatchingPage(animeRepository.calendar(it)) },
        { value -> mutableState.update { it.copy(animeCalendar = value) } })
    private val movieTvCalendarLoader = WatchingCalendarLoader(viewModelScope, repository::watchingCalendar,
        { value -> mutableState.update { it.copy(movieTvCalendar = value) } })

    fun loadWatchingCalendar(request: WatchingRequest, force: Boolean = false) {
        if (request.category == WatchingCategory.ANIME) animeCalendarLoader.load(request, force)
        else movieTvCalendarLoader.load(request, force)
    }

    fun resetMovieTvCalendar() = movieTvCalendarLoader.resetToFirstPage()
    private var homeJob: Job? = null
    private var browseJob: Job? = null
    private var discoverCatalogJob: Job? = null
    private var discoverAddonUrls: List<String>? = null
    private val discoverLoader = DiscoverLoader(viewModelScope, { catalog, page, genre ->
        if (catalog.manifestUrl == null) repository.discover(catalog, page, genre)
        else withContext(Dispatchers.IO) {
            val json = SourceProtocol.root(sourceRepository.document(DesktopDiscover.addonEndpoint(catalog, page, genre).toString()), SourceProtocol.MANIFEST_LIMIT, 1000)
            val metas = requireNotNull(json.optJSONArray("metas"))
            val type = if (catalog.type == "movie") MediaType.MOVIE else MediaType.SERIES
            // Native details/playback currently support IMDb and built-in TMDB/AniList identities only.
            val items = SeriesIdentity.items(CatalogJson.catalog(json, type, CatalogSource.CINEMETA))
            DiscoverPage(items, catalog.skipStep?.let { metas.length() >= it } == true)
        }
    }, { state.value.discover }, { change -> mutableState.update { it.copy(discover = change(it.discover)) } })

    fun setLibraryFilters(filters: LibraryFilters) {
        libraryBrowser.save(filters)
        mutableState.update { it.copy(libraryFilters = filters) }
    }

    fun loadDiscover(selection: DiscoverSelection, more: Boolean = false, force: Boolean = false) {
        val urls = state.value.addons.filterNot { ProviderHub.isHub(it) }.map { it.manifestUrl }
        if (!state.value.addonsLoading && (discoverAddonUrls != urls || force && state.value.discover.catalogsError != null)) {
            discoverAddonUrls = urls
            discoverCatalogJob?.cancel()
            mutableState.update { it.copy(discover = it.discover.copy(catalogsLoading = true, catalogsError = null,
                catalogs = it.discover.catalogs.filter { catalog -> catalog.manifestUrl == null || catalog.manifestUrl in urls })) }
            discoverCatalogJob = viewModelScope.launch {
                var failures = 0
                val catalogs = mutableListOf<DiscoverCatalog>()
                for (addon in state.value.addons.filterNot { ProviderHub.isHub(it) }) {
                    try { catalogs += withContext(Dispatchers.IO) {
                        DesktopDiscover.addonCatalogs(addon, SourceProtocol.root(sourceRepository.document(addon.manifestUrl), SourceProtocol.MANIFEST_LIMIT, 1000))
                    } }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        failures++
                        catalogs += state.value.discover.catalogs.filter { it.manifestUrl == addon.manifestUrl }
                    }
                }
                ensureActive()
                mutableState.update { it.copy(discover = it.discover.copy(catalogs = DesktopDiscover.catalogs + catalogs,
                    catalogsLoading = false, catalogsError = if (failures > 0) "No se pudieron consultar $failures extensiones. Los catálogos integrados siguen disponibles." else null)) }
                discoverLoader.load(state.value.discover.selection)
                libraryBrowser.save(state.value.discover.selection)
            }
        }
        // Do not discard a stored addon selection until its manifest has been restored.
        if (selection.catalog.isNotEmpty() && state.value.discover.page == 0 && !state.value.discover.loading &&
            selection.catalog !in state.value.discover.catalogs.map { it.key } &&
            (state.value.addonsLoading || state.value.discover.catalogsLoading)) return
        discoverLoader.load(selection, more, force)
        libraryBrowser.save(state.value.discover.selection)
    }
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
    private val animeTodayLoader = AnimeTodayLoader(viewModelScope, animeRepository::today,
        { change -> mutableState.update { it.copy(animeToday = change(it.animeToday)) } })
    private var addonRestoreFailed = false
    private var hubRefreshes = 0
    private val hubSync = HubDirectorySync(viewModelScope, ::refreshHubDirectory)

    fun startHubSync() = hubSync.start()
    fun stopHubSync() = hubSync.stop()

    fun rememberPlaybackQuery(fromSources: Boolean) {
        playbackQuery = state.value.sourceQuery.takeIf { fromSources }
    }

    fun reopenPlaybackSources() {
        playbackQuery?.let { requestSources(it.item, it.season, it.episode) }
    }

    fun setSourcePreferences(value: SourcePreferences) {
        sourceSettings.savePreferences(value)
        mutableState.update { it.copy(sourcePreferences = value) }
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
        animeTodayLoader.load()
    }

    fun playbackTarget(animeSource: Boolean): WatchTarget? {
        val query = state.value.sourceQuery ?: return null
        val episode = state.value.detail?.takeIf { it.item.key == query.item.key }?.episodes
            ?.firstOrNull { it.season == query.season && it.number == query.episode }
        return WatchTarget(query.item.copy(isAnime = query.item.isAnime || animeSource), query.season, query.episode,
            episode?.title.orEmpty(), episode?.imageUrl).takeIf(WatchProgressRules::valid)
    }

    init {
        viewModelScope.launch { tracking.state.collect { value -> mutableState.update { it.copy(animeTracking = value) } } }
        viewModelScope.launch { downloads.entries.collect { value -> mutableState.update { it.copy(downloads = value,
            downloadMessage = if (downloads.storageFailure) "No se pudo abrir el índice de descargas. Se conserva sin sobrescribir; reinicia la app para reintentar." else it.downloadMessage) } } }
        viewModelScope.launch { tracking.restore() }
        viewModelScope.launch { loadLibrarySeasonCards() }
        viewModelScope.launch { hubDirectory.state.collect { directory ->
            mutableState.update { it.copy(hubProviders = directory.providers, hubRuntimeProviders = directory.branding,
                hubDirectoryStale = directory.stale, sources = directory.decorate(it.sources)) }
        } }
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

    private suspend fun refreshHubDirectory(): HubDirectoryResult {
        hubRefreshes++
        mutableState.update { it.copy(hubDirectoryLoading = true) }
        try {
            val result = withContext(Dispatchers.IO) { hubDirectory.refresh() }
            mutableState.update { it.copy(hubProviders = result.providers, hubRuntimeProviders = result.branding, hubDirectoryStale = result.stale) }
            return result
        } finally {
            hubRefreshes--
            mutableState.update { it.copy(hubDirectoryLoading = hubRefreshes > 0) }
        }
    }

    fun loadHome() = loadHome(force = false)
    fun refreshHome() = loadHome(force = true)

    fun setHomePreferences(value: HomePreferences) {
        if (state.value.homePreferencesSaving) return
        mutableState.update { it.copy(homePreferencesSaving = true, homePreferencesError = null) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { homePreferencesStore.save(value) }
            mutableState.update { it.copy(homePreferences = if (saved) value else it.homePreferences,
                homePreferencesSaving = false,
                homePreferencesError = if (saved) null else "No se pudo guardar el diseño. Tu configuración anterior se conserva; vuelve a intentarlo.") }
        }
    }

    private fun loadHome(force: Boolean) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            mutableState.update { it.copy(homeLoading = true, homeError = null) }
            val requestedHeroes = mutableSetOf<String>()
            val enrichedHeroes = mutableMapOf<String, MediaItem>()
            try {
                val home = repository.home(force) { progress ->
                    ensureActive()
                    mutableState.update { it.copy(sections = progress.sections,
                        heroes = progress.heroes.map { hero -> enrichedHeroes[hero.key] ?: hero }) }
                    // Enrich as soon as the starter rows exist; a slow editorial row must not
                    // hold every logo/synopsis hostage. Each result is published independently.
                    if (progress.sections.take(6).none { it.loading }) {
                        val pending = progress.heroes.filter { requestedHeroes.add(it.key) }
                        if (pending.isNotEmpty()) launch {
                            repository.heroes(pending) { hero ->
                                ensureActive()
                                enrichedHeroes[hero.key] = hero
                                mutableState.update { current -> current.copy(heroes = current.heroes.map {
                                    if (it.key == hero.key) hero else it
                                }) }
                            }
                        }
                    }
                }
                ensureActive()
                mutableState.update { it.copy(sections = home.sections,
                    heroes = home.heroes.map { hero -> enrichedHeroes[hero.key] ?: hero }, homeLoading = false,
                    homeError = if (home.partial) "Algunas secciones no pudieron cargarse. Puedes reintentar sin perder el resto del catálogo." else null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(homeLoading = false, homeError = "No se pudo cargar el catálogo. Inténtalo de nuevo.") } }
        }
    }
    fun loadBrowse(id: String, refresh: Boolean = false) {
        val title = DesktopHomeCatalog.browseTitle(id) ?: return
        if (!refresh && state.value.browse.id == id && (state.value.browse.loading || state.value.browse.sections.isNotEmpty())) return
        browseJob?.cancel()
        mutableState.update { it.copy(browse = CatalogBrowseState(id, title,
            sections = it.browse.sections.takeIf { _ -> it.browse.id == id }.orEmpty(), loading = true)) }
        browseJob = viewModelScope.launch {
            try {
                val sections = repository.browse(id, refresh)
                ensureActive()
                mutableState.update { current -> if (current.browse.id != id) current else current.copy(
                    browse = current.browse.copy(sections = sections, loading = false,
                        error = if (sections.all { it.items.isEmpty() }) "No se pudo cargar este catálogo. Puedes reintentar." else null)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { current -> if (current.browse.id != id) current else current.copy(
                browse = current.browse.copy(loading = false, error = "No se pudo cargar este catálogo. Puedes reintentar.")) } }
        }
    }
    fun selectMedia(selected: MediaItem) {
        val item = SeriesIdentity.detailItem(selected)
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
            val canonical = SeriesIdentity.item(item)
            val alreadySaved = current.any { SeriesIdentity.item(it).key == canonical.key }
            val items = if (alreadySaved) current.filterNot { SeriesIdentity.item(it).key == canonical.key } else listOf(canonical) + current
            library.save(items)
            if (!alreadySaved) library.hiddenLibraryItems = library.hiddenLibraryItems - canonical.key
            mutableState.update { it.copy(savedItems = items, savedAddedAt = library.savedAddedAt, hiddenLibraryItems = library.hiddenLibraryItems) }
            if (!alreadySaved) persistMediaState(canonical, item.seasonHint, MediaListState())
        }
    }
    fun saveMediaState(item: MediaItem, season: Int?, value: MediaListState) { viewModelScope.launch { persistMediaState(item, season, value) } }

    fun setSyncService(service: AnimeService, enabled: Boolean) {
        val connected = tracking.state.value.connections[service]?.account != null
        library.setSyncService(service, enabled && connected)
        mutableState.update { it.copy(syncServices = library.syncServices()) }
    }

    fun removeMediaFromLibrary(selected: MediaItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val item = SeriesIdentity.item(selected)
            val animeIds = buildSet {
                item.animeEntryId?.toIntOrNull()?.let(::add)
                if (selected.source == CatalogSource.ANILIST) selected.id.toIntOrNull()?.let(::add)
                SeriesIdentity.index.parts(item).mapNotNull { it.animeId.toIntOrNull() }.forEach(::add)
            }
            val remoteDeletes = if (item.isAnime || animeIds.isNotEmpty()) tracking.state.value.connections.mapValues { (service, connection) ->
                if (connection.account == null || service !in state.value.syncServices) emptyList() else connection.entries.filter { entry ->
                    val linkedItem = entry.item()?.let(SeriesIdentity::item)
                    linkedItem?.key == item.key || entry.aniListId?.let { it in animeIds } == true ||
                        (service == AnimeService.ANILIST && entry.id in animeIds)
                }
            } else emptyMap()
            val deletedRemotely = mutableListOf<Pair<AnimeService, AnimeEntry>>()
            for ((service, entries) in remoteDeletes) for (entry in entries) {
                if (tracking.removeConfirmed(service, entry)) deletedRemotely += service to entry
                else {
                    deletedRemotely.asReversed().forEach { (deletedService, deletedEntry) ->
                        tracking.restoreDeletedEntry(deletedService, deletedEntry)
                    }
                    onComplete(false)
                    return@launch
                }
            }
            val saved = state.value.savedItems.filterNot { SeriesIdentity.item(it).key == item.key }
            val states = state.value.mediaStates.filterKeys { key -> key != item.key && !key.startsWith("${item.key}:season:") }
            val hidden = library.hiddenLibraryItems + item.key
            history.reset(item)
            library.mediaStates = states
            library.save(saved)
            library.hiddenLibraryItems = hidden
            library.watchingAnime = library.watchingAnime.filterNot { SeriesIdentity.item(it).key == item.key }
            library.hiddenWatchingAnime = library.hiddenWatchingAnime + item.key
            mutableState.update { it.copy(savedItems = saved, mediaStates = states, hiddenLibraryItems = hidden, savedAddedAt = library.savedAddedAt,
                watchProgress = history.entries.value, watchingAnime = watching(history.entries.value)) }
            onComplete(true)
        }
    }

    fun removeAnimeEntry(service: AnimeService, entry: AnimeEntry, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onComplete(tracking.removeConfirmed(service, entry))
        }
    }

    private suspend fun persistMediaState(selected: MediaItem, season: Int?, requested: MediaListState) {
        val item = SeriesIdentity.item(selected)
        val value = if (requested.status == AnimeStatus.DROPPED) requested.copy(progress = 0) else requested
        val stateKey = mediaStateKey(item, season)
        val states = state.value.mediaStates.toMutableMap().apply { put(stateKey, value) }
        val saved = (state.value.savedItems + item).distinctBy { it.key }.toMutableList()
        var discoveredSequel: MediaItem? = null
        if (value.status == AnimeStatus.COMPLETED && item.isAnime && item.type == MediaType.SERIES) {
            val detail = state.value.detail?.takeIf { it.item.key == selected.key }
            val nextSeason = season?.let { current -> detail?.seasons.orEmpty().filter { it.number > current }.minByOrNull { it.number } }
            nextSeason?.let { next -> states.putIfAbsent(mediaStateKey(item, next.number), MediaListState()) }
            discoveredSequel = detail?.sequel?.let(SeriesIdentity::item)?.takeIf { sequel -> sequel.key != item.key }
            discoveredSequel?.let { sequel ->
                if (saved.none { it.key == sequel.key }) saved.add(sequel)
                states.putIfAbsent(mediaStateKey(sequel, sequel.seasonHint ?: 1), MediaListState())
            }
        }
        library.mediaStates = states
        library.hiddenLibraryItems = library.hiddenLibraryItems - item.key
        library.save(saved)
        if (value.status == AnimeStatus.DROPPED) {
            history.reset(item, season.takeIf { stateKey != item.key })
            if (stateKey == item.key) {
                library.watchingAnime = library.watchingAnime.filterNot { SeriesIdentity.item(it).key == item.key }
                library.hiddenWatchingAnime = library.hiddenWatchingAnime + item.key
            }
        } else if (value.progress > 0 && value.progress <= WatchProgressCodec.MAX_ENTRIES) {
            val targets = if (selected.type == MediaType.MOVIE) listOf(WatchTarget(item)) else
                (1..value.progress).map { SeriesIdentity.index.target(WatchTarget(selected, season ?: 1, it)) }
            history.importWatched(targets)
        }
        mutableState.update { it.copy(mediaStates = states, savedItems = saved, hiddenLibraryItems = library.hiddenLibraryItems, savedAddedAt = library.savedAddedAt,
            watchProgress = history.entries.value, watchingAnime = watching(history.entries.value)) }
        viewModelScope.launch { loadLibrarySeasonCards() }
        val connections = tracking.state.value.connections
        val ids = buildSet {
            if (selected.source == CatalogSource.ANILIST) selected.id.toIntOrNull()?.let(::add)
            item.animeEntryId?.toIntOrNull()?.let { mapped -> if (season == null || item.seasonHint == null || item.seasonHint == season) add(mapped) }
            SeriesIdentity.index.parts(item).filter { season == null || it.season == season }
                .mapNotNull { it.animeId.toIntOrNull() }.forEach(::add)
            connections.forEach { (service, connection) -> connection.entries.forEach { entry ->
                val linkedItem = entry.item()?.let(SeriesIdentity::item)
                if (linkedItem != null && linkedItem.key == item.key &&
                    (season == null || linkedItem.seasonHint == null || linkedItem.seasonHint == season)) {
                    (entry.aniListId ?: entry.id.takeIf { service == AnimeService.ANILIST })?.let(::add)
                }
            } }
        }
        if (item.isAnime || item.source == CatalogSource.ANILIST || ids.isNotEmpty()) {
            ids.forEach { id -> connections.forEach { (service, connection) ->
                if (connection.account != null && service in state.value.syncServices) tracking.saveState(service, id, value)
            } }
        }
        discoveredSequel?.let { sequel ->
            val id = sequel.id.toIntOrNull()
            if (sequel.source == CatalogSource.ANILIST && id != null) tracking.state.value.connections.forEach { (service, connection) ->
                if (connection.account != null && service in state.value.syncServices) tracking.saveState(service, id, MediaListState())
            }
        }
    }
    private suspend fun loadLibrarySeasonCards() {
        val requested = state.value.mediaStates.keys.toList()
        for (key in requested) {
            val match = Regex("^(.*):season:([0-9]+)$").matchEntire(key) ?: continue
            val item = state.value.savedItems.firstOrNull { it.key == match.groupValues[1] } ?: continue
            val season = match.groupValues[2].toIntOrNull() ?: continue
            if (state.value.librarySeasonCards.any { it.key == item.key && it.seasonHint == season }) continue
            try {
                val card = repository.seasonCard(item, season)
                val cards = state.value.librarySeasonCards.filterNot { it.key == item.key && it.seasonHint == season } + card
                library.librarySeasonCards = cards
                mutableState.update { it.copy(librarySeasonCards = cards) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the labelled season card offline; retry on the next update. */ }
        }
    }
    fun renameProfile(name: String) {
        val normalized = LocalLibraryStore.normalizeName(name) ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(profileName = normalized, profileMessage = "Nombre guardado en este dispositivo.") }
            library.profileName = normalized
        }
    }

    suspend fun animeAuthorization(service: AnimeService): String = tracking.begin(service)
    fun handleAnimeAuthorization(raw: String) { viewModelScope.launch {
        val service = AnimeOAuth.service(raw) ?: return@launch
        tracking.finish(raw)
        if (tracking.state.value.connections[service]?.account != null) setSyncService(service, true)
    } }
    fun refreshAnimeAccount(service: AnimeService) { viewModelScope.launch {
        tracking.refresh(service)
    } }
    fun editAnimeEntry(service: AnimeService, aniListId: Int) { viewModelScope.launch { tracking.edit(service, aniListId) } }
    private fun localAnimeProgress(aniListId: Int): Int {
        val watched = state.value.watchProgress.asSequence().filter { it.watched }.mapNotNull {
            AnimeSyncRules.episode(it.target, SeriesIdentity.index)
        }.filter { it.first == aniListId }.map { it.second }.toSet()
        var progress = 0
        while (progress + 1 in watched) progress++
        return progress
    }
    fun dismissAnimeDraft() = tracking.dismissDraft()
    fun disconnectAnimeAccount(service: AnimeService) { viewModelScope.launch {
        tracking.disconnect(service)
        if (tracking.state.value.connections[service]?.account == null) setSyncService(service, false)
    } }
    fun updateAnimeEntry(service: AnimeService, entry: AnimeEntry, status: AnimeStatus, progress: Int, score: Double) {
        viewModelScope.launch { tracking.update(service, entry, status, progress, score) }
    }
    fun importAnimeEntry(entry: AnimeEntry) {
        viewModelScope.launch { importAnimeEntries(listOf(entry), requireHistoryCapacity = true) }
    }
    private suspend fun importAnimeEntries(entries: List<AnimeEntry>, requireHistoryCapacity: Boolean = false) {
        val imported = entries.mapNotNull { entry -> entry.item()?.let { entry to SeriesIdentity.item(it) } }
        if (imported.isEmpty()) return
        val progressTooLarge = imported.any { it.first.progress > WatchProgressCodec.MAX_ENTRIES }
        if (progressTooLarge && requireHistoryCapacity) {
            mutableState.update { it.copy(profileMessage = "Una entrada supera la capacidad del historial local. No se ha importado ni eliminado ningún dato.") }
            return
        }
        val targets = if (progressTooLarge) emptyList() else imported.flatMap { (entry, item) ->
            if (item.type == MediaType.MOVIE) {
                if (entry.progress > 0 || entry.status == AnimeStatus.COMPLETED) listOf(WatchTarget(item)) else emptyList()
            } else (1..entry.progress).map { SeriesIdentity.index.target(WatchTarget(item, 1, it)) }
        }.distinctBy { it.key }
        val historyImported = if (targets.isEmpty() && !progressTooLarge) true else !progressTooLarge && history.importWatched(targets)
        if (!historyImported && requireHistoryCapacity) {
            mutableState.update { it.copy(profileMessage = "No hay capacidad para importar todas las marcas sin desplazar historial existente. No se ha eliminado ni importado ningún dato.") }
            return
        }
        val saved = (imported.map { it.second } + state.value.savedItems).distinctBy { it.key }
        withContext(Dispatchers.IO) { library.save(saved) }
        mutableState.update { it.copy(savedItems = saved, savedAddedAt = library.savedAddedAt,
            profileMessage = if (historyImported) "Anime sincronizado a la biblioteca. Se conservan las marcas y posiciones existentes."
                else "Anime sincronizado a la biblioteca; no se importaron las marcas de visto porque se alcanzó el límite local.") }
    }
    fun saveProfileAppearance(value: ProfileAppearance) {
        if (state.value.profileBusy) return
        mutableState.update { it.copy(profileBusy = true, profileMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { profileStore.save(value) }
                mutableState.update { it.copy(profileAppearance = value, profileMessage = "Perfil guardado.") }
            } catch (_: Exception) { mutableState.update { it.copy(profileMessage = "No se pudo guardar el perfil.") } }
            finally { mutableState.update { it.copy(profileBusy = false) } }
        }
    }
    fun importProfileImage(uri: android.net.Uri, avatar: Boolean) {
        if (state.value.profileBusy) return
        mutableState.update { it.copy(profileBusy = true, profileMessage = null) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    val path = profileStore.import(uri, avatar)
                    val previous = state.value.profileAppearance
                    (if (avatar) previous.copy(avatarPath = path) else previous.copy(bannerPath = path)).also(profileStore::save)
                }
                mutableState.update { it.copy(profileAppearance = value, profileMessage = "Imagen guardada en este dispositivo.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(profileMessage = "No se pudo importar la imagen. Elige una imagen de hasta 12 MB.") } }
            finally { mutableState.update { it.copy(profileBusy = false) } }
        }
    }
    fun prepareDownloads(item: MediaItem, episodes: List<EpisodeItem>) {
        if (state.value.downloadEnqueueing) return
        val targets = if (item.type == MediaType.MOVIE) listOf(WatchTarget(item)) else episodes.filter { it.released }
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.number }).map { WatchTarget(item, it.season, it.number, it.title, it.imageUrl) }
        val pending = targets.distinctBy { it.key }.filter { target -> state.value.downloads.none { it.target.key == target.key } }
        if (pending.isEmpty()) {
            mutableState.update { it.copy(downloadMessage = "No hay elementos nuevos para descargar. Administra las tareas existentes desde tu perfil.") }
            return
        }
        mutableState.update { it.copy(downloadSelection = pending, downloadMessage = null) }
        pending.first().let { requestSources(it.item, it.season, it.episode) }
    }
    fun chooseDownloadSource(source: StreamSource) {
        if (state.value.downloadEnqueueing) return
        val target = state.value.downloadSelection.firstOrNull() ?: return
        mutableState.update { it.copy(downloadEnqueueing = true, downloadMessage = null) }
        viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) { downloads.enqueue(target, source) }
                mutableState.update { it.copy(downloadMessage = if (added) "Añadido a Descargas. Se guardará en una red no medida (Wi-Fi)." else "Este vídeo ya está en Descargas.") }
                if (state.value.downloadSelection.firstOrNull()?.key == target.key) nextDownloadSource()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(downloadMessage = "No se pudo añadir la fuente. Prueba otra opción.") } }
            finally { mutableState.update { it.copy(downloadEnqueueing = false) } }
        }
    }
    fun nextDownloadSource() {
        if (state.value.downloadEnqueueing && state.value.downloadSelection.isEmpty()) return
        val pending = state.value.downloadSelection.drop(1)
        sourceJob?.cancel()
        mutableState.update { it.copy(downloadSelection = pending, sourceQuery = null, sources = emptyList(), sourcesLoading = false) }
        pending.firstOrNull()?.let { requestSources(it.item, it.season, it.episode) }
    }
    fun downloadAction(id: String, action: String, fileIndex: Int? = null) {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { when (action) {
                "pause" -> downloads.pause(id)
                "resume" -> downloads.resume(id, fileIndex)
                "delete" -> downloads.delete(id)
            } } }
            catch (_: Exception) { mutableState.update { it.copy(downloadMessage = "No se pudo realizar la acción. Si se está usando el vídeo, ciérralo o pausa la descarga y reintenta.") } }
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
        mutableState.update { it.copy(sourceQuery = null, sources = emptyList(), sourcesLoading = false, sourceError = null, downloadSelection = emptyList()) }
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
                // All IMDb addons must receive the same converted episode. Share the conversion,
                // otherwise parallel provider requests can repeat metadata work and disagree.
                val standardQuery = async {
                    try { Result.success(repository.providerQuery(resolved, false)) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Result.failure(error) }
                }
                val lookups = addons.filter { hasImdb }.map { addon -> null to suspend {
                    (if (ProviderHub.isHub(addon)) hubRepository else sourceRepository).sources(addon, standardQuery.await().getOrThrow()) } } +
                    compatibleDesktop.map { provider -> provider.id to suspend {
                         desktopSourceRepository.sources(provider.id, if (provider.id == "animeav1") repository.providerQuery(resolved, true) else standardQuery.await().getOrThrow()) } }
                suspend fun lookup(desktopId: String?, request: suspend () -> SourceResult) {
                    try {
                        val result = request()
                        ensureActive()
                        unsupported += result.unsupportedCount
                        mutableState.update { current ->
                            if (current.sourceQuery?.key != resolved.key) current else current.copy(
                                sources = SourceMetadata.merge(current.sources, hubDirectory.state.value.decorate(result.sources)))
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        failures++
                        if (desktopId != null) mutableState.update { current ->
                            if (current.sourceQuery?.key != resolved.key) current else current.copy(
                                desktopSourceErrors = current.desktopSourceErrors + desktopId)
                        }
                    }
                }
                supervisorScope {
                    val queried = mutableSetOf<String>()
                    fun queryManaged(directory: HubDirectoryResult) {
                        directory.providers.filter { provider ->
                            hasImdb && provider.enabled && provider.config?.types?.contains(item.type) == true &&
                                addons.none { !ProviderHub.isHub(it) && it.manifestId == provider.id } &&
                                desktop.none { it.manifestUrl == provider.config.manifestUrl }
                        }.forEach { provider ->
                            val config = requireNotNull(provider.config)
                            if (queried.add(config.id)) launch { lookup(null) {
                                ProviderHubDirectory.filter(provider, SourceRepository(requestTimeoutMs = provider.timeoutMs, attempts = provider.attempts)
                                    .sources(config, standardQuery.await().getOrThrow()))
                            } }
                        }
                    }
                    // Start integrated/custom/cached providers immediately, not after two directory
                    // round-trips. Freshly published providers join this same query as metadata arrives.
                    lookups.forEach { (desktopId, request) -> launch { lookup(desktopId, request) } }
                    queryManaged(hubDirectory.state.value)
                    launch {
                        val directory = refreshHubDirectory()
                        if (directory.stale) failures++
                        queryManaged(directory)
                    }
                }
                ensureActive()
                mutableState.update { current ->
                    if (current.sourceQuery?.key != resolved.key) current else current.copy(
                        sourcesLoading = false,
                        sourceError = buildList {
                            if (failures > 0) add("$failures consultas de proveedores fallaron. Puedes reintentar; las fuentes recibidas siguen disponibles.")
                            if (unsupported > 0) add("$unsupported resultados no son compatibles (URL, DRM o datos inválidos) y no se enviarán al reproductor.")
                        }.joinToString(" ").takeIf { it.isNotEmpty() }
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
