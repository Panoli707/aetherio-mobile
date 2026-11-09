package com.panoli707.aetherio.nativeapp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.panoli707.aetherio.nativeapp.data.AnimeOAuth
import com.panoli707.aetherio.nativeapp.ui.PersonalActions
import com.panoli707.aetherio.nativeapp.data.StreamSource
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import com.panoli707.aetherio.nativeapp.player.torrent.TorrentRequestParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panoli707.aetherio.nativeapp.data.AetherioViewModel
import com.panoli707.aetherio.nativeapp.data.UpdateViewModel
import com.panoli707.aetherio.nativeapp.data.WatchProgressCodec
import com.panoli707.aetherio.nativeapp.data.DetailLinks
import com.panoli707.aetherio.nativeapp.data.SourcePlayback
import org.json.JSONArray
import org.json.JSONObject
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import com.panoli707.aetherio.nativeapp.player.VideoActivity
import com.panoli707.aetherio.nativeapp.player.TrailerPlayback
import com.panoli707.aetherio.nativeapp.ui.AetherioApp
import com.panoli707.aetherio.nativeapp.ui.loadThemePalette

class MainActivity : ComponentActivity() {
    private val model: AetherioViewModel by viewModels()
    private val updateModel: UpdateViewModel by viewModels()
    private var profileRequest by mutableIntStateOf(0)
    private var downloadsRequest by mutableIntStateOf(0)
    private var routeSequence = 0

    private fun consumeNavigation(request: Intent) {
        val callback = request.dataString
        request.data = null // OAuth fragments/codes must not survive recreation or appear in further intents.
        if (callback != null && AnimeOAuth.service(callback) != null) {
            profileRequest = ++routeSequence
            model.handleAnimeAuthorization(callback)
        }
        if (request.getBooleanExtra("openDownloads", false)) { downloadsRequest = ++routeSequence; request.removeExtra("openDownloads") }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeNavigation(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Aetherio)
        super.onCreate(savedInstanceState)
        loadThemePalette(this)
        routeSequence = savedInstanceState?.getInt("personalRouteSequence") ?: 0
        profileRequest = savedInstanceState?.getInt("personalProfileRequest") ?: 0
        downloadsRequest = savedInstanceState?.getInt("personalDownloadsRequest") ?: 0
        consumeNavigation(intent)
        if (Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val updates by updateModel.state.collectAsStateWithLifecycle()
            val activityState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
            val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra(VideoActivity.EXTRA_FIND_SOURCES, false) == true) {
                    model.reopenPlaybackSources()
                }
            }
            val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) updateModel.requestInstall()
                else updateModel.installationUnavailable()
            }
            LaunchedEffect(updates.installRequested, activityState, state.sourceQuery) {
                if (updates.installRequested && activityState.isAtLeast(Lifecycle.State.RESUMED) && state.sourceQuery == null) {
                    val installer = updateModel.consumeInstallRequest() ?: return@LaunchedEffect
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        } else {
                            installLauncher.launch(installer)
                            updateModel.installerLaunched()
                        }
                    } catch (_: Exception) { updateModel.installationUnavailable() }
                }
            }
            val launchSource: (StreamSource, VideoFormat) -> Unit = { source, format ->
                try {
                    val torrent = if (source.isTorrent) TorrentRequestParser.fromSource(source) else null
                    val validated = if (torrent == null) SourcePlayback.httpUrl(source) else null
                    val query = model.state.value.sourceQuery
                    val title = query?.let {
                        if (it.season != null && it.episode != null) "${it.item.title} · T${it.season} E${it.episode}" else it.item.title
                    } ?: "Reproductor"
                    model.rememberPlaybackQuery(fromSources = true)
                    val watchTarget = model.playbackTarget(source.addonId in setOf("pc:animeav1", "pc:animes"))
                    val target = Intent(this, VideoActivity::class.java)
                        .putExtra(VideoActivity.EXTRA_TITLE, title)
                        .putExtra(VideoActivity.EXTRA_OVERVIEW, query?.item?.overview?.take(4000))
                        .putExtra(VideoActivity.EXTRA_PROVIDER, source.providerName)
                        .putExtra(VideoActivity.EXTRA_HAS_SOURCES, query != null)
                        .putExtra(VideoActivity.EXTRA_WATCH_TARGET, watchTarget?.let(WatchProgressCodec::encodeTarget))
                        .putExtra(VideoActivity.EXTRA_SUBTITLES, JSONArray().apply {
                            source.subtitles.forEach { sub -> put(JSONObject().put("id", sub.id).put("url", sub.url)
                                .put("lang", sub.lang).put("mimeType", sub.mimeType)) }
                        }.toString())
                    if (torrent != null) target.putExtra(VideoActivity.EXTRA_TORRENT_REQUEST, torrent.encode())
                    else target.putExtra(VideoActivity.EXTRA_URL, validated)
                        .putExtra(VideoActivity.EXTRA_MIME, format.mimeType)
                        .putExtra(VideoActivity.EXTRA_DESKTOP_HTTP, source.allowDesktopHttp)
                        .putExtra(VideoActivity.EXTRA_PROVIDER_HEADERS, source.allowProviderHeaders)
                        .putExtra(VideoActivity.EXTRA_HEADERS, Bundle().apply {
                            source.headers.forEach { (name, value) -> putString(name, value) }
                        })
                    videoLauncher.launch(target)
                    model.dismissSources()
                } catch (_: Exception) {
                    Toast.makeText(this, "No se pudo abrir esta fuente. Prueba otra opción.", Toast.LENGTH_LONG).show()
                }
            }
            AetherioApp(
                state = state,
                updates = updates,
                onSelectMedia = model::selectMedia,
                onToggleSaved = model::toggleSaved,
                onEpisodeWatchAction = model::markWatched,
                onToggleWatching = model::toggleWatching,
                onLoadAnimeSeason = model::loadAnimeSeason,
                onLoadWatchingCalendar = model::loadWatchingCalendar,
                onLeaveMovieTvCalendar = model::resetMovieTvCalendar,
                onBrowse = model::loadBrowse,
                onLibraryFilters = model::setLibraryFilters,
                onDiscover = model::loadDiscover,
                onHomePreferencesChange = model::setHomePreferences,
                onSourcePreferencesChange = model::setSourcePreferences,
                onOpenExternal = { url ->
                    val validated = DetailLinks.validate(url)
                    if (validated != null) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(validated))) }
                    catch (_: Exception) { Toast.makeText(this, "No se pudo abrir el enlace externo.", Toast.LENGTH_LONG).show() }
                },
                onPlayTrailer = { trailer ->
                    try { startActivity(TrailerPlayback.intent(this, trailer)) }
                    catch (_: Exception) { Toast.makeText(this, "No se pudo abrir el tráiler integrado.", Toast.LENGTH_LONG).show() }
                },
                onContinue = model::resume,
                onSearch = model::search,
                onRetryHome = model::refreshHome,
                onRetryDetail = model::retryDetail,
                onSelectSeason = model::selectSeason,
                onRenameProfile = model::renameProfile,
                profileRequest = profileRequest, downloadsRequest = downloadsRequest,
                personalActions = PersonalActions(
                    setSyncService = model::setSyncService,
                    saveMediaState = model::saveMediaState,
                    removeMedia = model::removeMediaFromLibrary,
                    connect = { service -> lifecycleScope.launch {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.animeAuthorization(service))).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: Exception) { Toast.makeText(this@MainActivity, "No se pudo iniciar la vinculación. Consulta el aviso en Mi espacio.", Toast.LENGTH_LONG).show() }
                    } }, refresh = model::refreshAnimeAccount, disconnect = model::disconnectAnimeAccount,
                    updateAnime = model::updateAnimeEntry, removeAnime = model::removeAnimeEntry, importAnime = model::importAnimeEntry,
                    editAnime = model::editAnimeEntry, dismissAnime = model::dismissAnimeDraft,
                    appearance = model::saveProfileAppearance, importImage = model::importProfileImage,
                    downloadAction = model::downloadAction, playDownload = { id ->
                        state.downloads.firstOrNull { it.id == id }?.let { record ->
                            model.rememberPlaybackQuery(false)
                            videoLauncher.launch(Intent(this, VideoActivity::class.java).putExtra(VideoActivity.EXTRA_DOWNLOAD_ID, id)
                                .putExtra(VideoActivity.EXTRA_TITLE, record.label).putExtra(VideoActivity.EXTRA_WATCH_TARGET, WatchProgressCodec.encodeTarget(record.target)))
                        }
                    }),
                onPrepareDownloads = model::prepareDownloads,
                onSkipDownload = model::nextDownloadSource,
                onDownloadSource = { source ->
                    if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    model.chooseDownloadSource(source)
                },
                onAddAddon = model::addAddon,
                onRemoveAddon = model::removeAddon,
                onDesktopSourceToggle = model::setDesktopSourceEnabled,
                onRequestSources = model::requestSources,
                onDismissSources = model::dismissSources,
                onUpdateAction = updateModel::onAction,
                onAutomaticUpdatesChange = updateModel::setAutomatic,
                onPlaySource = launchSource,
                onOpenVideo = { url, format ->
                    try {
                        val validated = MediaUrlPolicy.validate(url)
                        model.rememberPlaybackQuery(fromSources = false)
                        videoLauncher.launch(Intent(this, VideoActivity::class.java)
                            .putExtra(VideoActivity.EXTRA_URL, validated)
                            .putExtra(VideoActivity.EXTRA_MIME, format.mimeType)
                            .putExtra(VideoActivity.EXTRA_TITLE, "Reproducción por enlace"))
                    } catch (_: Exception) {
                        Toast.makeText(this, "No se pudo abrir el vídeo. Usa una URL HTTPS directa válida.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        model.startHubSync()
        updateModel.checkForUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("personalRouteSequence", routeSequence)
        outState.putInt("personalProfileRequest", profileRequest)
        outState.putInt("personalDownloadsRequest", downloadsRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        model.stopHubSync()
        super.onStop()
    }
}
