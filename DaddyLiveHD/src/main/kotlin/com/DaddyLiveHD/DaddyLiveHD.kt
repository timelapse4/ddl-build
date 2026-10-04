package com.DaddyLiveHD

import android.util.Base64
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
        val list = mutableListOf<SearchResponse>()

        for (m in regex.findAll(html)) {
            val title = m.groupValues[1].trim().replace(Regex("<.*?>"), "").trim()
            val id = m.groupValues[2]
            if (title.length < 2) continue
            list.add(
                newLiveSearchResponse(
                    name = title,
                    url = "$mainUrl/stream/stream-$id.php",
                    type = TvType.Live
                )
            )
        }

        // แบ่งเป็นหมวดง่ายๆ กันว่าง
        val home = if (list.isNotEmpty()) {
            listOf(HomePageList(name = "Live 24/7", list = list, isHorizontalImages = false))
        } else {
            emptyList()
        }
        return newHomePageResponse(home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getMainPage(1, MainPageRequest("All", "/24-7-channels.php", "")).items
           .flatMap { it.list }
           .filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse {
        // ใช้ named arguments แก้ error Boolean
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
        val id = Regex("""(\d+)""").find(data)?.groupValues?.get(1)?: return false
        val folders = listOf("stream", "cast", "watch", "plus", "casting", "player")

        for (folder in folders) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val html = app.get(pageUrl, headers = headers).text
                if (html.length < 1000) continue

                val m3u8 = Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]+)""").find(html)?.groupValues?.get(1)
                   ?: Regex("""atob\(['"]([^'"]+)['"]\)""").findAll(html).mapNotNull {
                        try {
                            val d = String(Base64.decode(it.groupValues[1], Base64.DEFAULT))
                            Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]+)""").find(d)?.groupValues?.get(1)
                        } catch (_: Exception) { null }
                    }.firstOrNull()
                   ?: continue

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name [$folder]",
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
