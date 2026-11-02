package com.panoli707.aetherio.nativeapp.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log as AndroidLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.IOException
import okhttp3.OkHttpClient

@androidx.annotation.OptIn(UnstableApi::class)
class VideoActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var errorPanel: ScrollView
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private var player: ExoPlayer? = null
    private var mediaUrl: String? = null
    private var allowDesktopHttp = false
    private var playbackHeaders: Map<String, String> = emptyMap()
    private var resumePosition = C.TIME_UNSET
    private var resumePlayWhenReady = true
    private var mediaIsLive = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                // Network interceptors run again for every redirect, unlike the resolver.
                try {
                    MediaUrlPolicy.validate(chain.request().url.toString(), allowDesktopHttp)
                } catch (_: IllegalArgumentException) {
                    throw IOException(INVALID_URL_ERROR)
                }
                val request = if (chain.request().url.isHttps) chain.request() else chain.request().newBuilder()
                    .removeHeader("Authorization").removeHeader("Cookie").removeHeader("Referer").removeHeader("Origin").build()
                chain.proceed(request)
            }
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            AndroidLog.i(TAG, "stateInt=$playbackState")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerView.keepScreenOn = isPlaying
            AndroidLog.i(TAG, "playingBool=$isPlaying")
        }

        override fun onPlayerError(error: PlaybackException) {
            AndroidLog.i(TAG, "errorInt=${error.errorCode}")
            showError(PLAYBACK_ERROR)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Media3 errors may contain signed URLs; only our numeric state logs are enabled.
        Media3Log.setLogLevel(Media3Log.LOG_LEVEL_OFF)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        mediaIsLive = savedInstanceState?.getBoolean(STATE_LIVE, false) ?: false
        resumePosition = if (mediaIsLive) C.TIME_UNSET else {
            savedInstanceState?.getLong(STATE_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET
        }
        resumePlayWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY, true) ?: true

        // Titles come from catalog metadata; never expose a stream URI in the UI/task label.
        title = intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= 200 && !it.contains("://") && it.none(Char::isISOControl)
        } ?: DEFAULT_TITLE
        createContent()
        mediaUrl = try {
            allowDesktopHttp = intent.getBooleanExtra(EXTRA_DESKTOP_HTTP, false)
            val headers = if (intent.hasExtra(EXTRA_HEADERS)) {
                @Suppress("DEPRECATION")
                val bundle = intent.extras?.get(EXTRA_HEADERS) as? Bundle
                    ?: throw IllegalArgumentException()
                if (bundle.size() > 3) throw IllegalArgumentException()
                bundle.keySet().associateWith { key ->
                    @Suppress("DEPRECATION")
                    val value = bundle.get(key)
                    value as? String ?: throw IllegalArgumentException()
                }
            } else emptyMap()
            playbackHeaders = PlaybackHeaders.validate(headers)
            MediaUrlPolicy.validate(intent.getStringExtra(EXTRA_URL).orEmpty(), allowDesktopHttp)
        } catch (_: RuntimeException) {
            showError(INVALID_URL_ERROR)
            null
        }
    }

    override fun onStart() {
        super.onStart()
        startPlayer()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capturePlaybackState()
        outState.putBoolean(STATE_LIVE, mediaIsLive)
        outState.putLong(STATE_POSITION, if (mediaIsLive) C.TIME_UNSET else resumePosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, resumePlayWhenReady)
        super.onSaveInstanceState(outState)
    }

    private fun createContent() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(8), dp(4), dp(16), dp(4))
            setBackgroundColor(SURFACE)
        }
        header.addView(Button(this).apply {
            text = "Volver"
            contentDescription = "Volver a Aetherio"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(SURFACE)
            minHeight = dp(48)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        header.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val videoContainer = FrameLayout(this)
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShowPreviousButton(false)
            setShowNextButton(false)
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setShutterBackgroundColor(BACKGROUND)
            setBackgroundColor(BACKGROUND)
            setArtworkDisplayMode(PlayerView.ARTWORK_DISPLAY_MODE_OFF)
        }
        videoContainer.addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val errorContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        errorText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        errorContent.addView(errorText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        retryButton = Button(this).apply {
            text = "Reintentar"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(SURFACE)
            minHeight = dp(48)
            setOnClickListener {
                isEnabled = false
                releasePlayer()
                startPlayer()
            }
        }
        errorContent.addView(retryButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        errorPanel = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
            visibility = View.GONE
            addView(errorContent, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        videoContainer.addView(errorPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(videoContainer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun startPlayer() {
        val url = mediaUrl ?: return
        if (player != null) return
        errorPanel.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        retryButton.isEnabled = false
        try {
            // This factory also gates child manifests, segments and HLS encryption keys.
            val dataSourceFactory = ResolvingDataSource.Factory(
                OkHttpDataSource.Factory(httpClient)
                    .setDefaultRequestProperties(playbackHeaders)
            ) { dataSpec ->
                val validated = try {
                    MediaUrlPolicy.validate(dataSpec.uri.toString(), allowDesktopHttp)
                } catch (_: IllegalArgumentException) {
                    throw IOException(INVALID_URL_ERROR)
                }
                dataSpec.withUri(Uri.parse(validated))
            }
            val activePlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
            player = activePlayer
            activePlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            activePlayer.setHandleAudioBecomingNoisy(true)
            activePlayer.addListener(playerListener)
            playerView.player = activePlayer
            activePlayer.playWhenReady = resumePlayWhenReady
            val mimeType = VideoFormat.entries.mapNotNull { it.mimeType }
                .firstOrNull { it == intent.getStringExtra(EXTRA_MIME) }
            activePlayer.setMediaItem(
                MediaItem.Builder().setUri(url).setMimeType(mimeType).build(),
                if (mediaIsLive) C.TIME_UNSET else resumePosition
            )
            activePlayer.prepare()
        } catch (_: RuntimeException) {
            releasePlayer()
            showError(PLAYBACK_ERROR)
        }
    }

    private fun capturePlaybackState() {
        val activePlayer = player ?: return
        resumePlayWhenReady = activePlayer.playWhenReady
        val timeline = activePlayer.currentTimeline
        // A placeholder cannot identify live media and must not overwrite a pending VOD seek.
        if (!timeline.isEmpty &&
            !timeline.getWindow(activePlayer.currentMediaItemIndex, Timeline.Window()).isPlaceholder
        ) {
            mediaIsLive = activePlayer.isCurrentMediaItemLive
            resumePosition = if (mediaIsLive) C.TIME_UNSET else activePlayer.currentPosition.coerceAtLeast(0L)
        }
    }

    private fun releasePlayer() {
        capturePlaybackState()
        val activePlayer = player
        player = null
        activePlayer?.removeListener(playerListener)
        playerView.player = null
        activePlayer?.release()
        playerView.keepScreenOn = false
    }

    private fun showError(message: String) {
        playerView.keepScreenOn = false
        playerView.visibility = View.INVISIBLE
        errorText.text = message
        errorPanel.visibility = View.VISIBLE
        retryButton.isEnabled = mediaUrl != null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "com.panoli707.aetherio.nativeapp.player.URL"
        const val EXTRA_TITLE = "com.panoli707.aetherio.nativeapp.player.TITLE"
        const val EXTRA_MIME = "com.panoli707.aetherio.nativeapp.player.MIME"
        const val EXTRA_HEADERS = "com.panoli707.aetherio.nativeapp.player.HEADERS"
        const val EXTRA_DESKTOP_HTTP = "com.panoli707.aetherio.nativeapp.player.DESKTOP_HTTP"
        private const val TAG = "AetherioNativePlayer"
        private const val DEFAULT_TITLE = "Reproducción por enlace"
        private const val INVALID_URL_ERROR = "No se pudo abrir el vídeo. Usa una URL HTTPS directa válida."
        private const val PLAYBACK_ERROR = "No se pudo reproducir el vídeo. Comprueba la conexión o vuelve a buscar fuentes."
        private const val STATE_POSITION = "player.position"
        private const val STATE_PLAY_WHEN_READY = "player.playWhenReady"
        private const val STATE_LIVE = "player.live"
        private val BACKGROUND = Color.rgb(0x20, 0x20, 0x1e)
        private val SURFACE = Color.rgb(0x39, 0x39, 0x35)
    }
}
