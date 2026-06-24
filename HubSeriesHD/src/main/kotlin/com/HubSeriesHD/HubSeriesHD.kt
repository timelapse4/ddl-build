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

    // ─────────────────────────────────────────────
    // Main Page
    // ─────────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data
                  else "${request.data.trimEnd('/')}page/$page/"
        val document = app.get(url).document
        val items = document.select("article, div.item, div.TPost, div.MovieItem")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    // ─────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────
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

    // ─────────────────────────────────────────────
    // Load Series / Movie Page
    // ─────────────────────────────────────────────
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
            val epUrl   = ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epNum   = ep.selectFirst("span.num-epi, .Num, .numerando")
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
    // Load Links  (รองรับ obfuscated charCode array)
    // ─────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1) iframe ที่มี src ตรงๆ (non-obfuscated)
        document.select("iframe[src], iframe[data-src]")
            .map { it.attr("data-src").ifEmpty { it.attr("src") } }
            .filter { it.isNotBlank() && (it.startsWith("http") || it.startsWith("//")) }
            .forEach { rawSrc ->
                val iframeUrl = if (rawSrc.startsWith("//")) "https:$rawSrc" else rawSrc
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }

        // 2) <video><source src="...">
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

        // 3) Obfuscated script — charCode array + key offset
        //    pattern: var _xxxk = 7;  →  array[i] - key → char
        document.select("script:not([src])").forEach { scriptEl ->
            val scriptText = scriptEl.data()

            // ต้องมี String.fromCharCode หรือ .src = เพื่อกรองสคริปต์ที่ไม่เกี่ยว
            if (!scriptText.contains(".src") ||
                (!scriptText.contains("fromCharCode") && !scriptText.contains("charCodeAt"))
            ) return@forEach

            val iframeSrc = decodeObfuscatedSrc(scriptText)
            if (iframeSrc != null) {
                val fullUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                loadExtractor(fullUrl, data, subtitleCallback, callback)
                return@forEach   // เจอแล้ว ไม่ต้อง parse script นี้ต่อ
            }

            // fallback: หา m3u8 / mp4 ตรงๆ ใน script (กรณีไม่ได้ obfuscate)
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

    // ─────────────────────────────────────────────
    // Decode obfuscated charCode array
    // pattern ที่ site ใช้:
    //   var _05aak = 7;
    //   var _05aa  = [116,111,112,...]   ← ยาวที่สุด
    //   document.getElementById('refresh').src =
    //       _05aa.map(c => String.fromCharCode(c - _05aak)).join('')
    // ─────────────────────────────────────────────
    private fun decodeObfuscatedSrc(script: String): String? {

        // --- หา array ตัวเลขที่ยาวที่สุด (= payload) ---
        val arrayMatch = Regex("""\[\s*(\d+(?:\s*,\s*\d+)+)\s*\]""")
            .findAll(script)
            .maxByOrNull { it.value.length }
            ?: return null

        val numbers = arrayMatch.groupValues[1]
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }

        if (numbers.size < 10) return null   // array เล็กเกินไป ไม่ใช่ payload

        // --- หา key: ตัวแปรที่ชื่อลงท้าย "k" และ assign ค่าตัวเลขเดียว ---
        val keyFromScript = Regex("""(?:var|let|const)\s+\w*k\s*=\s*(\d+)\s*;""")
            .find(script)
            ?.groupValues?.get(1)?.toIntOrNull()

        // ถ้าหา key จาก regex ไม่เจอ → brute-force key 1..30
        val key = keyFromScript ?: run {
            (1..30).firstOrNull { k ->
                val decoded = numbers.map { (it - k).toChar() }.joinToString("")
                decoded.contains("nanoplayer", ignoreCase = true) ||
                decoded.contains("player", ignoreCase = true)
            } ?: return null
        }

        // --- Decode ---
        val decoded = numbers.map { (it - key).toChar() }.joinToString("")

        // --- ดึง src URL จาก decoded string ---
        // .src = "//nanoplayer.zip/..."  หรือ  .src="https://..."
        return Regex("""\.src\s*=\s*["']([^"']+)["']""")
            .find(decoded)
            ?.groupValues?.get(1)
    }
}
