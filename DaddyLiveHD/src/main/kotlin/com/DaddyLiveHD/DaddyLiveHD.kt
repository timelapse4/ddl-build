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

    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl
    )

    private val categoryMap = mapOf(
        "Sports – UK/Europe"  to listOf("Sky Sport", "TNT Sport", "BT Sport", "Eurosport", "LaLiga", "DAZN", "Canal+", "Eleven Sport", "Arena", "Sport TV", "Polsat", "TVP", "Cosmote", "Nova Sport", "SSC", "beIN", "Ziggo", "Movistar", "Premier Sport"),
        "Sports – USA/Canada" to listOf("ESPN", "Fox Sport", "CBS Sport", "NBC Sport", "NFL", "NBA TV", "NHL", "MLB", "TSN", "Sportsnet", "Big Ten", "SEC Network", "ACC Network", "PAC-12", "FanDuel", "TUDN", "Tennis Channel", "Golf Channel"),
        "Sports – Other"      to listOf("SuperSport", "Astro Super", "SporTV", "Match Football", "Willow", "T Sports", "PTV Sport", "Star Sport"),
        "Entertainment – US"  to listOf("ABC USA", "CBS USA", "NBC USA", "FOX USA", "HBO", "Showtime", "Starz", "AMC USA", "TNT USA", "TBS USA", "USA Network", "Discovery", "History", "Lifetime", "Bravo", "MSNBC", "CNN", "FX USA"),
        "Entertainment – UK"  to listOf("BBC", "ITV", "Channel 4", "Channel 5", "Dave", "Film4"),
        "Entertainment – EU"  to listOf("TF1", "M6", "France TV", "RTL", "ZDF", "ARD", "Rai", "Mediaset", "La Sexta", "Telecinco", "TVE"),
        "Kids"                to listOf("Disney", "Nickelodeon", "Nick Jr", "Cartoon", "Boomerang", "Baby TV", "PBS Kid"),
        "News"                to listOf("News", "Al Jazeera", "Sky News", "Euronews", "Bloomberg", "CNBC"),
        "Other Channels"      to emptyList()
    )

    override val mainPage = mainPageOf(
        CHANNEL_PAGE to "All Channels"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // หน้านี้ใช้ link รูปแบบ /watch.php?id=XX
        val doc   = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
        val links = doc.select("a[href*=watch.php?id=]")

        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for (link in links) {
            val title = link.text().trim()
            val href  = link.attr("href")
            if (title.isBlank() || href.isBlank()) continue

            // สร้าง stream URL จาก id
            val id = Regex("""id=(\d+)""").find(href)?.groupValues?.get(1) ?: continue
            val streamUrl = "$mainUrl/stream/stream-$id.php"

            val searchItem = newLiveSearchResponse(
                name = title,
                url  = streamUrl,
                type = TvType.Live
            )

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

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
        return doc.select("a[href*=watch.php?id=]")
            .filter { it.text().contains(query, ignoreCase = true) }
            .mapNotNull { link ->
                val title = link.text().trim()
                val id    = Regex("""id=(\d+)""").find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
                newLiveSearchResponse(
                    name = title,
                    url  = "$mainUrl/stream/stream-$id.php",
                    type = TvType.Live
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val id    = Regex("""stream-(\d+)\.php""").find(url)?.groupValues?.get(1) ?: ""
        val title = try {
            app.get(url, headers = siteHeaders).document
                .selectFirst("h1, h2, title")?.text()?.trim() ?: "Channel $id"
        } catch (e: Exception) {
            "Channel $id"
        }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("""stream-(\d+)\.php""").find(data)?.groupValues?.get(1) ?: return false

        val folders   = listOf("stream", "cast", "watch", "plus", "casting", "player")
        val m3u8Regex = Regex("""['"]?(https?://[^'">\s]+\.m3u8[^'">\s]*)['"]?""")

        for (folder in folders) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val html = app.get(
                    pageUrl,
                    headers = siteHeaders + mapOf("Referer" to "$mainUrl/")
                ).text

                // 1. หา m3u8 ตรงใน HTML/script
                val m3u8Direct = m3u8Regex.find(html)?.groupValues?.get(1)
                if (!m3u8Direct.isNullOrBlank()) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name   = "$name [$folder]",
                            url    = m3u8Direct,
                            type   = ExtractorLinkType.M3U8
                        ) {
                            this.referer = pageUrl
                            this.quality = Qualities.Unknown.value
                            this.headers = mapOf(
                                "Referer"    to pageUrl,
                                "Origin"     to mainUrl,
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                            )
                        }
                    )
                    return true
                }

                // 2. ตาม iframe
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
                        callback(
                            newExtractorLink(
                                source = name,
                                name   = "$name [iframe-$folder]",
                                url    = m3u8Inner,
                                type   = ExtractorLinkType.M3U8
                            ) {
                                this.referer = iframeUrl
                                this.quality = Qualities.Unknown.value
                                this.headers = mapOf(
                                    "Referer"    to iframeUrl,
                                    "Origin"     to mainUrl,
                                    "User-Agent" to "Mozilla/5.0"
                                )
                            }
                        )
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
