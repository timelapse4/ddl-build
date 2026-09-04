package com.hubserieshd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addPoster
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class HubSeriesHDProvider : MainAPI() {

    override var mainUrl = "https://hubserieshds.com"
    override var name = "HubSeriesHD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    companion object {
        // ต้องเป็น UA ตัวเดียวกันทั้งตอน capture และตอน playback
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // hls2.php / play2.php ที่มี uid= คือ manifest จริง
        val MANIFEST_REGEX = Regex("""(hls2|play2)\.php\?[^"'\s]*uid=""")
        val M3U8_REGEX = Regex("""\.m3u8(\?|$)""")
    }

    override val mainPage = mainPageOf(
        "" to "หน้าแรก",
        "/series" to "ซีรีส์ทั้งหมด",
        "/popular" to "ยอดนิยม",
    )

    // ---------------------------------------------------------------- main page

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page <= 1) "$mainUrl${request.data}"
                  else "$mainUrl${request.data}?page=$page"

        val document = app.get(
            url,
            referer = "$mainUrl/",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        val items = document.select("a.card").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.attr("href").ifBlank { return null }
        val title = this.selectFirst(".card-title, .title, h3")?.text()?.trim()
            ?: this.attr("title").trim().ifBlank { return null }
        val poster = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }

        return newTvSeriesSearchResponse(title, fixUrl(href), TvType.AsianDrama) {
            this.posterUrl = poster?.let { fixUrl(it) }
        }
    }

    // ------------------------------------------------------------------ search

    override suspend fun search(query: String): List<SearchResponse> {
        // FIX: encode query กัน space / ภาษาไทย ทำ URL พัง
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = app.get(
            "$mainUrl/search?name=$encoded",
            referer = "$mainUrl/",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        return document.select("a.card").mapNotNull { it.toSearchResult() }
    }

    // -------------------------------------------------------------------- load

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(
            url,
            referer = "$mainUrl/",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        val rawTitle = document.selectFirst("h1, .entry-title")?.text()?.trim().orEmpty()
        val title = rawTitle
            .replace(Regex("""\s*(EP|Ep|ep)[.\s]*\d+.*$"""), "")
            .replace(Regex("""\s*ตอนที่\s*\d+.*$"""), "")
            .trim()
            .ifBlank { rawTitle }

        val poster = document.selectFirst(".poster img, .thumb img, meta[property=og:image]")
            ?.let { it.attr("content").ifBlank { it.attr("data-src") }.ifBlank { it.attr("src") } }

        val description = document.selectFirst(".description, .entry-content p, meta[name=description]")
            ?.let { it.attr("content").ifBlank { it.text() } }?.trim()

        val episodes = document.select(".epgrid a.epitem").mapIndexedNotNull { idx, el ->
            val href = el.attr("href").ifBlank { return@mapIndexedNotNull null }
            val epText = el.text().trim()
            val epNum = Regex("""\d+""").find(epText)?.value?.toIntOrNull() ?: (idx + 1)

            newEpisode(fixUrl(href)) {
                this.name = epText.ifBlank { "ตอนที่ $epNum" }
                this.episode = epNum
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.plot = description
                addPoster(poster?.let { fixUrl(it) })
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.plot = description
                addPoster(poster?.let { fixUrl(it) })
            }
        }
    }

    // --------------------------------------------------------------- loadLinks

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1) ดึง iframe จริงจากหน้า episode — อย่าให้ WebView ไปนั่งเดาเอง
        val iframeUrl = extractIframe(data) ?: return false
        val iframeOrigin = originOf(iframeUrl)

        // 2) ให้ WebViewResolver ดักเอง (มัน attach window / จัดการ cookie / UA ให้ครบ)
        val resolved = app.get(
            iframeUrl,
            referer = "$data",
            headers = mapOf("User-Agent" to USER_AGENT),
            interceptor = WebViewResolver(
                interceptUrl = MANIFEST_REGEX,
                additionalUrls = listOf(M3U8_REGEX),
                useOkhttp = false,
                timeout = 30_000L
            )
        )

        val manifestUrl = resolved.url.takeIf {
            MANIFEST_REGEX.containsMatchIn(it) || M3U8_REGEX.containsMatchIn(it)
        } ?: return false

        // 3) hls2.php มักไม่ใช่ m3u8 ตรง ๆ — ตาม redirect / อ่าน JSON ก่อน
        val finalUrl = resolveManifest(manifestUrl, iframeOrigin)

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = finalUrl,
                type = ExtractorLinkType.M3U8
            ) {
                this.quality = Qualities.Unknown.value
                this.referer = "$iframeOrigin/"          // ไม่ hardcode โดเมนแล้ว
                this.headers = mapOf(
                    "User-Agent"      to USER_AGENT,      // ต้องตรงกับตอน capture
                    "Origin"          to iframeOrigin,
                    "Accept"          to "*/*",
                    "Accept-Language" to "th,en-US;q=0.9,en;q=0.8"
                )
            }
        )
        return true
    }

    // ----------------------------------------------------------------- helpers

    /** หา iframe ของ player จากหน้า episode รองรับทั้ง src ปกติ, data-src และ lazy */
    private suspend fun extractIframe(pageUrl: String): String? {
        val document = app.get(
            pageUrl,
            referer = "$mainUrl/",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        // ลอง iframe ตรง ๆ ก่อน
        val direct = document.select("iframe")
            .map { it.attr("src").ifBlank { it.attr("data-src") } }
            .firstOrNull { it.isNotBlank() }

        if (!direct.isNullOrBlank()) return fixProtocol(direct)

        // บางหน้าโยน iframe ผ่าน JS: player.src = "..." / file: "..."
        val html = document.html()
        return Regex("""["'](https?://[^"']*(?:nanoplayer|player|embed)[^"']*)["']""")
            .find(html)?.groupValues?.get(1)
            ?.let { fixProtocol(it) }
    }

    /**
     * hls2.php มักตอบเป็น redirect หรือ JSON { "file": "...m3u8" }
     * ถ้าโยนดิบ ๆ ให้ ExoPlayer แบบ M3U8 มันจะ parse ไม่ผ่านแล้วเงียบ
     */
    private suspend fun resolveManifest(url: String, origin: String): String {
        if (M3U8_REGEX.containsMatchIn(url)) return url

        return try {
            val res = app.get(
                url,
                referer = "$origin/",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Origin"     to origin,
                    "Accept"     to "*/*"
                ),
                allowRedirects = true
            )

            // ตาม redirect ไปเจอ m3u8 แล้ว
            if (M3U8_REGEX.containsMatchIn(res.url)) return res.url

            val body = res.text.trim()
            // ตอบเป็น playlist ดิบ
            if (body.startsWith("#EXTM3U")) return url
            // ตอบเป็น JSON
            Regex(""""(?:file|url|source|hls)"\s*:\s*"([^"]+\.m3u8[^"]*)"""")
                .find(body)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: url
        } catch (_: Exception) {
            url
        }
    }

    private fun originOf(url: String): String = try {
        URI(url).let { "${it.scheme}://${it.host}" }
    } catch (_: Exception) {
        mainUrl
    }

    private fun fixProtocol(url: String): String = when {
        url.startsWith("//")  -> "https:$url"
        url.startsWith("/")   -> "$mainUrl$url"
        url.startsWith("http") -> url
        else -> "https://$url"
    }
}
