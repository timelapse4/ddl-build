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

    override val mainPage = mainPageOf(
        "$mainUrl/24-7-channels.php" to "All Channels"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val items = doc.select("a[href*=watch.php?id=]").mapNotNull { link ->
            val title = link.text().trim()
            val href = link.attr("href")
            if (title.isBlank() || href.isBlank()) return@mapNotNull null
            newLiveSearchResponse(name = title, url = fixUrl(href), type = TvType.Live)
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/24-7-channels.php").document
        return doc.select("a[href*=watch.php?id=]")
            .filter { it.text().contains(query, ignoreCase = true) }
            .mapNotNull { link ->
                val title = link.text().trim()
                val href = link.attr("href")
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                newLiveSearchResponse(name = title, url = fixUrl(href), type = TvType.Live)
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h2, h1")?.text()?.trim() ?: name
        return newLiveStreamLoadResponse(name = title, url = url, dataUrl = url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("""id=(\d+)""").find(data)?.groupValues?.get(1) ?: return false
        val folders = listOf("stream", "cast", "watch", "plus", "casting", "player")
        for (folder in folders) {
            val streamUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val doc = app.get(streamUrl, referer = mainUrl).document
                for (script in doc.select("script").map { it.data() }) {
                    val m3u8 = Regex("""https?://[^\s'"]+\.m3u8[^\s'"]*""").find(script)?.value
                    if (!m3u8.isNullOrBlank()) {
                        callback(
                            ExtractorLink(
                                source = name,
                                name = "$name [$folder]",
                                url = m3u8,
                                referer = streamUrl,
                                quality = Qualities.Unknown.value,
                                isM3u8 = true
                            )
                        )
                        return true
                    }
                }
            } catch (_: Exception) { }
        }
        return false
    }
}
