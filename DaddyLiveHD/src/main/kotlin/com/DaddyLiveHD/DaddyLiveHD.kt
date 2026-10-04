package com.DaddyLiveHD

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DaddyLiveHD : MainAPI() {
    override var mainUrl = "https://dlive.sx"
    override var name = "DaddyLiveHD"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "/24-7-channels.php" to "All Channels"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://dlive.sx/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get("${mainUrl}${request.data}", headers = headers).text
        val regex = Regex("""([^<>\n]{3,}?)\s+ID:\s*(\d+)""")
        val items = mutableListOf<SearchResponse>()
        for (m in regex.findAll(html)) {
            val title = m.groupValues[1].trim()
            val id = m.groupValues[2]
            if (title.length < 3) continue
            items.add(
                newLiveSearchResponse(
                    name = title,
                    url = "$mainUrl/stream/stream-$id.php",
                    type = TvType.Live
                )
            )
        }
        // ใช้ named argument ทั้งหมดกันสลับตำแหน่ง
        val homeList = listOf(
            HomePageList(
                name = "Live 24/7",
                list = items,
                isHorizontalImages = false
            )
        )
        return newHomePageResponse(homeList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val all = getMainPage(1, MainPageRequest("All", "/24-7-channels.php", ""))
        return all.items.flatMap { it.list }.filter { it.name.contains(query, true) }
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(
            name = url.substringAfterLast("/").substringBefore(".php"),
            url = url,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("""\d+""").find(data)?.value?: return false
        for (folder in listOf("stream", "cast", "watch", "plus", "casting", "player")) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val html = app.get(pageUrl, headers = headers).text
                val m3u8 = Regex("""https?://[^\s'"<>]+\.m3u8[^\s'"<>]+""").find(html)?.value?: continue
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = pageUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                return true
            } catch (_: Exception) {}
        }
        return false
    }
}
