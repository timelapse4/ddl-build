// Streamed extension - maintained by timelapse4
// Originally based on work by Kraptor123

package com.timelapse4

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

open class EmbedSporty : EmbedStreams() {
    override val name = "EmbedSporty"
    override val mainUrl = "https://embed.st"
}

open class EmbedStreams : ExtractorApi() {
    override val name = "EmbedStreams"
    override val mainUrl = "https://embedsports.top"
    override val requiresReferer = true

    // How long to wait for the page to settle before clicking play
    private val playClickDelayMs = 2000L
    // How long to wait for an .m3u8 request before giving up
    private val timeoutMs = 15000L

    // Grabs the app's Application context without needing it passed in from the
    // plugin loader. Works on any Android process, independent of CloudStream's own API.
    private fun getApplicationContext(): Context? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            currentApplicationMethod.invoke(null) as? Context
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) = coroutineScope {
        try {
            val videoUrl = withContext(Dispatchers.Main) {
                getVideoUrlWithWebView(url)
            }
            if (videoUrl != null) {
                processVideoUrl(videoUrl, callback)
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun getVideoUrlWithWebView(url: String): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val captured = AtomicBoolean(false)
                var webView: WebView? = null

                try {
                    val ctx = getApplicationContext()
                    if (ctx == null) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    webView = WebView(ctx.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)

                                // Try clicking the play button once the page has settled
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            try {
                                                var playButton = document.querySelector('.jw-icon-display');
                                                if (playButton) {
                                                    playButton.click();
                                                    return 'clicked play button';
                                                }
                                                if (typeof jwplayer !== 'undefined') {
                                                    jwplayer().play();
                                                    return 'played via jwplayer api';
                                                }
                                                return 'play button not found';
                                            } catch(e) {
                                                return 'error: ' + e.message;
                                            }
                                        })();
                                        """.trimIndent()
                                    ) { }
                                }, playClickDelayMs)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null

                                if (reqUrl.endsWith(".m3u8") && !captured.get()) {
                                    if (captured.compareAndSet(false, true)) {
                                        cont.resume(reqUrl)
                                        Handler(Looper.getMainLooper()).postDelayed({ destroy() }, 500)
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                    }

                    webView.loadUrl(url)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (captured.compareAndSet(false, true)) {
                            cont.resume(null)
                            webView?.destroy()
                        }
                    }, timeoutMs)

                } catch (e: Exception) {
                    if (captured.compareAndSet(false, true)) {
                        cont.resume(null)
                        webView?.destroy()
                    }
                }

                cont.invokeOnCancellation {
                    if (captured.compareAndSet(false, true)) {
                        Handler(Looper.getMainLooper()).post { webView?.destroy() }
                    }
                }
            }
        }
    }

    private suspend fun processVideoUrl(videoUrl: String, callback: (ExtractorLink) -> Unit) {
        val sourceLabel = when {
            videoUrl.contains("alpha") -> "Alpha - Most reliable, 720p 30fps"
            videoUrl.contains("bravo") -> "Bravo - High FPS but low bitrate"
            videoUrl.contains("charlie") -> "Charlie - May sometimes have poor quality"
            videoUrl.contains("delta") -> "Delta - Backup, may lag/fail to load"
            videoUrl.contains("echo") -> "Echo - Decent quality"
            videoUrl.contains("foxtrot") -> "Foxtrot"
            videoUrl.contains("golf") -> "Golf - High quality, direct from source"
            videoUrl.contains("intel") -> "Intel - Wide event coverage, questionable quality"
            videoUrl.contains("admin") || videoUrl.contains("poocloud") -> "Admin - Added by admin"
            videoUrl.contains("hotel") -> "Hotel - Very high quality"
            else -> "Streamed"
        }

        callback.invoke(newExtractorLink(
            source = sourceLabel,
            name = sourceLabel,
            url = videoUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.quality = Qualities.Unknown.value
            this.referer = mainUrl
            this.headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.7103.48 Safari/537.36",
                "Origin" to mainUrl,
                "Connection" to "keep-alive"
            )
        })
    }
}
