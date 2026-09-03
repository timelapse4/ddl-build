package com.hubserieshd

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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

    private val webViewTimeoutMs = 20_000L

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

    private suspend fun getManifestUrlWithWebView(playUrl: String): String? {
        return suspendCancellableCoroutine<String?> { cont ->
            val captured = AtomicBoolean(false)
            var webView: WebView? = null

            Handler(Looper.getMainLooper()).post {
                try {
                    val ctx = getApplicationContext()
                    if (ctx == null) {
                        if (captured.compareAndSet(false, true)) cont.resume(null)
                        return@post
                    }

                    webView = WebView(ctx.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null

                                val isManifest = reqUrl.contains("uid=") &&
                                        (reqUrl.contains("hls2.php") || reqUrl.contains("play2.php"))

                                if (isManifest && captured.compareAndSet(false, true)) {
                                    cont.resume(reqUrl)
                                    Handler(Looper.getMainLooper()).postDelayed({ destroy() }, 500)
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                    }

                    webView?.loadUrl(playUrl)

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
            }

            cont.invokeOnCancellation {
                if (captured.compareAndSet(false, true)) {
                    Handler(Looper.getMainLooper()).post { webView?.destroy() }
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
