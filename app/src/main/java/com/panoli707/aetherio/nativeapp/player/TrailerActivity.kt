package com.panoli707.aetherio.nativeapp.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.panoli707.aetherio.nativeapp.R
import com.panoli707.aetherio.nativeapp.data.MediaTrailer

/** Separate from episode playback: trailers never write watch progress or request stream providers. */
class TrailerActivity : ComponentActivity() {
    internal var trailerWebView: WebView? = null
        private set
    internal var ready = false
        private set
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var retry: Button
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if (!ready && !isFinishing) showError("YouTube no respondió. Comprueba la conexión y vuelve a intentarlo.") }
    private var id: String? = null
    private var foreground = false

    @SuppressLint("SetJavaScriptEnabled") // Required by the official YouTube IFrame API; never used for arbitrary pages.
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Aetherio)
        super.onCreate(savedInstanceState)
        id = TrailerEmbed.validId(intent.getStringExtra(EXTRA_ID))
        if (id == null) { finish(); return }
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolbar.addView(Button(this).apply {
            text = "Volver"; contentDescription = "Cerrar tráiler"; setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-2, -2))
        toolbar.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)?.take(200).orEmpty().ifBlank { "Tráiler" }
            setTextColor(Color.WHITE); textSize = 18f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(12, 12, 12, 12)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(toolbar)
        status = TextView(this).apply { setTextColor(Color.WHITE); setPadding(16, 8, 16, 8) }
        retry = Button(this).apply { text = "Reintentar tráiler"; setOnClickListener { loadTrailer() } }
        val web = WebView(this)
        trailerWebView = web
        web.setBackgroundColor(Color.BLACK)
        web.contentDescription = "Reproductor de tráiler integrado"
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                if (request.isForMainFrame) !TrailerEmbed.allowedMainFrame(request.url.toString())
                else request.url.scheme !in listOf("https", "about")

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError(TrailerEmbed.error(0))
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showError("No se pudo establecer una conexión segura con el reproductor.")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                // Only messages from our local document affect status. No JavaScript/native bridge is exposed.
                if (message.sourceId() != TrailerEmbed.BASE_URL) return false
                when {
                    message.message() == "AETHERIO_TRAILER_READY" -> {
                        ready = true; handler.removeCallbacks(timeout); status.visibility = View.GONE; retry.visibility = View.GONE
                        if (!foreground) pauseTrailer()
                    }
                    message.message().startsWith("AETHERIO_TRAILER_ERROR:") ->
                        showError(TrailerEmbed.error(message.message().substringAfter(':').toIntOrNull() ?: 0))
                    message.message().startsWith("AETHERIO_TRAILER_STATE:") -> {
                        if (message.message().substringAfter(':') == "1" && foreground) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else if (message.message().substringAfter(':') == "1") pauseTrailer()
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) { showFullscreen(view, callback) }
            override fun onHideCustomView() { hideFullscreen() }
        }
        content.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(status)
        content.addView(retry)
        content.addView(TextView(this).apply {
            text = "YouTube · Reproducción integrada. Algunos vídeos tienen restricciones de región, edad o inserción."
            setTextColor(Color.LTGRAY); setPadding(16, 8, 16, 8)
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreenView != null) hideFullscreen() else finish() }
        })
        loadTrailer()
    }

    private fun loadTrailer() {
        val videoId = id ?: return
        ready = false
        status.text = "Cargando tráiler…"; status.visibility = View.VISIBLE; retry.visibility = View.GONE
        handler.removeCallbacks(timeout)
        // An HTTPS app-ID base URL supplies the HTTP Referer required by YouTube (including error 153).
        trailerWebView?.loadDataWithBaseURL(TrailerEmbed.BASE_URL, TrailerEmbed.document(videoId), "text/html", "UTF-8", TrailerEmbed.BASE_URL)
        handler.postDelayed(timeout, 25_000)
    }

    private fun showError(message: String) {
        handler.removeCallbacks(timeout)
        status.text = message; status.visibility = View.VISIBLE; retry.visibility = View.VISIBLE
        pauseTrailer()
    }

    internal fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) { callback.onCustomViewHidden(); return }
        fullscreenView = view; fullscreenCallback = callback; content.visibility = View.GONE
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        WindowInsetsControllerCompat(window, root).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    internal fun hideFullscreen() {
        fullscreenView?.let { root.removeView(it) }
        fullscreenView = null
        val callback = fullscreenCallback; fullscreenCallback = null
        content.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
        callback?.onCustomViewHidden()
    }

    private fun pauseTrailer() {
        trailerWebView?.evaluateJavascript("if(typeof pauseTrailer==='function')pauseTrailer();", null)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        foreground = false
        handler.removeCallbacks(timeout)
        pauseTrailer(); trailerWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume(); foreground = true; trailerWebView?.onResume()
        if (!ready && id != null) { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 25_000) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::content.isInitialized) hideFullscreen()
        trailerWebView?.let { web ->
            web.stopLoading(); web.loadUrl("about:blank"); (web.parent as? ViewGroup)?.removeView(web); web.destroy()
        }
        trailerWebView = null
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_ID = "trailer_id"
        internal const val EXTRA_TITLE = "trailer_title"
        fun intent(context: Context, trailer: MediaTrailer): Intent {
            require(TrailerEmbed.validId(trailer.id) != null)
            return Intent(context, TrailerActivity::class.java).putExtra(EXTRA_ID, trailer.id).putExtra(EXTRA_TITLE, trailer.title)
        }
    }
}
