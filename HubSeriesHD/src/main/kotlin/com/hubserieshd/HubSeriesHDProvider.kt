package com.hubserieshd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addEpisode
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Element

class HubSeriesHDProvider : MainAPI() {

    override var mainUrl = "https://hubserieshd.com"
    override var name = "HubSeriesHD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // ---------------------------------------------------------------
    // Main page
    // ---------------------------------------------------------------

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/"                     to "อัปเดตล่าสุด",
        "$mainUrl/category/series/page/%d/"     to "ซีรีส์",
        "$mainUrl/category/movie/page/%d/"      to "ภาพยนตร์",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data.format(page)
        val doc = app.get(url, referer = "$mainUrl/").document

        val items = doc.select("article, div.item, div.post, li.item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(link.attr("href")) ?: return null

        val title = this.selectFirst("h2, h3, .title, .entry-title")
            ?.text()
            ?.trim()
            ?: link.attr("title").ifBlank { return null }

        val img = this.selectFirst("img")
        val poster = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("data-lazy-src")?.ifBlank { null }
                ?: img?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(url, referer = "$mainUrl/").document

        return doc.select("article, div.item, div.post, li.item")
            .mapNotNull { it.toSearchResult() }
    }

    // ---------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/").document

        val title = doc.selectFirst("h1.entry-title, h1.title, h1")
            ?.text()
            ?.trim()
            .orEmpty()

        val poster = doc.selectFirst("div.poster img, .entry-content img, meta[property=og:image]")
            ?.let { it.attr("content").ifBlank { it.attr("data-src").ifBlank { it.attr("src") } } }

        val description = doc.selectFirst("div.entry-content p, .description, meta[name=description]")
            ?.let { it.attr("content").ifBlank { it.text() } }
            ?.trim()

        val year = Regex("""(19|20)\d{2}""")
            .find(doc.text())
            ?.value
            ?.toIntOrNull()

        // ---- หา episode list ----
        val epElements = doc.select(
            "div.episodios li a, ul.episode-list li a, .eplister ul li a, a[href*=ep], a[href*=ตอนที่]"
        ).filter { it.attr("href").isNotBlank() }

        return if (epElements.isNotEmpty()) {

            val episodes = epElements.mapIndexedNotNull { index, el ->
                val epUrl = fixUrlNull(el.attr("href")) ?: return@mapIndexedNotNull null
                val epName = el.text().trim().ifBlank { "ตอนที่ ${index + 1}" }
                val epNum = Regex("""(\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                    ?: (index + 1)

                newEpisode(epUrl) {
                    this.name = epName
                    this.episode = epNum
                }
            }.distinctBy { it.data }

            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.year = year
            }

        } else {

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
                this.year = year
            }
        }
    }

    // ---------------------------------------------------------------
    // Load links
    // ---------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data, referer = "$mainUrl/").document

        // เก็บ embed url ทุกแบบที่เจอ
        val embeds = mutableListOf<String>()

        doc.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").forEach { f ->
            val src = f.attr("src").ifBlank {
                f.attr("data-src").ifBlank { f.attr("data-litespeed-src") }
            }
            fixUrlNull(src)?.let { embeds.add(it) }
        }

        // บางธีมซ่อน embed ไว้ใน data-attribute ของปุ่มเลือกเซิร์ฟเวอร์
        doc.select("[data-embed], [data-player], [data-url]").forEach { e ->
            val src = e.attr("data-embed").ifBlank {
                e.attr("data-player").ifBlank { e.attr("data-url") }
            }
            if (src.startsWith("http")) embeds.add(src)
        }

        // เผื่อ embed ฝังในสคริปต์แบบ string ตรง ๆ
        Regex("""["'](https?://[^"']*(?:embed|player|/e/|/v/)[^"']*)["']""")
            .findAll(doc.html())
            .forEach { embeds.add(it.groupValues[1]) }

        val unique = embeds.distinct().filter { it.startsWith("http") }
        if (unique.isEmpty()) return false

        var found = false

        unique.take(4).forEach { embedUrl ->
            if (resolveEmbed(embedUrl, callback)) found = true
        }

        return found
    }

    /**
     * ใช้ WebViewResolver ให้ WebView โหลด player จริง
     * แล้วดักจับ request ที่เป็น m3u8 / hls2.php / master playlist
     */
    private suspend fun resolveEmbed(
        embedUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val interceptRegex = Regex("""(hls2\.php|master\.txt|index\.m3u8|\.m3u8|\.mp4)""")

        val resolver = WebViewResolver(
            interceptUrl = interceptRegex,
            additionalUrls = listOf(interceptRegex),
            useOkhttp = false,
            timeout = 25_000L
        )

        val response = runCatching {
            app.get(
                embedUrl,
                referer = "$mainUrl/",
                interceptor = resolver
            )
        }.getOrNull() ?: return false

        val hitUrl = response.url
        if (!interceptRegex.containsMatchIn(hitUrl)) return false

        val host = getBaseUrl(embedUrl)

        // hls2.php มักคืน JSON ที่มี field "file" ชี้ไป m3u8 อีกที
        val finalUrl = if (hitUrl.contains("hls2.php") && !hitUrl.contains(".m3u8")) {
            val body = runCatching { response.text }.getOrNull().orEmpty()
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.replace("\\/", "/")
                ?: hitUrl
        } else {
            hitUrl
        }

        val isM3u8 = finalUrl.contains(".m3u8") || finalUrl.contains("hls2.php")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$name — ${getHostName(host)}",
                url = finalUrl,
                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = host
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "Origin" to host,
                    "Referer" to "$host/",
                    "User-Agent" to USER_AGENT
                )
            }
        )

        return true
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun getBaseUrl(url: String): String {
        return runCatching {
            java.net.URI(url).let { "${it.scheme}://${it.host}" }
        }.getOrDefault(mainUrl)
    }

    private fun getHostName(url: String): String {
        return runCatching {
            java.net.URI(url).host.removePrefix("www.")
        }.getOrDefault("server")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
