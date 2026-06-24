package com.DaddyLiveHD

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DaddyLiveHD : MainAPI() {
    override var mainUrl = "https://dlhd.pk"
    override var name = "DaddyLiveHD"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val CHANNEL_PAGE = "/24-7-channels.php"
    }

    // Headers ที่ dlhd.pk ต้องการ
    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl
    )

    // ── Category keywords ─────────────────────────────────────────────────
    private val categoryMap = mapOf(
        "Sports – UK/Europe"  to listOf("Sky Sport", "TNT Sport", "BT Sport", "Eurosport", "LaLiga", "DAZN", "Canal+", "Eleven Sport", "Arena", "Sport TV", "Polsat", "TVP", "Cosmote", "Nova Sport", "SSC", "beIN", "Ziggo", "Movistar", "Premier Sport"),
        "Sports – USA/Canada" to listOf("ESPN", "Fox Sport", "CBS Sport", "NBC Sport", "NFL", "NBA TV", "NHL", "MLB", "TSN", "Sportsnet", "Big Ten", "SEC Network", "ACC Network", "PAC-12", "FanDuel", "TUDN", "Tennis Channel", "Golf Channel"),
        "Sports – Other"      to listOf("SuperSport", "Astro Super", "SporTV", "Match Football", "Willow", "T Sports", "PTV Sport", "Star Sport"),
        "Entertainment – US"  to listOf("ABC USA", "CBS USA", "NBC USA", "FOX USA", "HBO", "Showtime", "Starz", "AMC USA", "TNT USA", "TBS USA", "USA Network", "Discovery", "History", "Lifetime", "Bravo", "MSNBC", "CNN", "FX USA"),
        "Entertainment – UK"  to listOf("BBC", "ITV", "Channel 4", "Channel 5", "Dave", "Film4"),
        "Entertainment – EU"  to listOf("TF1", "M6", "France TV", "RTL", "ZDF", "ARD", "Rai", "Mediaset", "La Sexta", "Telecinco", "TVE"),
        "Kids"                to listOf("Disney", "Nickelodeon", "Nick Jr", "Cartoon", "Boomerang", "Baby TV", "PBS Kid"),
        "News"                to listOf("News", "CNN", "BBC News", "Al Jazeera", "Sky News", "Euronews", "Bloomberg", "CNBC"),
        "Other Channels"      to emptyList()
    )

    override val mainPage = mainPageOf(
        CHANNEL_PAGE to "All Channels"
    )

    // ── Main Page ─────────────────────────────────────────────────────────
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc   = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
        // selector ใหม่: ดึง link ที่ชี้ไปยัง stream-*.php
        val links = doc.select("a[href*=/stream/stream-], a[href*=stream-][href*=.php]")

        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for (link in links) {
            val title = link.select(".channel-name, span, p").firstOrNull()?.text()?.trim()
                ?: link.text().trim()
            val href  = link.attr("abs:href").ifBlank { fixUrl(link.attr("href")) }
            if (title.isBlank() || href.isBlank()) continue

            // หา logo จาก <img> ใน link
            val logoUrl = link.selectFirst("img")?.let { img ->
                val src = img.attr("abs:src").ifBlank { img.attr("src") }
                when {
                    src.startsWith("http") -> src
                    src.startsWith("/")    -> "$mainUrl$src"
                    else                   -> null
                }
            }

            val searchItem = newLiveSearchResponse(
                name = title,
                url  = href,
                type = TvType.Live
            ) {
                posterUrl = logoUrl
            }

            var placed = false
            for ((cat, keywords) in categoryMap) {
                if (cat == "Other Channels") continue
                if (keywords.any { kw -> title.contains(kw, ignoreCase = true) }) {
                    grouped[cat]?.add(searchItem)
                    placed = true
                    break
                }
            }
            if (!placed) grouped["Other Channels"]?.add(searchItem)
        }

        val pages = grouped
            .filter { it.value.isNotEmpty() }
            .map { (cat, items) -> HomePageList(cat, items, isHorizontalImages = false) }

        return newHomePageResponse(pages, hasNext = false)
    }

    // ── Search ────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
        return doc.select("a[href*=/stream/stream-], a[href*=stream-][href*=.php]")
            .filter { link ->
                val title = link.text().trim()
                title.contains(query, ignoreCase = true)
            }
            .map { link ->
                val title  = link.text().trim()
                val href   = link.attr("abs:href").ifBlank { fixUrl(link.attr("href")) }
                val logoUrl = link.selectFirst("img")?.attr("abs:src")
                newLiveSearchResponse(name = title, url = href, type = TvType.Live) {
                    posterUrl = logoUrl
                }
            }
    }

    // ── Load ──────────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        // url รูปแบบ: https://dlhd.pk/stream/stream-51.php
        val id    = Regex("""stream-(\d+)\.php""").find(url)?.groupValues?.get(1) ?: ""
        val title = try {
            app.get(url, headers = siteHeaders).document
                .selectFirst("h1, h2, title")?.text()?.trim()
                ?: "Channel $id"
        } catch (e: Exception) {
            "Channel $id"
        }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url,
            type    = TvType.Live
        )
    }

    // ── Load Links ────────────────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // ดึง channel id จาก URL
        val id = Regex("""stream-(\d+)\.php""").find(data)?.groupValues?.get(1) ?: return false

        val folders = listOf("stream", "cast", "watch", "plus", "casting", "player")
        val m3u8Regex = Regex("""['"]?(https?://[^'">\s]+\.m3u8[^'">\s]*)['"]?""")

        for (folder in folders) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val html = app.get(
                    pageUrl,
                    headers = siteHeaders + mapOf("Referer" to "$mainUrl/")
                ).text

                // ── 1. หา m3u8 ใน script tags ─────────────────────────
                val m3u8Direct = m3u8Regex.find(html)?.groupValues?.get(1)
                if (!m3u8Direct.isNullOrBlank()) {
                    callback(ExtractorLink(
                        source  = name,
                        name    = "$name [$folder]",
                        url     = m3u8Direct,
                        referer = pageUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8  = true,
                        headers = mapOf(
                            "Referer" to pageUrl,
                            "Origin"  to mainUrl,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        )
                    ))
                    return true
                }

                // ── 2. หา iframe แล้วตาม ─────────────────────────────
                val iframeUrl = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""")
                    .find(html)?.groupValues?.get(1)
                    ?.let { if (it.startsWith("http")) it else "$mainUrl$it" }

                if (!iframeUrl.isNullOrBlank() && iframeUrl != pageUrl) {
                    val innerHtml = app.get(
                        iframeUrl,
                        headers = siteHeaders + mapOf("Referer" to pageUrl)
                    ).text

                    val m3u8Inner = m3u8Regex.find(innerHtml)?.groupValues?.get(1)
                    if (!m3u8Inner.isNullOrBlank()) {
                        callback(ExtractorLink(
                            source  = name,
                            name    = "$name [iframe-$folder]",
                            url     = m3u8Inner,
                            referer = iframeUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8  = true,
                            headers = mapOf(
                                "Referer" to iframeUrl,
                                "Origin"  to mainUrl,
                                "User-Agent" to "Mozilla/5.0"
                            )
                        ))
                        return true
                    }
                }

            } catch (_: Exception) {
                // ลอง folder ถัดไป
            }
        }
        return false
    }
}
