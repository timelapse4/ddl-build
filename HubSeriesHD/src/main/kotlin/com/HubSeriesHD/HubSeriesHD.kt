package com.HubSeriesHD

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HubSeriesHD : MainAPI() {
    override var mainUrl = "https://hubserieshd.com"
    override var name = "HubSeriesHD"
    override val hasMainPage = true
    override var lang = "th"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/country/korea/"    to "ซีรีส์เกาหลี",
        "$mainUrl/country/china/"    to "ซีรีส์จีน",
        "$mainUrl/country/japan/"    to "ซีรีส์ญี่ปุ่น",
        "$mainUrl/country/thailand/" to "ซีรีส์ไทย",
        "$mainUrl/genre/romance/"    to "โรแมนติก",
        "$mainUrl/genre/action/"     to "แอ็คชั่น",
        "$mainUrl/genre/comedy/"     to "คอมเมดี้",
        "$mainUrl/"                  to "ล่าสุด",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else "${request.data.trimEnd('/')}page/$page/"
        val document = app.get(url).document
        val items = document.select("article, div.item, div.TPost, div.MovieItem")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.select("article, div.item, div.TPost, div.MovieItem")
            .mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = this.selectFirst("h2 a, h3 a, .Title a, .entry-title a, a.lnk-blk")
            ?: this.selectFirst("a") ?: return null
        val title  = anchor.text().trim().ifEmpty { return null }
        val href   = anchor.attr("href").ifEmpty { return null }
        val poster = this.selectFirst("img")?.let { img ->
            img.attr("data-src").ifEmpty { img.attr("src") }
        }
        return newAnimeSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(
            "h1.entry-title, h1.Title, h1.title, h1"
        )?.text()?.trim() ?: return null

        val poster = document.selectFirst(
            "div.poster img, div.Image img, article img.wp-post-image"
        )?.let { img -> img.attr("data-src").ifEmpty { img.attr("src") } }

        val description = document.selectFirst(
            "div.Description p, div.sinopsis p, div.entry-content p"
        )?.text()?.trim()

        val tags = document.select("div.genres a, .Genre a, a[rel=tag]")
            .map { it.text().trim() }

        val episodes = document.select(
            "ul.episodios li, #episodes li, .episodelist li, .ListEpisodes li"
        ).mapNotNull { ep ->
            val epUrl = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epNum = ep.selectFirst("span.num-epi, .Num, .numerando")
                ?.text()?.filter { it.isDigit() }?.toIntOrNull()
            val epTitle = epNum?.let { "ตอนที่ $it" } ?: ep.selectFirst("a")?.text() ?: "?"
            newEpisode(epUrl) {
                this.name = epTitle
                this.episode = epNum
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.AsianDrama) {
            this.posterUrl = poster
            this.plot      = description
            this.tags      = tags
            addEpisodes(DubStatus.Subbed, episodes.ifEmpty {
                listOf(
                    newEpisode(url) {
                        this.name = "ตอนที่ 1"
                        this.episode = 1
                    }
                )
            })
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("iframe[src], iframe[data-src]")
            .map { it.attr("data-src").ifEmpty { it.attr("src") } }
            .filter { it.startsWith("http") }
            .forEach { loadExtractor(it, data, subtitleCallback, callback) }

        document.select("video source[src], source[src]")
            .map { it.attr("src") }
            .filter { it.startsWith("http") }
            .forEach { videoUrl ->
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name   = name,
                        url    = videoUrl,
                    ) {
                        this.referer = data
                    }
                )
            }

        document.select("script:not([src])").forEach { script ->
            Regex("""['"]?(https?://[^'">\s]*\.(?:m3u8|mp4)[^'">\s]*)['"]?""")
                .findAll(script.data())
                .forEach { match ->
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name   = "$name (script)",
                            url    = match.groupValues[1],
                        ) {
                            this.referer = data
                        }
                    )
                }
        }

        return true
    }
}
