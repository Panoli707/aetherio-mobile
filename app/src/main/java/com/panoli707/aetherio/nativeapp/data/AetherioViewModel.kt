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
    private val app = application as com.panoli707.aetherio.nativeapp.AetherioApplication
    private val profileStore = ProfileStore(application)
    private val tracking = app.animeTracking
    private val downloads = app.mediaDownloads
    private val animeRepository = AnimeSeasonRepository()
    private val repository = AetherioRepository(animeRepository = animeRepository,
        homeCache = HomeCatalogCache(java.io.File(application.cacheDir, "desktop-home-53a9276")))
    private val library = LocalLibraryStore(application)
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
        homePreferences = homePreferencesStore.load(), profileAppearance = profileStore.load(),
        sourcePreferences = sourceSettings.loadPreferences(),
        enabledDesktopSources = sourceSettings.enabledDesktopSources))
    val state: StateFlow<AppState> = mutableState.asStateFlow()
    private var homeJob: Job? = null
    private var browseJob: Job? = null
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
            mutableState.update { it.copy(profileName = normalized, profileMessage = "Nombre guardado en este dispositivo.") }
            library.profileName = normalized
        }
    }

    suspend fun animeAuthorization(service: AnimeService): String = tracking.begin(service)
    fun handleAnimeAuthorization(raw: String) { viewModelScope.launch { tracking.finish(raw) } }
    fun refreshAnimeAccount(service: AnimeService) { viewModelScope.launch { tracking.refresh(service) } }
    fun editAnimeEntry(service: AnimeService, aniListId: Int) { viewModelScope.launch { tracking.edit(service, aniListId) } }
    fun dismissAnimeDraft() = tracking.dismissDraft()
    fun disconnectAnimeAccount(service: AnimeService) { viewModelScope.launch { tracking.disconnect(service) } }
    fun updateAnimeEntry(service: AnimeService, entry: AnimeEntry, status: AnimeStatus, progress: Int, score: Double) {
        viewModelScope.launch { tracking.update(service, entry, status, progress, score) }
    }
    fun importAnimeEntry(entry: AnimeEntry) {
        val item = entry.item() ?: return
        viewModelScope.launch {
            if (entry.progress > WatchProgressCodec.MAX_ENTRIES) {
                mutableState.update { it.copy(profileMessage = "Esta entrada supera la capacidad del historial local. No se ha importado ni eliminado ningún dato.") }
                return@launch
            }
            val targets = if (item.type == MediaType.MOVIE) { if (entry.progress > 0 || entry.status == AnimeStatus.COMPLETED) listOf(WatchTarget(item)) else emptyList() }
                else (1..entry.progress).map { SeriesIdentity.index.target(WatchTarget(item, 1, it)) }
            if (!history.importWatched(targets)) {
                mutableState.update { it.copy(profileMessage = "No hay capacidad para importar todas las marcas sin desplazar historial existente. No se ha eliminado ni importado ningún dato.") }
                return@launch
            }
            val canonical = SeriesIdentity.item(item)
            val saved = (listOf(canonical) + state.value.savedItems).distinctBy { it.key }
            withContext(Dispatchers.IO) { library.save(saved) }
            mutableState.update { it.copy(savedItems = saved) }
            // Remote counters do not invent playback positions. Only explicit per-entry confirmation imports badges.
            mutableState.update { it.copy(profileMessage = "Anime importado a la biblioteca. Se conservan las marcas y posiciones existentes.") }
        }
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
                val lookups = addons.filter { hasImdb }.map { addon -> null to suspend {
                    (if (ProviderHub.isHub(addon)) hubRepository else sourceRepository).sources(addon, repository.providerQuery(resolved, false)) } } +
                    compatibleDesktop.map { provider -> provider.id to suspend {
                         desktopSourceRepository.sources(provider.id, repository.providerQuery(resolved, provider.id == "animeav1")) } }
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
                                    .sources(config, repository.providerQuery(resolved, false)))
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
