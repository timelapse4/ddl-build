package com.HubSeriesHD

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HubSeriesHD : MainAPI() {
    override var mainUrl = "https://hubserieshd.com"
    override var name = "HubSeriesHD"
    override val hasMainPage = true
    override var lang = "th"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    // ─── selector กว้างที่สุด ลอง match ทุก WordPress theme ───
    // ถ้า site เปลี่ยน theme แค่เพิ่ม selector ใหม่ในนี้
    private val cardSelectors = listOf(
        // ── Blocksy / GeneratePress / Kadence ──
        "article.post",
        "article.type-post",
        // ── Ova, Rima, Ripro (theme ซีรีส์ไทยนิยม) ──
        "div.item",
        "div.film_list-wrap div.flw-item",
        "div.TPostMv",
        "div.TPost",
        // ── Sora, Muvipro ──
        "div.MovieItem",
        "div.movieItem",
        // ── Elementor cards ──
        "div.elementor-post",
        // ── Generic WP fallback ──
        "article",
        "li.post-item",
        "div.post-item",
    )

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

    // ─── ดึง card elements จาก document — ลอง selector ทีละตัว ───
    private fun Document.findCards(): List<Element> {
        for (sel in cardSelectors) {
            val found = this.select(sel)
            if (found.isNotEmpty()) {
                Log.d("HubSeriesHD", "✅ cardSelector matched: '$sel' → ${found.size} items")
                return found.toList()
            }
        }
        // ถ้าไม่เจอเลย → log ทุก top-level element เพื่อ debug
        Log.w("HubSeriesHD", "⚠️ No card selector matched! Top body children:")
        this.body()?.children()?.take(10)?.forEach {
            Log.w("HubSeriesHD", "  <${it.tagName()} class='${it.className()}'>")
        }
        return emptyList()
    }

    // ─── ดึง title + href + poster จาก card element ───
    private fun Element.toSearchResult(): SearchResponse? {
        // Title: ลอง selector หลายแบบ
        val anchor = this.selectFirst(
            "h2 a, h3 a, h4 a, " +
            ".Title a, .entry-title a, " +
            "a.lnk-blk, a.film-poster-ahref, " +
            ".film-detail a, " +
            "a[rel~=bookmark]"
        ) ?: this.selectFirst("a") ?: return null

        val title = this.selectFirst(
            ".Title, .entry-title, h2, h3, h4, .film-name, .dynamic-name"
        )?.text()?.trim()
            ?: anchor.text().trim()
            ?: anchor.attr("title").trim()

        if (title.isEmpty()) return null

        val href = anchor.attr("abs:href").ifEmpty {
            anchor.attr("href").ifEmpty { return null }
        }

        // Poster: data-src ก่อน (lazy-load) แล้ว src
        val poster = this.selectFirst("img")?.let { img ->
            img.attr("data-src").ifEmpty {
                img.attr("data-lazy-src").ifEmpty {
                    img.attr("src")
                }
            }
        }.let { if (it.isNullOrBlank() || it.contains("data:image")) null else it }

        return newAnimeSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    // ─────────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else "${request.data.trimEnd('/')}page/$page/"
        val document = app.get(url).document
        val items = document.findCards().mapNotNull { it.toSearchResult() }
        Log.d("HubSeriesHD", "getMainPage '${request.name}' page=$page → ${items.size} items")
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.findCards().mapNotNull { it.toSearchResult() }
    }

    // ─────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(
            "h1.entry-title, h1.Title, h1.title, h1"
        )?.text()?.trim() ?: return null

        val poster = document.selectFirst(
            "div.poster img, div.Image img, " +
            "article img.wp-post-image, " +
            ".singleBanner img, .postThumbnail img, " +
            "img.attachment-post-thumbnail"
        )?.let { img -> img.attr("data-src").ifEmpty { img.attr("src") } }

        val description = document.selectFirst(
            "div.Description p, div.sinopsis p, div.entry-content > p, " +
            ".wp-block-paragraph, .excerpt p"
        )?.text()?.trim()

        val tags = document.select(
            "div.genres a, .Genre a, a[rel=tag], .tags a, .cats a"
        ).map { it.text().trim() }

        // Episode list — หลาย theme ใช้ selector ต่างกัน
        val episodes = document.select(
            "ul.episodios li, #episodes li, " +
            ".episodelist li, .ListEpisodes li, " +
            ".ep_list li, .episodes-list li, " +
            "ul.eplist li"
        ).mapNotNull { ep ->
            val epUrl   = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epNum   = ep.selectFirst("span.num-epi, .Num, .numerando, .ep-number")
                ?.text()?.filter { it.isDigit() }?.toIntOrNull()
            val epTitle = epNum?.let { "ตอนที่ $it" } ?: ep.selectFirst("a")?.text() ?: "?"
            newEpisode(epUrl) {
                this.name    = epTitle
                this.episode = epNum
            }
        }.reversed()

        return newAnimeLoadResponse(title, url, TvType.AsianDrama) {
            this.posterUrl = poster
            this.plot      = description
            this.tags      = tags
            addEpisodes(DubStatus.Subbed, episodes.ifEmpty {
                listOf(newEpisode(url) { this.name = "ตอนที่ 1"; this.episode = 1 })
            })
        }
    }

    // ─────────────────────────────────────────────
    // Load Links + Obfuscated JS decoder
    // ─────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1) iframe src ตรงๆ
        document.select("iframe[src], iframe[data-src]")
            .map { it.attr("data-src").ifEmpty { it.attr("src") } }
            .filter { it.isNotBlank() && (it.startsWith("http") || it.startsWith("//")) }
            .forEach { rawSrc ->
                val iframeUrl = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }

        // 2) <video><source>
        document.select("video source[src], source[src]")
            .map { it.attr("src") }
            .filter { it.startsWith("http") }
            .forEach { videoUrl ->
                callback.invoke(
                    newExtractorLink(source = name, name = name, url = videoUrl) {
                        this.referer = data
                    }
                )
            }

        // 3) Obfuscated charCode array script
        document.select("script:not([src])").forEach { scriptEl ->
            val scriptText = scriptEl.data()
            if (!scriptText.contains(".src") ||
                (!scriptText.contains("fromCharCode") && !scriptText.contains("charCodeAt"))
            ) return@forEach

            val iframeSrc = decodeObfuscatedSrc(scriptText)
            if (iframeSrc != null) {
                val fullUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                Log.d("HubSeriesHD", "🔓 Decoded iframe src: $fullUrl")
                loadExtractor(fullUrl, data, subtitleCallback, callback)
                return@forEach
            }

            // fallback: m3u8/mp4 ใน script
            Regex("""['"]?(https?://[^'">\s]*\.(?:m3u8|mp4)[^'">\s]*)['"]?""")
                .findAll(scriptText)
                .forEach { match ->
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name   = "$name (script)",
                            url    = match.groupValues[1]
                        ) { this.referer = data }
                    )
                }
        }

        return true
    }

    private fun decodeObfuscatedSrc(script: String): String? {
        val arrayMatch = Regex("""\[\s*(\d+(?:\s*,\s*\d+)+)\s*\]""")
            .findAll(script)
            .maxByOrNull { it.value.length } ?: return null

        val numbers = arrayMatch.groupValues[1]
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }

        if (numbers.size < 10) return null

        val keyFromScript = Regex("""(?:var|let|const)\s+\w*k\s*=\s*(\d+)\s*;""")
            .find(script)?.groupValues?.get(1)?.toIntOrNull()

        val key = keyFromScript ?: run {
            (1..30).firstOrNull { k ->
                val decoded = numbers.map { (it - k).toChar() }.joinToString("")
                decoded.contains("nanoplayer", ignoreCase = true) ||
                decoded.contains("player", ignoreCase = true)
            } ?: return null
        }

        val decoded = numbers.map { (it - key).toChar() }.joinToString("")
        return Regex("""\.src\s*=\s*["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
    }
}
