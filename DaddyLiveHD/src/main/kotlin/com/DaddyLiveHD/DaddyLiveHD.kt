package com.DaddyLiveHD

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class DaddyLiveHD : MainAPI() {
    override var mainUrl = "https://dlhd.pk"
    override var name = "DaddyLiveHD"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val CHANNEL_PAGE = "/24-7-channels.php"
        private const val WATCH_URL = "/watch.php?id="
        private const val STREAM_URL = "/stream/stream-"
    }

    // ── Category grouping ──────────────────────────────────────────────────
    private val categoryMap = mapOf(
        "Sports – UK/Europe" to listOf("Sky", "TNT Sports", "BT Sport", "Eurosport", "LaLiga", "DAZN", "Canal+", "Eleven", "Arena", "Sport TV", "Eleven", "Polsat", "TVP", "Cosmote", "Nova Sports", "SSC", "beIN", "Ziggo", "Movistar"),
        "Sports – USA/Canada" to listOf("ESPN", "Fox Sports", "CBS Sports", "NBC Sports", "NFL", "NBA", "NHL", "MLB", "TSN", "Sportsnet", "Big Ten", "SEC", "ACC", "PAC-12", "FanDuel", "TUDN", "Tennis Channel", "Golf Channel"),
        "Sports – Other" to listOf("SuperSport", "Astro", "SporTV", "Premier Brasil", "Match Football", "PDC", "Willow", "T Sports", "PTV Sports"),
        "Entertainment – US" to listOf("ABC", "CBS", "NBC", "FOX", "CW", "HBO", "Showtime", "Starz", "AMC", "TNT", "TBS", "USA Network", "Discovery", "History", "Lifetime", "Bravo", "MSNBC", "CNN", "FX"),
        "Entertainment – UK" to listOf("BBC", "ITV", "Channel 4", "Channel 5", "Sky", "Dave", "Film4"),
        "Entertainment – EU" to listOf("TF1", "M6", "France", "RTL", "ZDF", "ARD", "Rai", "Mediaset", "La Sexta", "Telecinco", "TVE"),
        "Other Channels" to emptyList()
    )

    // ── Main page: one HomePageList per category ───────────────────────────
    override val mainPage = mainPageOf(
        CHANNEL_PAGE to "All Channels"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl${CHANNEL_PAGE}").document
        val links = doc.select("a[href*=watch.php?id=]")

        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for (link in links) {
            val title = link.text().trim()
            val href  = link.attr("href")
            if (title.isBlank() || href.isBlank()) continue

            val searchItem = newLiveSearchResponse(
                name = title,
                url  = fixUrl(href),
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

    // ── Search: filter channels by title ──────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl${CHANNEL_PAGE}").document
        return doc.select("a[href*=watch.php?id=]")
            .filter { it.text().contains(query, ignoreCase = true) }
            .map { link ->
                newLiveSearchResponse(
                    name = link.text().trim(),
                    url  = fixUrl(link.attr("href")),
                    type = TvType.Live
                )
            }
    }

    // ── Load detail: parse watch page for title & stream id ───────────────
    override suspend fun load(url: String): LoadResponse {
        val doc   = app.get(url).document
        val title = doc.selectFirst("h2, h1, title")?.text()?.trim()
            ?: "DaddyLiveHD Channel"
        val id    = Regex("""id=(\d+)""").find(url)?.groupValues?.get(1) ?: ""

        return newLiveStreamLoadResponse(
            name      = title,
            url       = url,
            dataUrl   = "$mainUrl${WATCH_URL}${id}",
            type      = TvType.Live
        )
    }

    // ── Load links: extract stream iframe src ─────────────────────────────
    override suspend fun loadLinks(
        data       : String,
        isCasting  : Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback   : (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("""id=(\d+)""").find(data)?.groupValues?.get(1) ?: return false

        // Try each stream folder in order
        val folders = listOf("stream", "cast", "watch", "plus", "casting", "player")

        for (folder in folders) {
            val streamUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val streamDoc = app.get(
                    streamUrl,
                    referer   = "$mainUrl/watch.php?id=$id",
                    headers   = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Accept"     to "*/*",
                        "Origin"     to mainUrl
                    )
                ).document

                // Look for HLS m3u8 link inside script tags
                val scripts = streamDoc.select("script").map { it.data() }
                for (script in scripts) {
                    val m3u8 = Regex("""['"]?(https?://[^'">\s]+\.m3u8[^'">\s]*)['"]?""")
                        .find(script)?.groupValues?.get(1)
                    if (!m3u8.isNullOrBlank()) {
                        callback(
                            ExtractorLink(
                                source  = name,
                                name    = "$name [$folder]",
                                url     = m3u8,
                                referer = streamUrl,
                                quality = Qualities.Unknown.value,
                                isM3u8  = true,
                                headers = mapOf(
                                    "Referer"    to streamUrl,
                                    "Origin"     to mainUrl,
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                                )
                            )
                        )
                        return true
                    }
                }

                // Also look for source src=
                val srcM3u8 = streamDoc
                    .select("source[src*=.m3u8], video[src*=.m3u8]")
                    .firstOrNull()?.attr("src")
                if (!srcM3u8.isNullOrBlank()) {
                    callback(
                        ExtractorLink(
                            source  = name,
                            name    = "$name [$folder]",
                            url     = srcM3u8,
                            referer = streamUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8  = true
                        )
                    )
                    return true
                }

                // Check for nested iframes pointing to another domain
                val iframeSrc = streamDoc.selectFirst("iframe[src]")?.attr("src")
                if (!iframeSrc.isNullOrBlank() && iframeSrc != streamUrl) {
                    val innerDoc = app.get(
                        iframeSrc,
                        referer = streamUrl,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0",
                            "Origin"     to mainUrl
                        )
                    ).document
                    for (script in innerDoc.select("script").map { it.data() }) {
                        val m3u8 = Regex("""['"]?(https?://[^'">\s]+\.m3u8[^'">\s]*)['"]?""")
                            .find(script)?.groupValues?.get(1)
                        if (!m3u8.isNullOrBlank()) {
                            callback(
                                ExtractorLink(
                                    source  = name,
                                    name    = "$name [iframe-$folder]",
                                    url     = m3u8,
                                    referer = iframeSrc,
                                    quality = Qualities.Unknown.value,
                                    isM3u8  = true
                                )
                            )
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next folder
            }
        }
        return false
    }
}
