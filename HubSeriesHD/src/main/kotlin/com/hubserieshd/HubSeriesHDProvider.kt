package com.hubserieshd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HubSeriesHDProvider : MainAPI() {

    override var mainUrl = "https://hubserieshds.com"
    override var name = "HubSeriesHD"
    override var lang = "th"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/episodes/page/%d/"          to "ตอนอัปเดตล่าสุด",
        "$mainUrl/country/korea/page/%d/"     to "ซีรีส์เกาหลี",
        "$mainUrl/country/china/page/%d/"     to "ซีรีส์จีน",
        "$mainUrl/tvshows/page/%d/"           to "ซีรีส์ทั้งหมด",
        "$mainUrl/movies/page/%d/"            to "ภาพยนตร์",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data.format(page), referer = "$mainUrl/").document

        val items = doc.select("div.items article, article.item, div.result-item article")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(
            selectFirst("div.poster a[href], div.data h3 a[href], div.details div.title a[href], h3 a[href]")
                ?.attr("href")
        ) ?: return null

        if (href.contains("/genre/") || href.contains("/country/")) return null

        val img = selectFirst("div.poster img, img")

        val title = selectFirst("div.data h3, div.details div.title, h3")
            ?.text()?.trim()?.ifBlank { null }
            ?: img?.attr("alt")?.trim()?.ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            img?.attr("data-src")?.ifBlank { null }
                ?: img?.attr("data-lazy-src")?.ifBlank { null }
                ?: img?.attr("srcset")?.substringBefore(" ")?.ifBlank { null }
                ?: img?.attr("src")
        )

        val isSeries = href.contains("/tvshows/") || href.contains("/episodes/")

        return newMovieSearchResponse(
            title, href, if (isSeries) TvType.TvSeries else TvType.Movie
        ) { this.posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.replace(" ", "+")}", referer = "$mainUrl/").document
        return doc.select("div.result-item article, div.items article, article.item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    // ---------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = "$mainUrl/").document

        val title = doc.selectFirst("div.data > h1, h1.entry-title, h1")
            ?.text()?.trim().orEmpty()

        val poster = fixUrlNull(
            doc.selectFirst("div.poster > img, div.sheader div.poster img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val plot = doc.selectFirst("div#info div.wp-content p, div.wp-content p, #info p")
            ?.text()?.trim()

        val year = doc.selectFirst("span.date, div.data span")
            ?.text()
            ?.let { Regex("""(19|20)\d{2}""").find(it)?.value }
            ?.toIntOrNull()

        val tags = doc.select("div.sgeneros a").map { it.text() }

        // DooPlay season/episode structure
        val episodes = doc.select("div#seasons div.se-c").flatMap { seasonBlock ->
            val seasonNum = seasonBlock.selectFirst("span.se-t")
                ?.text()?.trim()?.toIntOrNull() ?: 1

            seasonBlock.select("ul.episodios li").mapNotNull { li ->
                val a = li.selectFirst("div.episodiotitle a[href], a[href]") ?: return@mapNotNull null
                val epUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null

                val numerando = li.selectFirst("div.numerando")?.text()?.trim().orEmpty()
                val epNum = numerando.substringAfter("-").trim().toIntOrNull()
                    ?: Regex("""(\d+)""").find(a.text())?.groupValues?.get(1)?.toIntOrNull()

                val epPoster = fixUrlNull(
                    li.selectFirst("div.imagen img")
                        ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                )

                newEpisode(epUrl) {
                    this.name = a.text().trim().ifBlank { "ตอนที่ $epNum" }
                    this.season = seasonNum
                    this.episode = epNum
                    this.posterUrl = epPoster
                }
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    // ---------------------------------------------------------------
    // Load links — DooPlay AJAX player
    // ---------------------------------------------------------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data, referer = "$mainUrl/").document
        val embeds = linkedSetOf<String>()

        // 1) DooPlay player options → admin-ajax
        doc.select("#playeroptionsul li.dooplay_player_option, ul#playeroptionsul li").forEach { li ->
            val post = li.attr("data-post").ifBlank { return@forEach }
            val nume = li.attr("data-nume").ifBlank { return@forEach }
            val type = li.attr("data-type").ifBlank { "movie" }

            val json = runCatching {
                app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post"   to post,
                        "nume"   to nume,
                        "type"   to type
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text
            }.getOrNull().orEmpty()

            if (json.isBlank()) return@forEach

            val raw = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                .find(json)?.groupValues?.get(1)
                ?.replace("\\/", "/")
                ?.replace("\\\"", "\"")
                .orEmpty()

            if (raw.isBlank()) return@forEach

            val resolved = if (raw.contains("<iframe", true)) {
                Jsoup.parse(raw).selectFirst("iframe")?.attr("src").orEmpty()
            } else raw

            fixUrlNull(resolved)?.let { embeds.add(it) }
        }

        // 2) iframe ตรง ๆ (เผื่อบางหน้า)
        doc.select("div.pframe iframe[src], iframe[src], iframe[data-src]").forEach { f ->
            val src = f.attr("src").ifBlank { f.attr("data-src") }
            fixUrlNull(src)?.let { embeds.add(it) }
        }

        val list = embeds.filter { it.startsWith("http") && !it.contains("youtube") }
        if (list.isEmpty()) return false

        var found = false
        list.take(5).forEach { if (resolveEmbed(it, data, callback)) found = true }
        return found
    }

    private suspend fun resolveEmbed(
        embedUrl: String,
        pageRef: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // ลอง extractor มาตรฐานของ CloudStream ก่อน
        val ok = runCatching {
            loadExtractor(embedUrl, pageRef, { }, callback)
        }.getOrDefault(false)
        if (ok) return true

        // fallback: WebView intercept
        val rx = Regex("""(hls2\.php|master\.txt|index\.m3u8|\.m3u8|\.mp4)""")

        val res = runCatching {
            app.get(
                embedUrl,
                referer = "$mainUrl/",
                interceptor = WebViewResolver(
                    interceptUrl = rx,
                    additionalUrls = listOf(rx),
                    useOkhttp = false,
                    timeout = 25_000L
                )
            )
        }.getOrNull() ?: return false

        val hit = res.url
        if (!rx.containsMatchIn(hit)) return false

        val host = getBaseUrl(embedUrl)
        val isM3u8 = hit.contains(".m3u8") || hit.contains("hls2.php")

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "$name — ${getHostName(host)}",
                url = hit,
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

    private fun getBaseUrl(url: String) = runCatching {
        java.net.URI(url).let { "${it.scheme}://${it.host}" }
    }.getOrDefault(mainUrl)

    private fun getHostName(url: String) = runCatching {
        java.net.URI(url).host.removePrefix("www.")
    }.getOrDefault("server")

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
