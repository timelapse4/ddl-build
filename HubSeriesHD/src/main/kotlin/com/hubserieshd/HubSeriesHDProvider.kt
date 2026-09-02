package com.hubserieshd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
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
            addQuality(epText)
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // The play page (data) sets an empty <iframe id="refresh"> whose src is
        // populated client-side through several redirects:
        //   playerads1.php -> player.php -> sv2.php -> {hls2|play2}.php?uid=...
        // Rather than reverse the token generation, load the real page in a
        // WebView and intercept the final nanoplayer request that carries the
        // m3u8 manifest.
        val resolver = WebViewResolver(
            Regex("""(hls2|play2)\.php\?uid="""),
            additionalUrls = listOf(Regex("""\.m3u8""")),
        )

        val response = app.get(data, referer = mainUrl, interceptor = resolver)
        val m3u8Url = response.url

        if (!m3u8Url.contains("uid=") && !m3u8Url.contains(".m3u8")) return false

        M3u8Helper.generateM3u8(
            name,
            m3u8Url,
            referer = "https://nanoplayer.zip/",
            headers = mapOf("Origin" to "https://nanoplayer.zip")
        ).forEach(callback)

        return true
    }
}
