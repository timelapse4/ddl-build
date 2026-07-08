package com.DaddyLiveHD

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DaddyLiveHD : MainAPI() {
    override var mainUrl = "https://dlhd.st"
    override var name = "DaddyLiveHD"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    companion object {
        private const val TAG = "DaddyLiveHD"
        private const val CHANNEL_PAGE = "/24-7-channels.php"

        // folder ที่ stream page อาจอยู่
        private val STREAM_FOLDERS = listOf("stream", "cast", "watch", "plus", "casting", "player", "live")

        private val jsonMapper = jacksonObjectMapper().registerKotlinModule()

        // Cache for the iptv-org channel logo index so we only fetch/parse it once
        // per app session instead of on every single channel lookup.
        @Volatile
        private var iptvOrgLogoIndex: Map<String, String>? = null
    }

    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl,
        "Accept"     to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Cache-Control"   to "no-cache"
    )

    // categoryMap ตรงกับ group-title ใน playlist_gen.php ทุก group
    // key = ชื่อที่แสดงใน Cloudstream, value = คำค้นในชื่อช่อง
    private val categoryMap = linkedMapOf(
        // ── Sports ──────────────────────────────────────────────────────────
        "Sports UK"           to listOf(
            "TNT Sports", "Sky Sports", "BT Sport", "LaLigaTV UK", "DAZN 1 UK",
            "Viaplay Sports", "MUTV", "Liverpool TV", "Premier Sports Ireland"
        ),
        "Sports USA"          to listOf(
            "ESPN", "Fox Sports", "FOX Soccer", "CBS Sports", "NBC Sports",
            "NFL", "NBA TV", "MLB Network", "NHL Network", "GOLF Channel",
            "Tennis Channel", "BeIN SPORTS USA", "SEC Network", "ACC Network",
            "BIG TEN", "TUDN", "WWE Network", "Fight Network", "PDC TV",
            "MSG USA", "SportsNet New York", "SNY", "NESN USA", "YES Network",
            "Marquee Sports", "Chicago Sports", "NBC Sports Philadelphia",
            "Space City", "Root Sports", "Spectrum Sports", "FanDuel Sports"
        ),
        "Sports beIN"         to listOf(
            "beIN Sports MENA", "beIN Sports 1 Arabic", "beIN Sports 2 Arabic",
            "beIN Sports 3 Arabic", "beIN Sports 4 Arabic", "beIN Sports 5 Arabic",
            "beIN Sports 6 Arabic", "beIN Sports 7 Arabic", "beIN Sports 8 Arabic",
            "beIN Sports 9 Arabic", "beIN SPORTS XTRA", "beIN Sports MAX",
            "beIN SPORTS 1 France", "beIN SPORTS 2 France", "beIN SPORTS 3 France",
            "beIN SPORTS 1 Turkey", "beIN SPORTS 2 Turkey", "beIN SPORTS 3 Turkey",
            "beIN SPORTS 4 Turkey", "bein Sports 5 Turkey", "BeIN Sports HD Qatar",
            "beIN SPORTS en Espanol", "beIN SPORTS Australia", "beIN Sports Malaysia"
        ),
        "Sports France"       to listOf(
            "Canal+ MotoGP", "Canal+ Formula 1", "Canal+ France", "Canal+ Sport France",
            "Canal+ Foot", "Canal+ Sport360", "RMC Sport", "DAZN Ligue 1",
            "L'Equipe France", "Eurosport 1 France", "Eurosport 2 France"
        ),
        "Sports Spain"        to listOf(
            "Movistar Laliga", "Movistar Liga de Campeones", "Movistar Deportes",
            "DAZN 1 Spain", "DAZN 2 Spain", "DAZN 3 Spain", "DAZN 4 Spain",
            "DAZN F1 ES", "DAZN LaLiga", "EuroSport 1 Spain", "EuroSport 2 Spain",
            "GOL PLAY", "Teledeporte", "Real Madrid TV", "Barca TV"
        ),
        "Sports Germany"      to listOf(
            "Sky Sports 1 DE", "Sky Sports 2 DE", "Sky Sport Bundesliga",
            "Sky Sports F1 DE", "DAZN 1 Bar DE", "DAZN 2 Bar DE",
            "Sport 1 Germany", "SportDigital Fussball"
        ),
        "Sports Italy"        to listOf(
            "Sky Sport MAX Italy", "Sky Sport UNO Italy", "Sky Sport Arena Italy",
            "Sky Sport 24 Italy", "Sky Sport Calcio", "Sky Calcio",
            "Sky Sport Basket", "Sky Sport F1 Italy", "Sky Sport MotoGP Italy",
            "EuroSport 1 Italy", "EuroSport 2 Italy", "DAZN ZONA", "Rai Sport"
        ),
        "Sports Poland"       to listOf(
            "Canal+ Sport Poland", "Canal+ Extra", "Polsat Sport",
            "Eleven Sports 1 Poland", "Eleven Sports 2 Poland",
            "Eleven Sports 3 Poland", "Eleven Sports 4 Poland",
            "EuroSport 1 Poland", "EuroSport 2 Poland", "TVP Sport"
        ),
        "Sports Portugal"     to listOf(
            "Sport TV1", "Sport TV2", "Sport TV3", "Sport TV4", "Sport TV5", "Sport TV6",
            "Eleven Sports 1 Portugal", "Eleven Sports 2 Portugal",
            "Eleven Sports 3 Portugal", "Eleven Sports 4 Portugal",
            "Eleven Sports 5 Portugal", "Benfica TV"
        ),
        "Sports Greece"       to listOf(
            "EuroSport 1 Greece", "EuroSport 2 Greece",
            "Nova Sports 1 Greece", "Nova Sports 2 Greece", "Nova Sports 3 Greece",
            "Nova Sports 4 Greece", "Nova Sports 5 Greece", "Nova Sports 6 Greece",
            "Nova Sports Premier League", "Cosmote Sport"
        ),
        "Sports Romania"      to listOf(
            "Digi Sport", "Orange Sport", "Prima Sport"
        ),
        "Sports Balkans"      to listOf(
            "Arena Sport 1 Premium", "Arena Sport 2 Premium",
            "Arena Sport 1 Serbia", "Arena Sport 2 Serbia", "Arena Sport 3 Serbia",
            "Arena Sport 4 Serbia", "Arena Sport 5", "Arena Sport 1 Croatia",
            "Arena Sport 2 Croatia", "Arena Sport 3 Croatia", "Arena Sport 4 Croatia",
            "Arena Sport 1 BiH", "Sport Klub", "Nova Sport Serbia",
            "Max Sport 1 Croatia", "Max Sport 2 Croatia"
        ),
        "Sports Bulgaria"     to listOf(
            "Diema Sport", "Nova Sport Bulgaria",
            "Max Sport 1 Bulgaria", "Max Sport 2 Bulgaria",
            "Max Sport 3 Bulgaria", "Max Sport 4 Bulgaria"
        ),
        "Sports Middle East"  to listOf(
            "Abu Dhabi Sports", "Dubai Sports", "Alkass",
            "OnTime Sports", "SSC Sport"
        ),
        "Sports Africa"       to listOf(
            "SuperSport Grandstand", "SuperSport PSL", "SuperSport Premier League",
            "SuperSport LaLiga", "SuperSport Variety", "SuperSport Action",
            "SuperSport Rugby", "SuperSport Golf", "SuperSport Tennis",
            "SuperSport Motorsport", "Supersport Football", "SuperSport Cricket"
        ),
        "Sports Asia"         to listOf(
            "Astro SuperSport", "Star Sports", "SONY TEN",
            "PTV Sports", "A Sport PK", "Ten Sports PK", "T Sports BD",
            "Willow Cricket", "Willow 2 Cricket", "Fox Cricket"
        ),
        "Sports Canada"       to listOf(
            "TSN", "Sportsnet Ontario", "Sportsnet West", "Sportsnet East",
            "Sportsnet 360", "Sportsnet World", "Sportsnet One",
            "RDS CA", "RDS 2 CA", "TVA Sports"
        ),
        "Sports Brazil"       to listOf(
            "SporTV Brasil", "SporTV2 Brasil", "SporTV3 Brasil",
            "ESPN Brasil", "ESPN2 Brasil", "ESPN3 Brasil"
        ),
        "Sports Latin"        to listOf(
            "ESPN Argentina", "ESPN2 Argentina", "Fox Sports Argentina",
            "TNT Sports Argentina", "TyC Sports"
        ),
        "Sports Mexico"       to listOf(
            "ESPN 1 MX", "ESPN 2 MX", "Fox Sports 1 MX", "Fox Sports 2 MX",
            "Claro Sports MX", "TUDN MX"
        ),
        "Sports New Zealand"  to listOf(
            "Sky Sport 1 NZ", "Sky Sport 2 NZ", "Sky Sport 3 NZ",
            "Sky Sport 4 NZ", "Sky Sport 5 NZ"
        ),
        "Sports Australia"    to listOf(
            "FOX Sports 502 AU", "FOX Sports 503 AU",
            "FOX Sports 504 AU", "FOX Sports 505 AU"
        ),
        "Sports Sweden"       to listOf(
            "Eurosport 1 SW", "Eurosport 2 SW", "TV4 Sport"
        ),
        "Sports Denmark"      to listOf(
            "TV2 Sport X Denmark", "TV3 Sport Denmark", "TV2 Sport Denmark"
        ),
        "Sports Netherlands"  to listOf(
            "ESPN 1 NL", "ESPN 2 NL", "ESPN 3 NL",
            "Ziggo Sport"
        ),
        "Sports Israel"       to listOf(
            "Sport 1 Israel", "Sport 2 Israel", "Sport 3 Israel",
            "Sport 4 Israel", "Sport 5 Israel"
        ),
        "Sports Russia"       to listOf(
            "Match Football", "Match TV Russia"
        ),
        "Sports Turkey"       to listOf(
            "A Spor Turkey", "TRT Spor"
        ),
        "Sports Cyprus"       to listOf(
            "Cytavision Sports"
        ),
        "Sports Czech"        to listOf(
            "Nova Sport 1 CZ", "Nova Sport 2 CZ", "Nova Sport 3 CZ", "Nova Sport 4 CZ",
            "CT Sport CZ", "Canal+ Sport CZ", "Premier Sport 1 CZ", "Premier Sport 2 CZ"
        ),
        "Sports Slovak"       to listOf(
            "JOJ Spor SK", "Canal+ Sport SK"
        ),
        // ── TV ──────────────────────────────────────────────────────────────
        "TV UK"               to listOf(
            "BBC One", "BBC Two", "BBC Three", "BBC Four", "BBC News",
            "ITV 1 UK", "ITV 2 UK", "ITV 3 UK", "ITV 4 UK",
            "Channel 4 UK", "Channel 5 UK", "Sky Witness", "Sky Atlantic",
            "E4 Channel", "Dave", "Gold UK", "Film4 UK", "Sky Showcase",
            "Sky Arts", "Sky Comedy", "Sky Crime", "Sky History", "Sky MAX UK",
            "RTE 1", "RTE 2"
        ),
        "TV USA"              to listOf(
            "ABC USA", "CBS USA", "NBC USA", "FOX USA", "CNN USA", "MSNBC",
            "Fox News", "CNBC USA", "TBS USA", "TNT USA", "AMC USA",
            "A&E USA", "FX USA", "Bravo USA", "E! Entertainment", "Lifetime Network",
            "Hallmark Channel", "HGTV", "The Food Network", "TLC",
            "Discovery Channel", "Animal Planet", "History USA",
            "National Geographic", "Science Channel", "SYFY USA",
            "Cartoon Network", "Disney Channel", "NICK", "Comedy Central",
            "MTV USA", "VH1 USA"
        ),
        "Movies USA"          to listOf(
            "HBO USA", "HBO2 USA", "Cinemax USA", "Showtime USA",
            "Starz", "Paramount Network"
        ),
        "Movies UK"           to listOf(
            "Sky Cinema Premiere", "Sky Cinema Select", "Sky Cinema Hits",
            "Sky Cinema Action UK", "Sky Cinema Comedy UK", "Sky Cinema Drama UK"
        ),
        "Movies Italy"        to listOf(
            "Sky Cinema Uno Italy", "Sky Cinema Action Italy",
            "Sky Cinema Comedy Italy", "Sky Cinema Romance Italy"
        ),
        "TV France"           to listOf(
            "TF1 France", "M6 France", "France 2", "France 3", "France 4",
            "France 5", "C8 France", "BFM TV", "Arte France"
        ),
        "TV Italy"            to listOf(
            "Rai 1 Italy", "Rai 2 Italy", "Rai 3 Italy",
            "Italia 1 Italy", "La7 Italy", "Sky UNO Italy"
        ),
        "TV Spain"            to listOf(
            "TVE La 1", "Telecinco Spain", "Antena 3 Spain"
        ),
        "TV Germany"          to listOf(
            "ZDF DE", "RTL DE", "SAT.1 DE", "ProSieben DE"
        ),
        "TV Portugal"         to listOf(
            "RTP 1 Portugal", "RTP 2 Portugal", "SIC Portugal", "TVI Portugal"
        ),
        "TV Bulgaria"         to listOf(
            "bTV Bulgaria", "Nova TV Bulgaria", "BNT 1 Bulgaria"
        ),
        "TV Canada"           to listOf(
            "CTV Canada", "CBC CA", "Global CA"
        ),
        "TV Turkey"           to listOf(
            "ATV Turkey", "Kanal D Turkey", "Show TV Turkey"
        ),
        "TV Denmark"          to listOf(
            "DR1 Denmark", "DR2 Denmark", "TV2 Denmark"
        ),
        "TV Netherlands"      to listOf(
            "RTL7 Netherland", "Veronica NL"
        ),
        "TV Israel"           to listOf(
            "Channel 9 Israel", "Channel 12 Israel"
        ),
        "TV Czech"            to listOf(
            "Nova HD CZ", "CT1 HD CZ"
        ),
        "TV Slovak"           to listOf(
            "JOJ SK"
        ),
        "TV Brazil"           to listOf(
            "Globo SP", "Globo RIO"
        ),
        "TV Mexico"           to listOf(
            "Azteca Uno MX", "Las Estrellas"
        ),
        "TV Romania"          to listOf(
            "Prima TV RO"
        ),
        // fallback
        "Other Channels"      to emptyList()
    )

    override val mainPage = mainPageOf(
        CHANNEL_PAGE to "All Channels"
    )

    // ============================================================
    //  หน้าหลัก: ดึงรายการช่องจาก 24-7-channels.php
    // ============================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document

        // รองรับทั้ง href รูปแบบเก่า (watch.php?id=) และรูปแบบใหม่ที่อาจเปลี่ยน
        val links = doc.select("a[href*='id='], a[href*='stream-'], a[href*='/watch/'], a[href*='/live/']")

        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for (link in links) {
            val title = link.text().trim()
            if (title.isBlank()) continue

            val href = link.attr("href").trim()
            if (href.isBlank()) continue

            // ดึง ID จาก href ไม่ว่าจะรูปแบบใดก็ตาม
            val id = extractIdFromHref(href) ?: continue
            val absoluteHref = fixUrl(href)
            val logoUrl = getLogoUrlFast(id)

            val searchItem = newLiveSearchResponse(
                name = title,
                url  = buildInternalUrl(id, absoluteHref, title),
                type = TvType.Live
            ) {
                this.posterUrl = logoUrl
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
            .map { (cat, items) -> HomePageList(cat, items, isHorizontalImages = true) }

        return newHomePageResponse(pages, hasNext = false)
    }

    // ============================================================
    //  ค้นหา
    // ============================================================
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
        return doc.select("a[href*='id='], a[href*='stream-'], a[href*='/watch/'], a[href*='/live/']")
            .filter { it.text().contains(query, ignoreCase = true) }
            .mapNotNull { link ->
                val title = link.text().trim()
                val href = link.attr("href")
                val id    = extractIdFromHref(href) ?: return@mapNotNull null
                val logoUrl = getLogoUrlFast(id)
                newLiveSearchResponse(
                    name = title,
                    url  = buildInternalUrl(id, fixUrl(href), title),
                    type = TvType.Live
                ) {
                    this.posterUrl = logoUrl
                }
            }
    }

    // ============================================================
    //  โหลดหน้าช่อง
    // ============================================================
    override suspend fun load(url: String): LoadResponse {
        val (id, _, decodedTitle) = decodeData(url)
        val title = decodedTitle ?: "Channel $id"
        val logoUrl = getLogoUrl(id, title)
        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = logoUrl
        }
    }

    // ============================================================
    //  โหลด Links — จุดสำคัญ แก้ปัญหา "no links"
    // ============================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = coroutineScope {
        val (id, originalHref, _) = decodeData(data)
        if (id == "0") {
            Log.d(TAG, "loadLinks: could not extract id from '$data'")
            return@coroutineScope false
        }

        // Try the real href we scraped from the channel list first (it may already be
        // the correct page), plus every guessed folder - all checked in parallel.
        val candidates = linkedSetOf<String>()
        if (!originalHref.isNullOrBlank()) candidates.add(originalHref)
        STREAM_FOLDERS.forEach { folder -> candidates.add("$mainUrl/$folder/stream-$id.php") }

        val jobs = candidates.map { pageUrl ->
            async { pageUrl to resolvePage(pageUrl) }
        }

        val results = jobs.awaitAll()
        val (winningUrl, link) = results.firstOrNull { it.second != null } ?: (null to null)

        if (link == null) {
            Log.d(TAG, "loadLinks: no stream found for id=$id across ${candidates.size} candidates")
            return@coroutineScope false
        }

        Log.d(TAG, "loadLinks: found stream for id=$id at $winningUrl")
        callback(link)
        true
    }

    // Tries a single page end-to-end: page -> direct m3u8, or page -> iframe -> m3u8,
    // or page -> iframe -> nested iframe -> m3u8. Returns null if this page has nothing.
    private suspend fun resolvePage(pageUrl: String): ExtractorLink? {
        val label = pageUrl.removePrefix(mainUrl).trim('/').substringBefore("?").ifBlank { pageUrl }
        try {
            val response = app.get(
                pageUrl,
                headers = siteHeaders + mapOf("Referer" to "$mainUrl/"),
                timeout = 20
            )

            val html = response.text
            if (html.length < 200) {
                Log.d(TAG, "resolvePage[$label]: response too short (${html.length} chars)")
                return null
            }
            if (html.contains("404") && html.contains("Not Found", ignoreCase = true)) {
                Log.d(TAG, "resolvePage[$label]: 404 page")
                return null
            }
            if (html.contains("Access Blocked", ignoreCase = true)) {
                Log.d(TAG, "resolvePage[$label]: access blocked")
                return null
            }

            findM3u8(html)?.let { m3u8 ->
                return buildLink("$name [$label]", m3u8, pageUrl)
            }

            val iframes = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1] }.toList()

            for (iframeSrc in iframes) {
                val iframeUrl = if (iframeSrc.startsWith("http")) iframeSrc
                                else "$mainUrl/${iframeSrc.trimStart('/')}"
                if (iframeUrl == pageUrl) continue

                try {
                    val iHtml = app.get(
                        iframeUrl,
                        headers = siteHeaders + mapOf("Referer" to pageUrl),
                        timeout = 20
                    ).text

                    findM3u8(iHtml)?.let { m3u8Inner ->
                        return buildLink("$name [iframe-$label]", m3u8Inner, iframeUrl)
                    }

                    val nested = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                        .find(iHtml)?.groupValues?.get(1)
                    if (nested != null) {
                        val nestedUrl = if (nested.startsWith("http")) nested
                                        else "$mainUrl/${nested.trimStart('/')}"
                        val nHtml = app.get(
                            nestedUrl,
                            headers = siteHeaders + mapOf("Referer" to iframeUrl),
                            timeout = 20
                        ).text
                        findM3u8(nHtml)?.let { m3u8N ->
                            return buildLink("$name [nested-$label]", m3u8N, nestedUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "resolvePage[$label]: iframe fetch failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "resolvePage[$label]: failed: ${e.message}")
        }

        return null
    }

    private suspend fun buildLink(sourceName: String, m3u8: String, referer: String): ExtractorLink {
        val originDomain = try {
            val uri = java.net.URI(referer)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            mainUrl
        }

        return newExtractorLink(
            source = name,
            name = sourceName,
            url = m3u8,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = referer
            this.quality = Qualities.Unknown.value
            this.headers = mapOf(
                "Referer" to referer,
                "Origin" to originDomain,
                "User-Agent" to siteHeaders["User-Agent"]!!
            )
        }
    }

    // ============================================================
    //  Helper: หา m3u8 URL ใน HTML (เหมือน PHP stream.php)
    // ============================================================
    private fun tryBase64Decode(encoded: String, flags: Int): String? {
        return try {
            String(Base64.decode(encoded, flags))
        } catch (e: Exception) {
            null
        }
    }

    private fun findM3u8(html: String): String? {
        // 1. atob(...) → base64 decode
        val atobMatch = Regex("""atob\s*\(\s*['"]([A-Za-z0-9+/=_-]{20,})['"]""").find(html)
        if (atobMatch != null) {
            val encoded = atobMatch.groupValues[1]
            // Try standard base64 first, then URL-safe (some sites use - and _ instead of + and /)
            val decoded = tryBase64Decode(encoded, Base64.DEFAULT)
                ?: tryBase64Decode(encoded, Base64.URL_SAFE)
            if (decoded != null && decoded.startsWith("http") && decoded.contains(".m3u8")) {
                return decoded.trim()
            }
        }

        // 2. JSON-like: "file":"...", source:"...", url:"..."
        val jsonMatch = Regex(
            """['"]?(?:file|source|src|url|stream|hls)['"]?\s*[=:]\s*['"]([^'"]+\.m3u8[^'"]*)['"]""",
            RegexOption.IGNORE_CASE
        ).find(html)
        if (jsonMatch != null) return jsonMatch.groupValues[1].trim()

        // 3. hls.loadSource("...")
        val hlsMatch = Regex("""hls\.loadSource\s*\(\s*['"]([^'"]+)['"]""").find(html)
        if (hlsMatch != null) return hlsMatch.groupValues[1].trim()

        // 4. var xxx = "...m3u8..."
        val varMatch = Regex("""var\s+\w+\s*=\s*['"]([^'"]+\.m3u8[^'"]*)['"]""").find(html)
        if (varMatch != null) return varMatch.groupValues[1].trim()

        // 5. src="...m3u8..."
        val srcMatch = Regex("""src=['"]([^'"]+\.m3u8[^'"]*)['"]""").find(html)
        if (srcMatch != null) return srcMatch.groupValues[1].trim()

        // 6. raw URL ใน HTML ทั่วไป
        val rawMatch = Regex("""(https?://[^\s'"<>\\]+\.m3u8[^\s'"<>\\]*)""").find(html)
        if (rawMatch != null) return rawMatch.groupValues[1].trim()

        return null
    }

    // ============================================================
    //  Helper: ดึง ID จาก href รูปแบบต่าง ๆ
    // ============================================================
    private fun extractIdFromHref(href: String): String? {
        // ?id=XX
        Regex("""[?&]id=(\d+)""").find(href)?.let { return it.groupValues[1] }
        // /stream-XX.php หรือ /watch-XX.php
        Regex("""[-/]stream-(\d+)""").find(href)?.let { return it.groupValues[1] }
        Regex("""[-/]watch-(\d+)""").find(href)?.let { return it.groupValues[1] }
        // /live/XX หรือ /channel/XX
        Regex("""(?:live|channel|watch)/(\d+)""").find(href)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractIdFromUrl(url: String): String? {
        Regex("""[?&]id=(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""stream-(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""dlhd_(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""/(\d+)$""").find(url)?.let { return it.groupValues[1] }
        return null
    }

    // ============================================================
    //  Helper: สร้าง URL ภายใน (เก็บ id ไว้ใช้ตอน loadLinks)
    // ============================================================
    private fun buildInternalUrl(id: String, href: String, title: String) = "$id::$href::$title"

    // Splits the combined "id::href::title" format back apart. Falls back to extracting
    // the id via regex if the data doesn't have the expected separator (e.g. old format).
    private fun decodeData(data: String): Triple<String, String?, String?> {
        val parts = data.split("::", limit = 3)
        return if (parts.size == 3) {
            Triple(parts[0], parts[1], parts[2])
        } else if (parts.size == 2) {
            Triple(parts[0], parts[1], null)
        } else {
            Triple(extractIdFromUrl(data) ?: "0", null, null)
        }
    }

    // ============================================================
    //  Logo map: id -> filename ครบทุก 523 ช่อง จาก playlist_gen.php
    //  PHP logo จริง → ใช้ตามเดิม, ไม่มี → auto-slug จากชื่อช่อง
    // ============================================================
    private val logoMap = mapOf(
        "31" to "tnt_sports_1_uk.png",  // TNT Sports 1 UK
        "32" to "tnt_sports_2_uk.png",  // TNT Sports 2 UK
        "33" to "tnt_sports_3_uk.png",  // TNT Sports 3 UK
        "34" to "tnt_sports_4_uk.png",  // TNT Sports 4 UK
        "35" to "sky_sports_football_uk.png",  // Sky Sports Football UK
        "36" to "sky_sports_plus.png",  // Sky Sports+ Plus
        "37" to "sky_sports_action_uk.png",  // Sky Sports Action UK
        "38" to "sky_sports_main_event.png",  // Sky Sports Main Event
        "46" to "sky_sports_tennis_uk.png",  // Sky Sports Tennis UK
        "130" to "sky_sports_premier_league.png",  // Sky Sports Premier League
        "60" to "sky_sports_f1_uk.png",  // Sky Sports F1 UK
        "65" to "sky_sports_cricket.png",  // Sky Sports Cricket
        "70" to "sky_sports_golf_uk.png",  // Sky Sports Golf UK
        "366" to "sky_sports_news_uk.png",  // Sky Sports News UK
        "449" to "sky_sports_mix_uk.png",  // Sky Sports MIX UK
        "554" to "sky_sports_racing_uk.png",  // Sky Sports Racing UK
        "276" to "lalagatv_uk.png",  // LaLigaTV UK
        "230" to "dazn_1_uk.png",  // DAZN 1 UK
        "451" to "viaplay_sports_1_uk.png",  // Viaplay Sports 1 UK
        "550" to "viaplay_sports_2_uk.png",  // Viaplay Sports 2 UK
        "377" to "mutv_uk.png",  // MUTV UK
        "826" to "liverpool_tv_lfc_tv.png",  // Liverpool TV (LFC TV)
        "771" to "premier_sports_ireland_1.png",  // Premier Sports Ireland 1
        "799" to "premier_sports_ireland_2.png",  // Premier Sports Ireland 2
        "44" to "espn_usa.png",  // ESPN USA
        "45" to "espn2_usa.png",  // ESPN2 USA
        "316" to "espnu_usa.png",  // ESPNU USA
        "288" to "espnews.png",  // ESPNews
        "375" to "espn_deportes.png",  // ESPN Deportes
        "39" to "fox_sports_1_usa.png",  // Fox Sports 1 USA
        "758" to "fox_sports_2_usa.png",  // Fox Sports 2 USA
        "756" to "fox_soccer_plus.png",  // FOX Soccer Plus
        "308" to "cbs_sports_network.png",  // CBS Sports Network
        "910" to "cbs_sports_golazo.png",  // CBS Sports Golazo
        "405" to "nfl_network.png",  // NFL Network
        "667" to "nfl_redzone.png",  // NFL RedZone
        "404" to "nba_tv_usa.png",  // NBA TV USA
        "399" to "mlb_network_usa.png",  // MLB Network USA
        "663" to "nhl_network_usa.png",  // NHL Network USA
        "318" to "golf_channel_usa.png",  // GOLF Channel USA
        "40" to "tennis_channel.png",  // Tennis Channel
        "425" to "bein_sports_usa.png",  // BeIN SPORTS USA
        "385" to "sec_network_usa.png",  // SEC Network USA
        "664" to "acc_network_usa.png",  // ACC Network USA
        "397" to "big_ten_network.png",  // BIG TEN Network
        "66" to "tudn_usa.png",  // TUDN USA
        "376" to "wwe_network.png",  // WWE Network
        "757" to "fight_network.png",  // Fight Network
        "43" to "pdc_tv.png",  // PDC TV
        "765" to "msg_usa.png",  // MSG USA
        "759" to "sportsnet_new_york_sny.png",  // SportsNet New York (SNY)
        "762" to "nesn_usa.png",  // NESN USA
        "763" to "yes_network_usa.png",  // YES Network USA
        "770" to "marquee_sports_network.png",  // Marquee Sports Network
        "776" to "chicago_sports_network.png",  // Chicago Sports Network
        "777" to "nbc_sports_philadelphia.png",  // NBC Sports Philadelphia
        "921" to "space_city_home_network.png",  // Space City Home Network
        "920" to "root_sports_northwest.png",  // Root Sports Northwest
        "982" to "spectrum_sportsnet_usa.png",  // Spectrum SportsNet USA
        "764" to "spectrum_sportsnet_la.png",  // Spectrum Sportsnet LA
        "890" to "fanduel_sports_az.png",  // FanDuel Sports AZ
        "891" to "fanduel_sports_detroit.png",  // FanDuel Sports Detroit
        "892" to "fanduel_sports_florida.png",  // FanDuel Sports Florida
        "893" to "fanduel_sports_great_lakes.png",  // FanDuel Sports Great Lakes
        "894" to "fanduel_sports_indiana.png",  // FanDuel Sports Indiana
        "895" to "fanduel_sports_kansas_city.png",  // FanDuel Sports Kansas City
        "896" to "fanduel_sports_midwest.png",  // FanDuel Sports Midwest
        "897" to "fanduel_sports_new_orleans.png",  // FanDuel Sports New Orleans
        "898" to "fanduel_sports_north.png",  // FanDuel Sports North
        "899" to "fanduel_sports_ohio.png",  // FanDuel Sports Ohio
        "900" to "fanduel_sports_oklahoma.png",  // FanDuel Sports Oklahoma
        "902" to "fanduel_sports_socal.png",  // FanDuel Sports SoCal
        "903" to "fanduel_sports_south.png",  // FanDuel Sports South
        "904" to "fanduel_sports_southeast.png",  // FanDuel Sports Southeast
        "905" to "fanduel_sports_sun.png",  // FanDuel Sports Sun
        "906" to "fanduel_sports_west.png",  // FanDuel Sports West
        "907" to "fanduel_sports_wisconsin.png",  // FanDuel Sports Wisconsin
        "61" to "bein_sports_mena_english_1.png",  // beIN Sports MENA English 1
        "90" to "bein_sports_mena_english_2.png",  // beIN Sports MENA English 2
        "91" to "bein_sports_1_arabic.png",  // beIN Sports 1 Arabic
        "92" to "bein_sports_2_arabic.png",  // beIN Sports 2 Arabic
        "93" to "bein_sports_3_arabic.png",  // beIN Sports 3 Arabic
        "94" to "bein_sports_4_arabic.png",  // beIN Sports 4 Arabic
        "95" to "bein_sports_5_arabic.png",  // beIN Sports 5 Arabic
        "96" to "bein_sports_6_arabic.png",  // beIN Sports 6 Arabic
        "97" to "bein_sports_7_arabic.png",  // beIN Sports 7 Arabic
        "98" to "bein_sports_8_arabic.png",  // beIN Sports 8 Arabic
        "99" to "bein_sports_9_arabic.png",  // beIN Sports 9 Arabic
        "100" to "bein_sports_xtra_1.png",  // beIN SPORTS XTRA 1
        "494" to "bein_sports_max_4_france.png",  // beIN Sports MAX 4 France
        "495" to "bein_sports_max_5_france.png",  // beIN Sports MAX 5 France
        "496" to "bein_sports_max_6_france.png",  // beIN Sports MAX 6 France
        "497" to "bein_sports_max_7_france.png",  // beIN Sports MAX 7 France
        "498" to "bein_sports_max_8_france.png",  // beIN Sports MAX 8 France
        "499" to "bein_sports_max_9_france.png",  // beIN Sports MAX 9 France
        "500" to "bein_sports_max_10_france.png",  // beIN Sports MAX 10 France
        "116" to "bein_sports_1_france.png",  // beIN SPORTS 1 France
        "117" to "bein_sports_2_france.png",  // beIN SPORTS 2 France
        "118" to "bein_sports_3_france.png",  // beIN SPORTS 3 France
        "62" to "bein_sports_1_turkey.png",  // beIN SPORTS 1 Turkey
        "63" to "bein_sports_2_turkey.png",  // beIN SPORTS 2 Turkey
        "64" to "bein_sports_3_turkey.png",  // beIN SPORTS 3 Turkey
        "67" to "bein_sports_4_turkey.png",  // beIN SPORTS 4 Turkey
        "1010" to "bein_sports_5_turkey.png",  // bein Sports 5 Turkey
        "578" to "bein_sports_hd_qatar.png",  // BeIN Sports HD Qatar
        "372" to "bein_sports_en_espanol.png",  // beIN SPORTS en Espanol
        "491" to "bein_sports_australia_1.png",  // beIN SPORTS Australia 1
        "492" to "bein_sports_australia_2.png",  // beIN SPORTS Australia 2
        "493" to "bein_sports_australia_3.png",  // beIN SPORTS Australia 3
        "712" to "bein_sports_1_malaysia.png",  // beIN Sports 1 Malaysia
        "713" to "bein_sports_2_malaysia.png",  // beIN Sports 2 Malaysia
        "714" to "bein_sports_3_malaysia.png",  // beIN Sports 3 Malaysia
        "271" to "canal_motogp_france.png",  // Canal+ MotoGP France
        "273" to "canal_formula_1.png",  // Canal+ Formula 1
        "121" to "canal_plus_france.png",  // Canal+ France
        "122" to "canal_sport_france.png",  // Canal+ Sport France
        "463" to "canal_foot_france.png",  // Canal+ Foot France
        "464" to "canal_sport360.png",  // Canal+ Sport360
        "119" to "rmc_sport_1_france.png",  // RMC Sport 1 France
        "120" to "rmc_sport_2_france.png",  // RMC Sport 2 France
        "960" to "dazn_ligue_1_france.png",  // DAZN Ligue 1 France
        "772" to "eurosport_1_france.png",  // Eurosport 1 France
        "773" to "eurosport_2_france.png",  // Eurosport 2 France
        "84" to "movistar_laliga.png",  // Movistar Laliga
        "435" to "movistar_liga_de_campeones.png",  // Movistar Liga de Campeones
        "436" to "movistar_deportes_spain.png",  // Movistar Deportes Spain
        "438" to "movistar_deportes_2_spain.png",  // Movistar Deportes 2 Spain
        "526" to "movistar_deportes_3_spain.png",  // Movistar Deportes 3 Spain
        "527" to "movistar_deportes_4_spain.png",  // Movistar Deportes 4 Spain
        "445" to "dazn_1_spain.png",  // DAZN 1 Spain
        "446" to "dazn_2_spain.png",  // DAZN 2 Spain
        "447" to "dazn_3_spain.png",  // DAZN 3 Spain
        "448" to "dazn_4_spain.png",  // DAZN 4 Spain
        "537" to "dazn_f1_es.png",  // DAZN F1 ES
        "538" to "dazn_laliga.png",  // DAZN LaLiga
        "524" to "eurosport_1_spain.png",  // EuroSport 1 Spain
        "525" to "eurosport_2_spain.png",  // EuroSport 2 Spain
        "530" to "gol_play_spain.png",  // GOL PLAY Spain
        "529" to "teledeporte_spain.png",  // Teledeporte Spain
        "523" to "real_madrid_tv_spain.png",  // Real Madrid TV Spain
        "522" to "barca_tv_spain.png",  // Barca TV Spain
        "240" to "sky_sports_1_de.png",  // Sky Sports 1 DE
        "241" to "sky_sports_2_de.png",  // Sky Sports 2 DE
        "558" to "sky_sport_bundesliga_1_hd.png",  // Sky Sport Bundesliga 1 HD
        "946" to "sky_sport_bundesliga_2.png",  // Sky Sport Bundesliga 2
        "947" to "sky_sport_bundesliga_3.png",  // Sky Sport Bundesliga 3
        "948" to "sky_sport_bundesliga_4.png",  // Sky Sport Bundesliga 4
        "949" to "sky_sport_bundesliga_5.png",  // Sky Sport Bundesliga 5
        "274" to "sky_sports_f1_de.png",  // Sky Sports F1 DE
        "426" to "dazn_1_bar_de.png",  // DAZN 1 Bar DE
        "427" to "dazn_2_bar_de.png",  // DAZN 2 Bar DE
        "641" to "sport_1_germany.png",  // Sport 1 Germany
        "571" to "sportdigital_fussball.png",  // SportDigital Fussball
        "460" to "sky_sport_max_italy.png",  // Sky Sport MAX Italy
        "461" to "sky_sport_uno_italy.png",  // Sky Sport UNO Italy
        "462" to "sky_sport_arena_italy.png",  // Sky Sport Arena Italy
        "869" to "sky_sport_24_italy.png",  // Sky Sport 24 Italy
        "870" to "sky_sport_calcio_italy.png",  // Sky Sport Calcio Italy
        "871" to "sky_calcio_1_italy.png",  // Sky Calcio 1 Italy
        "872" to "sky_calcio_2_italy.png",  // Sky Calcio 2 Italy
        "873" to "sky_calcio_3_italy.png",  // Sky Calcio 3 Italy
        "874" to "sky_calcio_4_italy.png",  // Sky Calcio 4 Italy
        "875" to "sky_sport_basket_italy.png",  // Sky Sport Basket Italy
        "577" to "sky_sport_f1_italy.png",  // Sky Sport F1 Italy
        "575" to "sky_sport_motogp_italy.png",  // Sky Sport MotoGP Italy
        "878" to "eurosport_1_italy.png",  // EuroSport 1 Italy
        "879" to "eurosport_2_italy.png",  // EuroSport 2 Italy
        "877" to "dazn_zona_italy.png",  // DAZN ZONA Italy
        "882" to "rai_sport_italy.png",  // Rai Sport Italy
        "48" to "canal_sport_poland.png",  // Canal+ Sport Poland
        "73" to "canal_sport_2_poland.png",  // Canal+ Sport 2 Poland
        "259" to "canal_sport_3_poland.png",  // Canal+ Sport 3 Poland
        "983" to "canal_extra_1_7_poland.png",  // Canal+ Extra 1-7 Poland
        "47" to "polsat_sport_poland.png",  // Polsat Sport Poland
        "50" to "polsat_sport_2_poland.png",  // Polsat Sport 2 Poland
        "129" to "polsat_sport_3_poland.png",  // Polsat Sport 3 Poland
        "991" to "polsat_sport_premium_1.png",  // Polsat Sport Premium 1
        "992" to "polsat_sport_premium_2.png",  // Polsat Sport Premium 2
        "993" to "polsat_sport_extra_1.png",  // Polsat Sport Extra 1
        "994" to "polsat_sport_extra_2.png",  // Polsat Sport Extra 2
        "995" to "polsat_sport_extra_3.png",  // Polsat Sport Extra 3
        "996" to "polsat_sport_extra_4.png",  // Polsat Sport Extra 4
        "997" to "polsat_sport_fight.png",  // Polsat Sport Fight
        "998" to "polsat_sport_news.png",  // Polsat Sport NEWS
        "71" to "eleven_sports_1_poland.png",  // Eleven Sports 1 Poland
        "72" to "eleven_sports_2_poland.png",  // Eleven Sports 2 Poland
        "428" to "eleven_sports_3_poland.png",  // Eleven Sports 3 Poland
        "999" to "eleven_sports_4_poland.png",  // Eleven Sports 4 Poland
        "57" to "eurosport_1_poland.png",  // EuroSport 1 Poland
        "58" to "eurosport_2_poland.png",  // EuroSport 2 Poland
        "128" to "tvp_sport_poland.png",  // TVP Sport Poland
        "49" to "sport_tv1_portugal.png",  // Sport TV1 Portugal
        "74" to "sport_tv2_portugal.png",  // Sport TV2 Portugal
        "454" to "sport_tv3_portugal.png",  // Sport TV3 Portugal
        "289" to "sport_tv4_portugal.png",  // Sport TV4 Portugal
        "290" to "sport_tv5_portugal.png",  // Sport TV5 Portugal
        "291" to "sport_tv6_portugal.png",  // Sport TV6 Portugal
        "455" to "eleven_sports_1_portugal.png",  // Eleven Sports 1 Portugal
        "456" to "eleven_sports_2_portugal.png",  // Eleven Sports 2 Portugal
        "457" to "eleven_sports_3_portugal.png",  // Eleven Sports 3 Portugal
        "458" to "eleven_sports_4_portugal.png",  // Eleven Sports 4 Portugal
        "459" to "eleven_sports_5_portugal.png",  // Eleven Sports 5 Portugal
        "380" to "benfica_tv_pt.png",  // Benfica TV PT
        "41" to "eurosport_1_greece.png",  // EuroSport 1 Greece
        "42" to "eurosport_2_greece.png",  // EuroSport 2 Greece
        "631" to "nova_sports_1_greece.png",  // Nova Sports 1 Greece
        "632" to "nova_sports_2_greece.png",  // Nova Sports 2 Greece
        "633" to "nova_sports_3_greece.png",  // Nova Sports 3 Greece
        "634" to "nova_sports_4_greece.png",  // Nova Sports 4 Greece
        "635" to "nova_sports_5_greece.png",  // Nova Sports 5 Greece
        "636" to "nova_sports_6_greece.png",  // Nova Sports 6 Greece
        "599" to "nova_sports_premier_league.png",  // Nova Sports Premier League
        "622" to "cosmote_sport_1_hd.png",  // Cosmote Sport 1 HD
        "623" to "cosmote_sport_2_hd.png",  // Cosmote Sport 2 HD
        "624" to "cosmote_sport_3_hd.png",  // Cosmote Sport 3 HD
        "625" to "cosmote_sport_4_hd.png",  // Cosmote Sport 4 HD
        "626" to "cosmote_sport_5_hd.png",  // Cosmote Sport 5 HD
        "627" to "cosmote_sport_6_hd.png",  // Cosmote Sport 6 HD
        "628" to "cosmote_sport_7_hd.png",  // Cosmote Sport 7 HD
        "629" to "cosmote_sport_8_hd.png",  // Cosmote Sport 8 HD
        "630" to "cosmote_sport_9_hd.png",  // Cosmote Sport 9 HD
        "400" to "digi_sport_1_romania.png",  // Digi Sport 1 Romania
        "401" to "digi_sport_2_romania.png",  // Digi Sport 2 Romania
        "402" to "digi_sport_3_romania.png",  // Digi Sport 3 Romania
        "403" to "digi_sport_4_romania.png",  // Digi Sport 4 Romania
        "439" to "orange_sport_1_romania.png",  // Orange Sport 1 Romania
        "440" to "orange_sport_2_romania.png",  // Orange Sport 2 Romania
        "441" to "orange_sport_3_romania.png",  // Orange Sport 3 Romania
        "442" to "orange_sport_4_romania.png",  // Orange Sport 4 Romania
        "583" to "prima_sport_1.png",  // Prima Sport 1
        "584" to "prima_sport_2.png",  // Prima Sport 2
        "585" to "prima_sport_3.png",  // Prima Sport 3
        "586" to "prima_sport_4.png",  // Prima Sport 4
        "134" to "arena_sport_1_premium.png",  // Arena Sport 1 Premium
        "135" to "arena_sport_2_premium.png",  // Arena Sport 2 Premium
        "429" to "arena_sport_1_serbia.png",  // Arena Sport 1 Serbia
        "430" to "arena_sport_2_serbia.png",  // Arena Sport 2 Serbia
        "431" to "arena_sport_3_serbia.png",  // Arena Sport 3 Serbia
        "581" to "arena_sport_4_serbia.png",  // Arena Sport 4 Serbia
        "940" to "arena_sport_5_10_serbia.png",  // Arena Sport 5-10 Serbia
        "432" to "arena_sport_1_croatia.png",  // Arena Sport 1 Croatia
        "433" to "arena_sport_2_croatia.png",  // Arena Sport 2 Croatia
        "434" to "arena_sport_3_croatia.png",  // Arena Sport 3 Croatia
        "580" to "arena_sport_4_croatia.png",  // Arena Sport 4 Croatia
        "579" to "arena_sport_1_bih.png",  // Arena Sport 1 BiH
        "101" to "sport_klub_1_croatia.png",  // Sport Klub 1 Croatia
        "102" to "sport_klub_2_croatia.png",  // Sport Klub 2 Croatia
        "103" to "sport_klub_3_croatia.png",  // Sport Klub 3 Croatia
        "104" to "sport_klub_4_croatia.png",  // Sport Klub 4 Croatia
        "582" to "nova_sport_serbia.png",  // Nova Sport Serbia
        "779" to "max_sport_1_croatia.png",  // Max Sport 1 Croatia
        "780" to "max_sport_2_croatia.png",  // Max Sport 2 Croatia
        "465" to "diema_sport_bulgaria.png",  // Diema Sport Bulgaria
        "466" to "diema_sport_2_bulgaria.png",  // Diema Sport 2 Bulgaria
        "467" to "diema_sport_3_bulgaria.png",  // Diema Sport 3 Bulgaria
        "468" to "nova_sport_bulgaria.png",  // Nova Sport Bulgaria
        "472" to "max_sport_1_bulgaria.png",  // Max Sport 1 Bulgaria
        "473" to "max_sport_2_bulgaria.png",  // Max Sport 2 Bulgaria
        "474" to "max_sport_3_bulgaria.png",  // Max Sport 3 Bulgaria
        "475" to "max_sport_4_bulgaria.png",  // Max Sport 4 Bulgaria
        "600" to "abu_dhabi_sports_1_uae.png",  // Abu Dhabi Sports 1 UAE
        "601" to "abu_dhabi_sports_2_uae.png",  // Abu Dhabi Sports 2 UAE
        "604" to "dubai_sports_1_uae.png",  // Dubai Sports 1 UAE
        "605" to "dubai_sports_2_uae.png",  // Dubai Sports 2 UAE
        "606" to "dubai_sports_3_uae.png",  // Dubai Sports 3 UAE
        "781" to "alkass_one.png",  // Alkass One
        "782" to "alkass_two.png",  // Alkass Two
        "783" to "alkass_three.png",  // Alkass Three
        "784" to "alkass_four.png",  // Alkass Four
        "611" to "ontime_sports.png",  // OnTime Sports
        "614" to "ssc_sport_1.png",  // SSC Sport 1
        "615" to "ssc_sport_2.png",  // SSC Sport 2
        "616" to "ssc_sport_3.png",  // SSC Sport 3
        "617" to "ssc_sport_4.png",  // SSC Sport 4
        "618" to "ssc_sport_5.png",  // SSC Sport 5
        "619" to "ssc_sport_extra_1.png",  // SSC Sport Extra 1
        "620" to "ssc_sport_extra_2.png",  // SSC Sport Extra 2
        "621" to "ssc_sport_extra_3.png",  // SSC Sport Extra 3
        "412" to "supersport_grandstand.png",  // SuperSport Grandstand
        "413" to "supersport_psl.png",  // SuperSport PSL
        "414" to "supersport_premier_league.png",  // SuperSport Premier League
        "415" to "supersport_laliga.png",  // SuperSport LaLiga
        "416" to "supersport_variety_1.png",  // SuperSport Variety 1
        "417" to "supersport_variety_2.png",  // SuperSport Variety 2
        "418" to "supersport_variety_3.png",  // SuperSport Variety 3
        "419" to "supersport_variety_4.png",  // SuperSport Variety 4
        "420" to "supersport_action.png",  // SuperSport Action
        "421" to "supersport_rugby.png",  // SuperSport Rugby
        "422" to "supersport_golf.png",  // SuperSport Golf
        "423" to "supersport_tennis.png",  // SuperSport Tennis
        "424" to "supersport_motorsport.png",  // SuperSport Motorsport
        "56" to "supersport_football.png",  // Supersport Football
        "368" to "supersport_cricket.png",  // SuperSport Cricket
        "123" to "astro_supersport_1.png",  // Astro SuperSport 1
        "124" to "astro_supersport_2.png",  // Astro SuperSport 2
        "125" to "astro_supersport_3.png",  // Astro SuperSport 3
        "126" to "astro_supersport_4.png",  // Astro SuperSport 4
        "267" to "star_sports_1_in.png",  // Star Sports 1 IN
        "268" to "star_sports_hindi_in.png",  // Star Sports Hindi IN
        "885" to "sony_ten_1.png",  // SONY TEN 1
        "886" to "sony_ten_2.png",  // SONY TEN 2
        "887" to "sony_ten_3.png",  // SONY TEN 3
        "450" to "ptv_sports.png",  // PTV Sports
        "269" to "a_sport_pk.png",  // A Sport PK
        "741" to "ten_sports_pk.png",  // Ten Sports PK
        "270" to "t_sports_bd.png",  // T Sports BD
        "346" to "willow_cricket.png",  // Willow Cricket
        "598" to "willow_2_cricket.png",  // Willow 2 Cricket
        "369" to "fox_cricket.png",  // Fox Cricket
        "111" to "tsn1.png",  // TSN1
        "112" to "tsn2.png",  // TSN2
        "113" to "tsn3.png",  // TSN3
        "114" to "tsn4.png",  // TSN4
        "115" to "tsn5.png",  // TSN5
        "406" to "sportsnet_ontario.png",  // Sportsnet Ontario
        "407" to "sportsnet_west.png",  // Sportsnet West
        "408" to "sportsnet_east.png",  // Sportsnet East
        "409" to "sportsnet_360.png",  // Sportsnet 360
        "410" to "sportsnet_world.png",  // Sportsnet World
        "411" to "sportsnet_one.png",  // Sportsnet One
        "839" to "rds_ca.png",  // RDS CA
        "840" to "rds_2_ca.png",  // RDS 2 CA
        "833" to "tva_sports.png",  // TVA Sports
        "834" to "tva_sports_2.png",  // TVA Sports 2
        "78" to "sportv_brasil.png",  // SporTV Brasil
        "79" to "sportv2_brasil.png",  // SporTV2 Brasil
        "80" to "sportv3_brasil.png",  // SporTV3 Brasil
        "81" to "espn_brasil.png",  // ESPN Brasil
        "82" to "espn2_brasil.png",  // ESPN2 Brasil
        "83" to "espn3_brasil.png",  // ESPN3 Brasil
        "149" to "espn_argentina.png",  // ESPN Argentina
        "150" to "espn2_argentina.png",  // ESPN2 Argentina
        "787" to "fox_sports_argentina.png",  // Fox Sports Argentina
        "388" to "tnt_sports_argentina.png",  // TNT Sports Argentina
        "746" to "tyc_sports_argentina.png",  // TyC Sports Argentina
        "925" to "espn_1_mx.png",  // ESPN 1 MX
        "926" to "espn_2_mx.png",  // ESPN 2 MX
        "929" to "fox_sports_1_mx.png",  // Fox Sports 1 MX
        "930" to "fox_sports_2_mx.png",  // Fox Sports 2 MX
        "933" to "claro_sports_mx.png",  // Claro Sports MX
        "935" to "tudn_mx.png",  // TUDN MX
        "588" to "sky_sport_1_nz.png",  // Sky Sport 1 NZ
        "589" to "sky_sport_2_nz.png",  // Sky Sport 2 NZ
        "590" to "sky_sport_3_nz.png",  // Sky Sport 3 NZ
        "591" to "sky_sport_4_nz.png",  // Sky Sport 4 NZ
        "592" to "sky_sport_5_nz.png",  // Sky Sport 5 NZ
        "820" to "fox_sports_502_au.png",  // FOX Sports 502 AU
        "821" to "fox_sports_503_au.png",  // FOX Sports 503 AU
        "822" to "fox_sports_504_au.png",  // FOX Sports 504 AU
        "823" to "fox_sports_505_au.png",  // FOX Sports 505 AU
        "231" to "eurosport_1_sw.png",  // Eurosport 1 SW
        "232" to "eurosport_2_sw.png",  // Eurosport 2 SW
        "703" to "tv4_sport_live_1.png",  // TV4 Sport Live 1
        "704" to "tv4_sport_live_2.png",  // TV4 Sport Live 2
        "705" to "tv4_sport_live_3.png",  // TV4 Sport Live 3
        "706" to "tv4_sport_live_4.png",  // TV4 Sport Live 4
        "707" to "tv4_sportkanalen.png",  // TV4 Sportkanalen
        "808" to "tv2_sport_x_denmark.png",  // TV2 Sport X Denmark
        "809" to "tv3_sport_denmark.png",  // TV3 Sport Denmark
        "810" to "tv2_sport_denmark.png",  // TV2 Sport Denmark
        "379" to "espn_1_nl.png",  // ESPN 1 NL
        "386" to "espn_2_nl.png",  // ESPN 2 NL
        "888" to "espn_3_nl.png",  // ESPN 3 NL
        "393" to "ziggo_sport_nl.png",  // Ziggo Sport NL
        "398" to "ziggo_sport_2_nl.png",  // Ziggo Sport 2 NL
        "919" to "ziggo_sport_3_nl.png",  // Ziggo Sport 3 NL
        "396" to "ziggo_sport_4_nl.png",  // Ziggo Sport 4 NL
        "383" to "ziggo_sport_5_nl.png",  // Ziggo Sport 5 NL
        "901" to "ziggo_sport_6_nl.png",  // Ziggo Sport 6 NL
        "140" to "sport_1_israel.png",  // Sport 1 Israel
        "141" to "sport_2_israel.png",  // Sport 2 Israel
        "142" to "sport_3_israel.png",  // Sport 3 Israel
        "143" to "sport_4_israel.png",  // Sport 4 Israel
        "144" to "sport_5_israel.png",  // Sport 5 Israel
        "145" to "sport_5_plus_israel.png",  // Sport 5 PLUS Israel
        "146" to "sport_5_live_israel.png",  // Sport 5 Live Israel
        "147" to "sport_5_star_israel.png",  // Sport 5 Star Israel
        "148" to "sport_5_gold_israel.png",  // Sport 5 Gold Israel
        "136" to "match_football_1_russia.png",  // Match Football 1 Russia
        "137" to "match_football_2_russia.png",  // Match Football 2 Russia
        "138" to "match_football_3_russia.png",  // Match Football 3 Russia
        "127" to "match_tv_russia.png",  // Match TV Russia
        "1011" to "a_spor_turkey.png",  // A Spor Turkey
        "889" to "trt_spor_tr.png",  // TRT Spor TR
        "911" to "cytavision_sports_1_cyprus.png",  // Cytavision Sports 1 Cyprus
        "912" to "cytavision_sports_2_cyprus.png",  // Cytavision Sports 2 Cyprus
        "913" to "cytavision_sports_3_cyprus.png",  // Cytavision Sports 3 Cyprus
        "1021" to "nova_sport_1_cz.png",  // Nova Sport 1 CZ
        "1022" to "nova_sport_2_cz.png",  // Nova Sport 2 CZ
        "1023" to "nova_sport_3_cz.png",  // Nova Sport 3 CZ
        "1024" to "nova_sport_4_cz.png",  // Nova Sport 4 CZ
        "1033" to "ct_sport_cz.png",  // CT Sport CZ
        "1020" to "canal_sport_cz.png",  // Canal+ Sport CZ
        "1030" to "premier_sport_1_cz.png",  // Premier Sport 1 CZ
        "1031" to "premier_sport_2_cz.png",  // Premier Sport 2 CZ
        "1052" to "joj_spor_sk.png",  // JOJ Spor SK
        "1063" to "canal_sport_sk.png",  // Canal+ Sport SK
        "356" to "bbc_one_uk.png",  // BBC One UK
        "357" to "bbc_two_uk.png",  // BBC Two UK
        "358" to "bbc_three_uk.png",  // BBC Three UK
        "359" to "bbc_four_uk.png",  // BBC Four UK
        "349" to "bbc_news_channel_hd.png",  // BBC News Channel HD
        "350" to "itv_1_uk.png",  // ITV 1 UK
        "351" to "itv_2_uk.png",  // ITV 2 UK
        "352" to "itv_3_uk.png",  // ITV 3 UK
        "353" to "itv_4_uk.png",  // ITV 4 UK
        "354" to "channel_4_uk.png",  // Channel 4 UK
        "355" to "channel_5_uk.png",  // Channel 5 UK
        "361" to "sky_witness_hd.png",  // Sky Witness HD
        "362" to "sky_atlantic.png",  // Sky Atlantic
        "363" to "e4_channel.png",  // E4 Channel
        "348" to "dave.png",  // Dave
        "687" to "gold_uk.png",  // Gold UK
        "688" to "film4_uk.png",  // Film4 UK
        "682" to "sky_showcase_uk.png",  // Sky Showcase UK
        "683" to "sky_arts_uk.png",  // Sky Arts UK
        "684" to "sky_comedy_uk.png",  // Sky Comedy UK
        "685" to "sky_crime.png",  // Sky Crime
        "686" to "sky_history.png",  // Sky History
        "708" to "sky_max_uk.png",  // Sky MAX UK
        "364" to "rte_1.png",  // RTE 1
        "365" to "rte_2.png",  // RTE 2
        "51" to "abc_usa.png",  // ABC USA
        "52" to "cbs_usa.png",  // CBS USA
        "53" to "nbc_usa.png",  // NBC USA
        "54" to "fox_usa.png",  // FOX USA
        "345" to "cnn_usa.png",  // CNN USA
        "327" to "msnbc.png",  // MSNBC
        "347" to "fox_news.png",  // Fox News
        "309" to "cnbc_usa.png",  // CNBC USA
        "336" to "tbs_usa.png",  // TBS USA
        "338" to "tnt_usa.png",  // TNT USA
        "303" to "amc_usa.png",  // AMC USA
        "302" to "a_e_usa.png",  // A&E USA
        "317" to "fx_usa.png",  // FX USA
        "307" to "bravo_usa.png",  // Bravo USA
        "315" to "e_entertainment.png",  // E! Entertainment
        "326" to "lifetime_network.png",  // Lifetime Network
        "320" to "hallmark_channel.png",  // Hallmark Channel
        "382" to "hgtv.png",  // HGTV
        "384" to "the_food_network.png",  // The Food Network
        "337" to "tlc.png",  // TLC
        "313" to "discovery_channel.png",  // Discovery Channel
        "304" to "animal_planet.png",  // Animal Planet
        "322" to "history_usa.png",  // History USA
        "328" to "national_geographic.png",  // National Geographic
        "294" to "science_channel.png",  // Science Channel
        "373" to "syfy_usa.png",  // SYFY USA
        "339" to "cartoon_network.png",  // Cartoon Network
        "312" to "disney_channel.png",  // Disney Channel
        "330" to "nick.png",  // NICK
        "310" to "comedy_central.png",  // Comedy Central
        "371" to "mtv_usa.png",  // MTV USA
        "344" to "vh1_usa.png",  // VH1 USA
        "321" to "hbo_usa.png",  // HBO USA
        "689" to "hbo2_usa.png",  // HBO2 USA
        "374" to "cinemax_usa.png",  // Cinemax USA
        "333" to "showtime_usa.png",  // Showtime USA
        "335" to "starz.png",  // Starz
        "970" to "starz_cinema.png",  // Starz Cinema
        "975" to "starz_encore.png",  // Starz Encore
        "334" to "paramount_network.png",  // Paramount Network
        "671" to "sky_cinema_premiere_uk.png",  // Sky Cinema Premiere UK
        "672" to "sky_cinema_select_uk.png",  // Sky Cinema Select UK
        "673" to "sky_cinema_hits_uk.png",  // Sky Cinema Hits UK
        "677" to "sky_cinema_action_uk.png",  // Sky Cinema Action UK
        "678" to "sky_cinema_comedy_uk.png",  // Sky Cinema Comedy UK
        "680" to "sky_cinema_drama_uk.png",  // Sky Cinema Drama UK
        "860" to "sky_cinema_uno_italy.png",  // Sky Cinema Uno Italy
        "861" to "sky_cinema_action_italy.png",  // Sky Cinema Action Italy
        "862" to "sky_cinema_comedy_italy.png",  // Sky Cinema Comedy Italy
        "864" to "sky_cinema_romance_italy.png",  // Sky Cinema Romance Italy
        "469" to "tf1_france.png",  // TF1 France
        "470" to "m6_france.png",  // M6 France
        "950" to "france_2.png",  // France 2
        "951" to "france_3.png",  // France 3
        "952" to "france_4.png",  // France 4
        "953" to "france_5.png",  // France 5
        "956" to "c8_france.png",  // C8 France
        "957" to "bfm_tv_france.png",  // BFM TV France
        "958" to "arte_france.png",  // Arte France
        "850" to "rai_1_italy.png",  // Rai 1 Italy
        "851" to "rai_2_italy.png",  // Rai 2 Italy
        "852" to "rai_3_italy.png",  // Rai 3 Italy
        "854" to "italia_1_italy.png",  // Italia 1 Italy
        "855" to "la7_italy.png",  // La7 Italy
        "881" to "sky_uno_italy.png",  // Sky UNO Italy
        "533" to "tve_la_1_spain.png",  // TVE La 1 Spain
        "532" to "telecinco_spain.png",  // Telecinco Spain
        "531" to "antena_3_spain.png",  // Antena 3 Spain
        "727" to "zdf_de.png",  // ZDF DE
        "740" to "rtl_de.png",  // RTL DE
        "729" to "sat_1_de.png",  // SAT.1 DE
        "730" to "prosieben_de.png",  // ProSieben DE
        "719" to "rtp_1_portugal.png",  // RTP 1 Portugal
        "720" to "rtp_2_portugal.png",  // RTP 2 Portugal
        "722" to "sic_portugal.png",  // SIC Portugal
        "723" to "tvi_portugal.png",  // TVI Portugal
        "479" to "btv_bulgaria.png",  // bTV Bulgaria
        "480" to "nova_tv_bulgaria.png",  // Nova TV Bulgaria
        "476" to "bnt_1_bulgaria.png",  // BNT 1 Bulgaria
        "602" to "ctv_canada.png",  // CTV Canada
        "832" to "cbc_ca.png",  // CBC CA
        "836" to "global_ca.png",  // Global CA
        "1000" to "atv_turkey.png",  // ATV Turkey
        "1001" to "kanal_d_turkey.png",  // Kanal D Turkey
        "1002" to "show_tv_turkey.png",  // Show TV Turkey
        "801" to "dr1_denmark.png",  // DR1 Denmark
        "802" to "dr2_denmark.png",  // DR2 Denmark
        "817" to "tv2_denmark.png",  // TV2 Denmark
        "390" to "rtl7_netherland.png",  // RTL7 Netherland
        "378" to "veronica_nl.png",  // Veronica NL
        "546" to "channel_9_israel.png",  // Channel 9 Israel
        "549" to "channel_12_israel.png",  // Channel 12 Israel
        "833" to "tva_sports.png",  // TVA Sports
        "1034" to "nova_hd_cz.png",  // Nova HD CZ
        "1035" to "ct1_hd_cz.png",  // CT1 HD CZ
        "1050" to "joj_sk.png",  // JOJ SK
        "760" to "globo_sp.png",  // Globo SP
        "761" to "globo_rio.png",  // Globo RIO
        "934" to "azteca_uno_mx.png",  // Azteca Uno MX
        "924" to "las_estrellas.png",  // Las Estrellas
        "843" to "prima_tv_ro.png",  // Prima TV RO
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IptvOrgChannel(
        @JsonProperty("name") val name: String?,
        @JsonProperty("logo") val logo: String?
    )

    private fun normalizeName(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    // Downloads and indexes iptv-org's public channel database (name -> logo url).
    // Only runs once per app session; cached in the companion object after that.
    private suspend fun getIptvOrgLogoIndex(): Map<String, String>? {
        iptvOrgLogoIndex?.let { return it }

        return try {
            val json = app.get("https://iptv-org.github.io/api/channels.json", timeout = 20).text
            val channels: List<IptvOrgChannel> = jsonMapper.readValue(json)

            val index = HashMap<String, String>()
            for (ch in channels) {
                val name = ch.name ?: continue
                val logo = ch.logo ?: continue
                if (logo.isBlank()) continue
                val key = normalizeName(name)
                if (!index.containsKey(key)) index[key] = logo
            }

            Log.d(TAG, "getIptvOrgLogoIndex: loaded ${index.size} entries")
            iptvOrgLogoIndex = index
            index
        } catch (e: Exception) {
            Log.d(TAG, "getIptvOrgLogoIndex: failed to load - ${e.message}")
            null
        }
    }

    // Tries the exact channel title first, then progressively drops trailing words
    // (dlhd.st often suffixes titles with a country name that iptv-org doesn't use,
    // e.g. "BNT 2 Bulgaria" -> "BNT 2", "ESPN USA" -> "ESPN").
    private suspend fun matchLogoByName(title: String): String? {
        val index = getIptvOrgLogoIndex() ?: return null
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

        // Only try the full name, then the name with just the last word dropped
        // (e.g. "Sky Sports Golf DE" -> "Sky Sports Golf"). Dropping further than
        // that risks landing on a short/generic string that collides with an
        // unrelated channel elsewhere in the database.
        for (dropCount in 0..1) {
            val keep = words.size - dropCount
            if (keep < 1) break
            val candidate = words.subList(0, keep).joinToString(" ")
            val key = normalizeName(candidate)
            // Require a reasonably specific candidate - very short leftover
            // strings ("de", "tnt") are too generic to trust a match on.
            if (key.length < 5) continue
            index[key]?.let { return it }
        }
        return null
    }

    // Fast path for list views (getMainPage/search) - no network, no fuzzy matching,
    // just the static map. Keeps browsing snappy even with hundreds of channels per page.
    private fun getLogoUrlFast(id: String): String {
        logoMap[id]?.let { filename -> return "$mainUrl/logos/$filename" }
        return "$mainUrl/assets/logos/logo.png"
    }

    // Full lookup used only when opening a single channel's detail page - worth the
    // extra iptv-org matching cost here since it's just one channel, not hundreds.
    private suspend fun getLogoUrl(id: String, title: String): String? {
        logoMap[id]?.let { filename -> return "$mainUrl/logos/$filename" }
        matchLogoByName(title)?.let { return it }
        return "$mainUrl/assets/logos/logo.png"
    }
}
