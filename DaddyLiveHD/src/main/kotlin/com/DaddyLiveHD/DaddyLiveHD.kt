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
    override val mainPage = mainPageOf("/24-7-channels.php" to "All")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get("${mainUrl}${request.data}").text
        val items = Regex("""ID:\s*(\d+)""").findAll(html).map {
            val id = it.groupValues[1]
            newLiveSearchResponse("Channel $id", "$mainUrl/stream/stream-$id.php", TvType.Live)
        }.toList()
        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = "Live",
                    list = items,
                    isHorizontalImages = false
                )
            )
        )
    }
    override suspend fun search(q: String): List<SearchResponse> = emptyList()
    override suspend fun load(url: String) = newLiveStreamLoadResponse(name = url, url = url, dataUrl = url)
    override suspend fun loadLinks(data: String, isCasting: Boolean, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit): Boolean {
        val id = Regex("""\d+""").find(data)?.value?: return false
        val m3u8 = app.get("$mainUrl/stream/stream-$id.php").text.let {
            Regex("""https?://[^\s'"<>]+\.m3u8[^\s'"<>]+""").find(it)?.value
        }?: return false
        cb.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8))
        return true
    }
}