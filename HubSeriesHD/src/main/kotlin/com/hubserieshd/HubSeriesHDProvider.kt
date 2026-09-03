
package com.hubserieshd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import org.jsoup.nodes.Element

class HubSeriesHDProvider : MainAPI() {
    override var mainUrl = "https://hubserieshds.com"
    override var name = "HubSeriesHD"
    override val hasMainPage = true
    override var lang = "th"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/country/korea/" to "ซีรี่ย์เกาหลี",
        "$mainUrl/country/usa/" to "ซีรี่ย์ฝรั่ง",
        "$mainUrl/country/china/" to "ซีรี่ย์จีน"
    )

    // ---------- helpers ----------

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this
        val href = a.attr("href").let { fixUrl(it) }
        val title = a.selectFirst(".title")?.text()?.trim() ?: return null
        val posterUrl = a.selectFirst("img.poster")?.attr("src")?.let { fixUrlNull(it) }
        val epText = a.selectFirst(".ep")?.text()?.trim()

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
            if (!epText.isNullOrBlank()) addQuality(epText)
        }
    }

    // ---------- main page / listing ----------

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}$page/"
        val document = app.get(url).document

        val items = document.select("main.wrap .grid a.card, main.wrap a.card")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items)
    }

    // ---------- search ----------

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?name=$query").document
        return document.select("a.card").mapNotNull { it.toSearchResult() }
    }

    // ---------- detail page ----------

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?.replace(Regex("EP\\.[0-9,\\-–]+.*"), "")?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "Unknown"

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val episodes = document.select(".epgrid a.epitem").mapIndexedNotNull { index, el ->
            val epHref = el.attr("href").let { fixUrl(it) }
            val epLabel = el.selectFirst(".e")?.text()?.trim() ?: return@mapIndexedNotNull null
            val epNum = Regex("""(\d+)""").find(epLabel)?.groupValues?.get(1)?.toIntOrNull()
                ?: (index + 1)
            val epDate = el.selectFirst(".d")?.text()?.trim()

            newEpisode(epHref) {
                this.name = epLabel
                this.episode = epNum
                this.description = epDate
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ---------- links ----------
    // hubserieshds.com sets its player <iframe> src purely via client-side JS
    // with no static token exposed anywhere in the page source. Instead of
    // reversing that logic, load the real /play/{id}/ page in a hidden WebView
    // and grab the nanoplayer manifest request once the page's own JS fires it.

    private val webViewTimeoutMs = 25_000L
    private val tapDelayMs = 3_000L

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

    // Locates the player <iframe id="refresh"> on the (same-origin) top page via
    // JS and simulates a real finger tap at its exact center. A blind tap at
    // screen-center risks landing on one of this site's many ad overlays
    // instead of the actual play button, so we ask the page itself where the
    // player sits first. Coordinates are returned as fractions of the
    // viewport (0.0-1.0) so CSS-pixel vs device-pixel differences cancel out.
    private fun locateAndTapPlayer(webView: WebView, viewWidthPx: Int, viewHeightPx: Int) {
        val script = """
            (function() {
                try {
                    var el = document.getElementById('refresh') ||
                              document.querySelector('iframe');
                    if (!el) return 'none';
                    var r = el.getBoundingClientRect();
                    var cx = (r.left + r.width / 2) / window.innerWidth;
                    var cy = (r.top + r.height / 2) / window.innerHeight;
                    return cx + ',' + cy;
                } catch (e) {
                    return 'error:' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            val cleaned = result?.trim('"') ?: "none"
            val parts = cleaned.split(",")
            val fx = parts.getOrNull(0)?.toFloatOrNull()
            val fy = parts.getOrNull(1)?.toFloatOrNull()

            val x = if (fx != null) (fx * viewWidthPx) else viewWidthPx / 2f
            val y = if (fy != null) (fy * viewHeightPx) else viewHeightPx / 2f

            val now = android.os.SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0)
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        }
    }

    private suspend fun getManifestUrlWithWebView(playUrl: String): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { cont ->
                val captured = AtomicBoolean(false)
                var webView: WebView? = null
                val viewWidthPx = 1080
                val viewHeightPx = 1920

                try {
                    val ctx = getApplicationContext()
                    if (ctx == null) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    val wv = WebView(ctx.applicationContext)
                    webView = wv
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.mediaPlaybackRequiresUserGesture = false

                    // Needs real layout dimensions for dispatchTouchEvent to mean anything,
                    // even though the view is never attached to a visible window.
                    val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(viewWidthPx, android.view.View.MeasureSpec.EXACTLY)
                    val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(viewHeightPx, android.view.View.MeasureSpec.EXACTLY)
                    wv.measure(widthSpec, heightSpec)
                    wv.layout(0, 0, viewWidthPx, viewHeightPx)

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Give the nested iframes a moment to finish loading, then tap
                            // twice (some sites need a second tap to dismiss an ad overlay
                            // before the real player becomes clickable).
                            Handler(Looper.getMainLooper()).postDelayed({
                                view?.let { locateAndTapPlayer(it, viewWidthPx, viewHeightPx) }
                            }, tapDelayMs)
                            Handler(Looper.getMainLooper()).postDelayed({
                                view?.let { locateAndTapPlayer(it, viewWidthPx, viewHeightPx) }
                            }, tapDelayMs + 4000L)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null

                            val isManifest = reqUrl.contains("uid=") &&
                                    (reqUrl.contains("hls2.php") || reqUrl.contains("play2.php"))

                            if (isManifest && captured.compareAndSet(false, true)) {
                                cont.resume(reqUrl)
                                Handler(Looper.getMainLooper()).postDelayed({ webView?.destroy() }, 500)
                            }

                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    wv.loadUrl(playUrl)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (captured.compareAndSet(false, true)) {
                            cont.resume(null)
                            webView?.destroy()
                        }
                    }, webViewTimeoutMs)

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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val manifestUrl = getManifestUrlWithWebView(data) ?: return false

        callback.invoke(newExtractorLink(
            source = name,
            name = name,
            url = manifestUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.quality = Qualities.Unknown.value
            this.referer = "https://nanoplayer.zip/"
            this.headers = mapOf(
                "Origin" to "https://nanoplayer.zip"
            )
        })

        return true
    }
}
