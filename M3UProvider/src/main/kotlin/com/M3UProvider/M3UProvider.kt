package com.M3UProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class M3UProvider : MainAPI() {
    override var mainUrl              = "https://iptv.852851.xyz"
    override var name                 = "M3U IPTV"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Live)

    private val m3uUrl = "https://iptv.852851.xyz/sub/o7zZG7OQIZTN/playlist.m3u"

    private val fetchHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept"          to "*/*",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    private var cachedChannels: List<M3UChannel>? = null

    data class M3UChannel(
        val name  : String,
        val url   : String,
        val logo  : String?,
        val group : String,
        val tvgId : String?
    )

    private suspend fun fetchAndParse(): List<M3UChannel> {
        cachedChannels?.let { return it }
        val raw      = app.get(m3uUrl, headers = fetchHeaders, timeout = 30).text
        val channels = mutableListOf<M3UChannel>()
        val lines    = raw.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val name  = parseAttr(line, "tvg-name") ?: line.substringAfterLast(",").trim()
                val logo  = parseAttr(line, "tvg-logo")
                val group = parseAttr(line, "group-title") ?: "Other"
                val tvgId = parseAttr(line, "tvg-id")
                val url   = lines.getOrNull(i + 1)?.trim() ?: ""
                if (url.isNotBlank() && !url.startsWith("#")) {
                    channels.add(M3UChannel(name, url, logo, group, tvgId))
                    i += 2; continue
                }
            }
            i++
        }
        cachedChannels = channels
        return channels
    }

    private fun parseAttr(line: String, attr: String): String? {
        val m = Regex("""$attr="([^"]*)"""").find(line) ?: return null
        return m.groupValues[1].ifBlank { null }
    }

    override val mainPage = mainPageOf(m3uUrl to "All Channels")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val grouped = linkedMapOf<String, MutableList<SearchResponse>>()
        for (ch in fetchAndParse()) {
            grouped.getOrPut(ch.group) { mutableListOf() }.add(toSearch(ch))
        }
        return newHomePageResponse(
            grouped.map { (g, items) -> HomePageList(g, items, isHorizontalImages = true) },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> =
        fetchAndParse().filter { it.name.contains(query, ignoreCase = true) }.map { toSearch(it) }

    override suspend fun load(url: String): LoadResponse {
        val (title, logo) = decodeMeta(url)
        return newLiveStreamLoadResponse(name = title, url = url, dataUrl = url) {
            this.posterUrl = logo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val (title, _) = decodeMeta(data)
        val streamUrl  = extractStreamUrl(data)
        val type = if (streamUrl.contains(".mpd")) ExtractorLinkType.DASH else ExtractorLinkType.M3U8
        callback(newExtractorLink(source = name, name = title, url = streamUrl, type = type) {
            this.referer = mainUrl
            this.quality = Qualities.Unknown.value
            this.headers = fetchHeaders
        })
        return true
    }

    private fun toSearch(ch: M3UChannel) = newLiveSearchResponse(
        name = ch.name,
        url  = encodeMeta(ch.url, ch.name, ch.logo),
        type = TvType.Live
    ) { this.posterUrl = ch.logo }

    private fun encodeMeta(u: String, n: String, l: String?) =
        "${u.replace("|","%7C")}|${n.replace("|","%7C")}|${(l?:"").replace("|","%7C")}"

    private fun decodeMeta(s: String): Pair<String, String?> {
        val p = s.split("|")
        return (p.getOrNull(1)?.replace("%7C","|") ?: "Channel") to
               p.getOrNull(2)?.replace("%7C","|")?.ifBlank { null }
    }

    private fun extractStreamUrl(s: String) = s.split("|").first().replace("%7C","|")
}
