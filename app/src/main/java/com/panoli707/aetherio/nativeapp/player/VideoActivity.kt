package com.panoli707.aetherio.nativeapp.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log as AndroidLog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.panoli707.aetherio.nativeapp.ui.AetherioTheme
import com.panoli707.aetherio.nativeapp.data.WatchHistoryRepository
import com.panoli707.aetherio.nativeapp.data.WatchProgressCodec
import com.panoli707.aetherio.nativeapp.data.WatchTarget
import com.panoli707.aetherio.nativeapp.data.SeriesIdentity
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.player.torrent.*
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray

@androidx.annotation.OptIn(UnstableApi::class)
class VideoActivity : ComponentActivity() {
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var screen by mutableStateOf(VideoUiState())
    private var mediaUrl: String? = null
    private var allowDesktopHttp = false
    private var playbackHeaders: Map<String, String> = emptyMap()
    private var resumePosition = C.TIME_UNSET
    private var resumePlayWhenReady = true
    private var mediaIsLive = false
    private var ticker: Job? = null
    private var startJob: Job? = null
    private var torrentRequest: TorrentRequest? = null
    private var torrentSession: TorrentSessionManager.Session? = null
    private var torrentRoute: TorrentRoute? = null
    private var torrentJob: Job? = null
    private var lastTorrentProgress = 0L
    private var lastTorrentProgressAt = 0L
    private var watchTarget: WatchTarget? = null
    private val history by lazy { WatchHistoryRepository.get(application) }
    private var progressInitialized = false
    private var restoredSession = false
    private var lastSaveTime = 0L
    private var lastSavedPosition = -1L
    private var lastSavedDuration = -1L
    private var sessionReady = false
    private val trackOptions = mutableMapOf<String, Pair<TrackGroup, Int>>()

    private val httpClient by lazy {
        OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                try { MediaUrlPolicy.validate(chain.request().url.toString(), allowDesktopHttp) }
                catch (_: IllegalArgumentException) { throw IOException("Destino de video no permitido") }
                val request = chain.request()
                val headers = request.headers.names().associateWith { request.header(it).orEmpty() }
                val allowed = PlaybackHeaders.forDestination(headers, mediaUrl, request.url)
                val builder = request.newBuilder()
                (headers.keys - allowed.keys).forEach(builder::removeHeader)
                chain.proceed(builder.build())
            }.build()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(active: Player, events: Player.Events) {
            if (active !== player) return
            refreshState()
            refreshTracks(active)
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            AndroidLog.i(TAG, "playingBool=$playing")
        }
        override fun onPlaybackStateChanged(state: Int) {
            AndroidLog.i(TAG, "stateInt=$state")
            if (state == Player.STATE_READY) sessionReady = true
            if (state == Player.STATE_ENDED) { saveProgress(force = true, ended = true); closeTorrent() }
        }
        override fun onPlayerError(error: PlaybackException) {
            saveProgress(force = true)
            AndroidLog.i(TAG, "errorInt=${error.errorCode}")
            val http = generateSequence(error as Throwable) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
            if (http != null) AndroidLog.i(TAG, "httpStatusInt=$http")
            screen = screen.copy(buffering = false, playing = false, error = when {
                http in listOf(401, 403, 404, 410) -> "El servidor no permite usar este enlace o ya ha caducado. Prueba otra fuente."
                http != null -> "El servidor de video no responde correctamente (HTTP $http). Prueba otra fuente."
                error.errorCode in 4000..4999 -> "Este formato de video o audio no es compatible con el dispositivo."
                else -> "No se pudo reproducir esta fuente. Revisa la conexion o elige otra."
            })
            if (torrentRequest != null) { releasePlayer(); closeTorrent() }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Media3Log.setLogLevel(Media3Log.LOG_LEVEL_OFF)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val defaultOrientation = if (resources.configuration.smallestScreenWidthDp >= 600 || isInMultiWindowMode)
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        requestedOrientation = savedInstanceState?.getInt(STATE_ORIENTATION, defaultOrientation) ?: defaultOrientation
        mediaIsLive = savedInstanceState?.getBoolean(STATE_LIVE, false) ?: false
        resumePosition = if (mediaIsLive) C.TIME_UNSET else savedInstanceState?.getLong(STATE_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET
        resumePlayWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY, true) ?: true
        restoredSession = savedInstanceState != null
        watchTarget = WatchProgressCodec.decodeTarget(intent.getStringExtra(EXTRA_WATCH_TARGET))?.let(SeriesIdentity.index::target)
        title = cleanLabel(intent.getStringExtra(EXTRA_TITLE)) ?: "Reproductor"
        screen = VideoUiState(title = watchTarget?.episodeTitle?.takeIf { it.isNotBlank() }
            ?: watchTarget?.item?.title ?: title.toString(), subtitle = cleanLabel(intent.getStringExtra(EXTRA_PROVIDER)).orEmpty(),
            animeEpisode = watchTarget?.let { it.item.isAnime && it.episode != null && (it.season ?: 0) >= 1 } == true,
            volume = savedInstanceState?.getFloat(STATE_VOLUME, 1f)?.coerceIn(0f, 1f) ?: 1f,
            episodeLabel = watchTarget?.let { target -> target.season?.let { "T$it · E${target.episode}" } }.orEmpty(),
            posterUrl = watchTarget?.item?.posterUrl,
            overview = intent.getStringExtra(EXTRA_OVERVIEW)?.take(4000)?.trim().orEmpty())
        watchTarget?.takeIf { screen.animeEpisode }?.let { target -> lifecycleScope.launch {
            val segments = OpeningRepository().segments(SeriesIdentity.externalTarget(target))
            screen = screen.copy(opening = segments?.opening, ending = segments?.ending)
        } }
        mediaUrl = try {
            if (intent.hasExtra(EXTRA_TORRENT_REQUEST)) {
                require(!intent.hasExtra(EXTRA_URL))
                torrentRequest = TorrentRequest.decode(requireNotNull(intent.getStringExtra(EXTRA_TORRENT_REQUEST)))
            }
            allowDesktopHttp = torrentRequest == null && intent.getBooleanExtra(EXTRA_DESKTOP_HTTP, false)
            val headers = if (torrentRequest == null) intent.getBundleExtra(EXTRA_HEADERS) else null
            playbackHeaders = PlaybackHeaders.validate(headers?.keySet()?.associateWith {
                headers.getString(it) ?: throw IllegalArgumentException()
            }.orEmpty(), torrentRequest == null && intent.getBooleanExtra(EXTRA_PROVIDER_HEADERS, false))
            if (torrentRequest != null) null else MediaUrlPolicy.validate(intent.getStringExtra(EXTRA_URL).orEmpty(), allowDesktopHttp)
        } catch (_: RuntimeException) {
            torrentRequest = null
            screen = screen.copy(error = "El enlace de video no es valido o no esta permitido.")
            null
        }
        setContent {
            AetherioTheme {
                PlayerScreen(screen, intent.getBooleanExtra(EXTRA_HAS_SOURCES, false), ::onAction,
                    onSeek = { player?.takeIf { it.isCurrentMediaItemSeekable }?.seekTo(it) },
                    onSpeed = { player?.setPlaybackSpeed(it) }, onTrack = ::selectTrack,
                    onVolume = { value ->
                        val volume = value.coerceIn(0f, 1f)
                        screen = screen.copy(volume = volume)
                        player?.volume = volume
                    },
                    video = { mode -> AndroidView(
                        factory = { context -> PlayerView(context).apply {
                            useController = false
                            setShutterBackgroundColor(Color.BLACK)
                            setBackgroundColor(Color.BLACK)
                            setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_OFF)
                            subtitleView?.let(PlayerSubtitleStyle::applyTo)
                        } },
                        modifier = Modifier.fillMaxSize(), onReset = null,
                        onRelease = { it.player = null },
                        update = { it.player = player; it.resizeMode = mode }
                     ) }, onTorrentFile = { index -> torrentSession?.selectFile(index) })
            }
        }
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        startJob = lifecycleScope.launch {
            if (!progressInitialized) {
                val saved = watchTarget?.let { target -> history.load().firstOrNull { it.target.key == target.key } }
                if (!restoredSession && !mediaIsLive) resumePosition = saved?.resumePositionMs ?: C.TIME_UNSET
                progressInitialized = true
            }
            // onStart runs before Lifecycle dispatches ON_START; cancellation in onStop is the guard.
            if (!isActive || isFinishing) return@launch
            preparePlayback()
            ticker = lifecycleScope.launch { while (isActive) { refreshState(); saveProgress(); checkTorrentStall(); delay(500) } }
        }
    }
    override fun onPause() {
        saveProgress(force = true)
        super.onPause()
    }
    override fun onStop() {
        startJob?.cancel()
        startJob = null
        ticker?.cancel()
        ticker = null
        releasePlayer()
        closeTorrent()
        super.onStop()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        saveProgress(force = true)
        capturePlaybackState()
        outState.putBoolean(STATE_LIVE, mediaIsLive)
        outState.putLong(STATE_POSITION, resumePosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, resumePlayWhenReady)
        outState.putInt(STATE_ORIENTATION, requestedOrientation)
        outState.putFloat(STATE_VOLUME, screen.volume)
        super.onSaveInstanceState(outState)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun preparePlayback() {
        val request = torrentRequest
        if (request == null) { startPlayer(); return }
        closeTorrent()
        screen = screen.copy(error = null, buffering = true, torrentLabel = "Iniciando motor", torrentFiles = emptyList(), torrentMetrics = null)
        val session = (application as AetherioApplication).torrentSessions.open(request)
        torrentSession = session
        torrentJob = lifecycleScope.launch {
            session.state.collect { state ->
                if (torrentSession !== session) return@collect
                when (state) {
                    is TorrentState.Preparing -> screen = screen.copy(torrentLabel = state.label, buffering = true)
                    is TorrentState.SelectFile -> screen = screen.copy(torrentLabel = "Selecciona el archivo", torrentFiles = state.files)
                    is TorrentState.Ready -> {
                        if (torrentRoute !== state.route) {
                            torrentRoute = state.route
                            mediaUrl = state.route.url
                            lastTorrentProgressAt = SystemClock.elapsedRealtime()
                            lastTorrentProgress = 0L
                            screen = screen.copy(torrentLabel = null, torrentFiles = emptyList())
                            startPlayer()
                        }
                        screen = screen.copy(torrentMetrics = state.metrics)
                    }
                    is TorrentState.Failed -> {
                        releasePlayer()
                        closeTorrent()
                        screen = screen.copy(error = state.message, buffering = false, torrentLabel = null, torrentFiles = emptyList())
                    }
                    TorrentState.Closed -> Unit
                }
            }
        }
    }

    private fun closeTorrent() {
        torrentJob?.cancel()
        torrentJob = null
        torrentSession?.close()
        torrentSession = null
        torrentRoute = null
        if (torrentRequest != null) mediaUrl = null
        screen = screen.copy(torrentLabel = null, torrentFiles = emptyList(), torrentMetrics = null)
    }

    private fun checkTorrentStall() {
        val active = player ?: return
        if (torrentRoute == null) return
        val now = SystemClock.elapsedRealtime()
        val bytes = screen.torrentMetrics?.let { it.verifiedBytes + it.deliveredBytes } ?: 0L
        if (!active.playWhenReady || active.playbackState != Player.STATE_BUFFERING || bytes != lastTorrentProgress) {
            lastTorrentProgressAt = now
            lastTorrentProgress = bytes
        } else if (now - lastTorrentProgressAt >= 120_000) {
            releasePlayer()
            closeTorrent()
            screen = screen.copy(error = "El torrent no está entregando datos. Reintenta o elige otra fuente.", buffering = false)
        }
    }

    private fun startPlayer() {
        val url = mediaUrl ?: return
        if (player != null) return
        screen = screen.copy(error = null, buffering = true, videoHeight = 0)
        sessionReady = false
        try {
            val remoteSource = ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(httpClient).setDefaultRequestProperties(playbackHeaders)
            ) { spec ->
                val validated = try { MediaUrlPolicy.validate(spec.uri.toString(), allowDesktopHttp) }
                catch (_: IllegalArgumentException) { throw IOException("Destino de video no permitido") }
                spec.withUri(Uri.parse(validated))
            }
            val source = torrentRoute?.let { TorrentDataSourceFactory(it, remoteSource) } ?: remoteSource
            val active = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
            player = active
            active.volume = screen.volume
            active.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            active.setHandleAudioBecomingNoisy(true)
            active.addListener(listener)
            active.playWhenReady = resumePlayWhenReady
            val mime = if (torrentRequest != null) null else VideoFormat.entries.mapNotNull { it.mimeType }.firstOrNull { it == intent.getStringExtra(EXTRA_MIME) }
            val subtitles = mutableListOf<MediaItem.SubtitleConfiguration>()
            val rawSubtitles = intent.getStringExtra(EXTRA_SUBTITLES)
            if (rawSubtitles != null && rawSubtitles.length <= 128 * 1024) runCatching {
                val array = JSONArray(rawSubtitles)
                for (i in 0 until minOf(array.length(), 200)) {
                    val sub = array.getJSONObject(i)
                    val subUrl = MediaUrlPolicy.validate(sub.getString("url"))
                    val subMime = sub.optString("mimeType").takeIf { it in setOf("text/vtt", "application/x-subrip", "text/x-ssa", "application/ttml+xml") } ?: continue
                    subtitles.add(MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl)).setMimeType(subMime)
                        .setLanguage(sub.optString("lang").take(80)).setId(sub.optString("id").take(256)).build())
                }
            }
            active.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mime).setSubtitleConfigurations(subtitles).build(),
                if (mediaIsLive) C.TIME_UNSET else resumePosition)
            active.prepare()
        } catch (_: RuntimeException) {
            releasePlayer()
            closeTorrent()
            screen = screen.copy(buffering = false, error = "No se pudo preparar el reproductor.")
        }
    }

    private fun onAction(action: VideoAction) {
        val active = player
        when (action) {
            VideoAction.CLOSE -> { saveProgress(force = true); releasePlayer(); closeTorrent(); finish() }
            VideoAction.FIND_SOURCES -> {
                if (intent.getBooleanExtra(EXTRA_HAS_SOURCES, false)) {
                    saveProgress(force = true)
                    releasePlayer()
                    closeTorrent()
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_FIND_SOURCES, true))
                    finish()
                }
            }
            VideoAction.RETRY -> { releasePlayer(); preparePlayback() }
            VideoAction.TOGGLE_PLAY -> if (active?.isPlaying == true) active.pause() else active?.play()
            VideoAction.REWIND, VideoAction.FORWARD -> if (active?.isCurrentMediaItemSeekable == true) {
                val target = active.currentPosition + if (action == VideoAction.REWIND) -10_000 else 10_000
                active.seekTo(target.coerceIn(0L, active.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
            }
            VideoAction.ROTATE -> requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            VideoAction.SKIP_OPENING, VideoAction.SKIP_ENDING -> if (active != null && screen.animeEpisode && active.isCurrentMediaItemSeekable && !active.isCurrentMediaItemLive) {
                val duration = active.duration
                val position = active.currentPosition
                val current = screen.copy(positionMs = position, durationMs = duration,
                    seekable = active.isCurrentMediaItemSeekable, live = active.isCurrentMediaItemLive)
                val skip = if (action == VideoAction.SKIP_OPENING) openingSkip(current) else endingSkip(current)
                skip?.let { active.seekTo(it.targetMs); saveProgress(force = true) }
            }
        }
    }

    private fun refreshState() {
        val active = player ?: return
        screen = screen.copy(playing = active.isPlaying,
            buffering = active.playbackState == Player.STATE_BUFFERING && screen.error == null,
            positionMs = active.currentPosition.coerceAtLeast(0), durationMs = active.duration.takeIf { it > 0 } ?: 0,
            seekable = active.isCurrentMediaItemSeekable, live = active.isCurrentMediaItemLive,
            videoHeight = active.videoSize.height, speed = active.playbackParameters.speed)
    }

    private fun refreshTracks(active: Player) {
        trackOptions.clear()
        val audio = mutableListOf<PlayerTrackOption>()
        val subtitles = mutableListOf<PlayerTrackOption>()
        active.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type !in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) return@forEachIndexed
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val id = "${group.type}:$groupIndex:$index"
                trackOptions[id] = group.mediaTrackGroup to index
                val language = format.language?.takeUnless { it == "und" }?.let {
                    Locale.forLanguageTag(it).getDisplayLanguage(Locale("es")).takeIf(String::isNotBlank)
                }
                val label = cleanLabel(format.label) ?: language ?: "Pista ${index + 1}"
                val option = PlayerTrackOption(id, label, group.isTrackSelected(index))
                if (group.type == C.TRACK_TYPE_AUDIO) audio.add(option) else subtitles.add(option)
            }
        }
        screen = screen.copy(audioTracks = audio, subtitleTracks = subtitles)
    }

    private fun selectTrack(id: String) {
        val active = player ?: return
        val builder = active.trackSelectionParameters.buildUpon()
        if (id == "text:none") builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else {
            val (group, index) = trackOptions[id] ?: return
            builder.setTrackTypeDisabled(group.type, false).setOverrideForType(TrackSelectionOverride(group, listOf(index)))
        }
        active.trackSelectionParameters = builder.build()
    }

    private fun capturePlaybackState() {
        val active = player ?: return
        resumePlayWhenReady = active.playWhenReady
        val timeline = active.currentTimeline
        if (!timeline.isEmpty && !timeline.getWindow(active.currentMediaItemIndex, Timeline.Window()).isPlaceholder) {
            mediaIsLive = active.isCurrentMediaItemLive
            resumePosition = if (mediaIsLive) C.TIME_UNSET else active.currentPosition.coerceAtLeast(0)
        }
    }
    private fun saveProgress(force: Boolean = false, ended: Boolean = false) {
        val target = watchTarget ?: return
        val active = player ?: return
        if (!sessionReady || active.isCurrentMediaItemLive || !active.isCurrentMediaItemSeekable) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSaveTime < 5000) return
        val position = active.currentPosition
        val duration = active.duration
        if (!ended && position == lastSavedPosition && duration == lastSavedDuration) return
        lastSaveTime = now
        lastSavedPosition = position
        lastSavedDuration = duration
        history.record(target, position, duration, ended)
    }
    private fun releasePlayer() {
        saveProgress(force = true)
        capturePlaybackState()
        val active = player
        player = null
        active?.removeListener(listener)
        active?.release()
        trackOptions.clear()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen = screen.copy(playing = false, buffering = false)
    }

    private fun cleanLabel(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.length <= 200 && !it.contains("://") && it.none(Char::isISOControl)
    }

    companion object {
        const val EXTRA_URL = "com.panoli707.aetherio.nativeapp.player.URL"
        const val EXTRA_TORRENT_REQUEST = "com.panoli707.aetherio.nativeapp.player.TORRENT_REQUEST"
        const val EXTRA_TITLE = "com.panoli707.aetherio.nativeapp.player.TITLE"
        const val EXTRA_OVERVIEW = "com.panoli707.aetherio.nativeapp.player.OVERVIEW"
        const val EXTRA_MIME = "com.panoli707.aetherio.nativeapp.player.MIME"
        const val EXTRA_HEADERS = "com.panoli707.aetherio.nativeapp.player.HEADERS"
        const val EXTRA_DESKTOP_HTTP = "com.panoli707.aetherio.nativeapp.player.DESKTOP_HTTP"
        const val EXTRA_PROVIDER = "com.panoli707.aetherio.nativeapp.player.PROVIDER"
        const val EXTRA_HAS_SOURCES = "com.panoli707.aetherio.nativeapp.player.HAS_SOURCES"
        const val EXTRA_FIND_SOURCES = "com.panoli707.aetherio.nativeapp.player.FIND_SOURCES"
        const val EXTRA_WATCH_TARGET = "com.panoli707.aetherio.nativeapp.player.WATCH_TARGET"
        const val EXTRA_PROVIDER_HEADERS = "com.panoli707.aetherio.nativeapp.player.PROVIDER_HEADERS"
        const val EXTRA_SUBTITLES = "com.panoli707.aetherio.nativeapp.player.SUBTITLES"
        private const val TAG = "AetherioNativePlayer"
        private const val STATE_POSITION = "player.position"
        private const val STATE_PLAY_WHEN_READY = "player.playWhenReady"
        private const val STATE_LIVE = "player.live"
        private const val STATE_ORIENTATION = "player.orientation"
        private const val STATE_VOLUME = "player.volume"
    }
}
