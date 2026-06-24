package com.DaddyLiveHD

import android.util.Base64
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

        // ลองหลาย logo base URL (dlhd.pk เปลี่ยนบ่อย)
        private val LOGO_BASES = listOf(
            "https://dlhd.pk/logos/",
            "https://dlhd.pk/assets/logos/",
            "https://dlhd.pk/images/logos/",
            "https://dlhd.pk/img/logos/"
        )

        // folder ที่ stream page อาจอยู่
        private val STREAM_FOLDERS = listOf("stream", "cast", "watch", "plus", "casting", "player", "live")
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

            // Logo: ลอง path ต่าง ๆ — ใส่ path แรกก่อน (Cloudstream จะ fallback เองถ้าโหลดไม่ได้)
            val logoSlug = titleToLogoSlug(title)
            val logoUrl  = "${LOGO_BASES[0]}$logoSlug.png"

            val searchItem = newLiveSearchResponse(
                name = title,
                url  = buildInternalUrl(id),
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
            .map { (cat, items) -> HomePageList(cat, items, isHorizontalImages = false) }

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
                val id    = extractIdFromHref(link.attr("href")) ?: return@mapNotNull null
                val logoSlug = titleToLogoSlug(title)
                newLiveSearchResponse(
                    name = title,
                    url  = buildInternalUrl(id),
                    type = TvType.Live
                ) {
                    this.posterUrl = "${LOGO_BASES[0]}$logoSlug.png"
                }
            }
    }

    // ============================================================
    //  โหลดหน้าช่อง
    // ============================================================
    override suspend fun load(url: String): LoadResponse {
        val id    = extractIdFromUrl(url) ?: "0"
        val title = "Channel $id"
        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = "${LOGO_BASES[0]}channel_${id}.png"
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
    ): Boolean {
        val id = extractIdFromUrl(data) ?: return false
        var found = false

        for (folder in STREAM_FOLDERS) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val response = app.get(
                    pageUrl,
                    headers = siteHeaders + mapOf("Referer" to "$mainUrl/"),
                    timeout = 20
                )

                // ข้ามถ้า page ไม่มีอยู่ หรือ blocked
                val html = response.text
                if (html.length < 200) continue
                if (html.contains("404") && html.contains("Not Found", ignoreCase = true)) continue
                if (html.contains("Access Blocked", ignoreCase = true)) continue

                // พยายามหา m3u8 จาก HTML โดยตรง
                val m3u8 = findM3u8(html)
                if (m3u8 != null) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name   = "$name [$folder]",
                            url    = m3u8,
                            type   = ExtractorLinkType.M3U8
                        ) {
                            this.referer = pageUrl
                            this.quality = Qualities.Unknown.value
                            this.headers = mapOf(
                                "Referer"    to pageUrl,
                                "Origin"     to mainUrl,
                                "User-Agent" to siteHeaders["User-Agent"]!!
                            )
                        }
                    )
                    found = true
                    break
                }

                // ไม่เจอ m3u8 ตรง ๆ → ตาม iframe ชั้นแรก
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

                        val m3u8Inner = findM3u8(iHtml)
                        if (m3u8Inner != null) {
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
                                        "User-Agent" to siteHeaders["User-Agent"]!!
                                    )
                                }
                            )
                            found = true
                            break
                        }

                        // iframe ซ้อน iframe (ชั้นที่ 2)
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
                            val m3u8N = findM3u8(nHtml)
                            if (m3u8N != null) {
                                callback(
                                    newExtractorLink(
                                        source = name,
                                        name   = "$name [nested-$folder]",
                                        url    = m3u8N,
                                        type   = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = nestedUrl
                                        this.quality = Qualities.Unknown.value
                                        this.headers = mapOf(
                                            "Referer"    to nestedUrl,
                                            "Origin"     to mainUrl,
                                            "User-Agent" to siteHeaders["User-Agent"]!!
                                        )
                                    }
                                )
                                found = true
                                break
                            }
                        }
                    } catch (_: Exception) { }
                }

                if (found) break

            } catch (_: Exception) {
                // ลอง folder ถัดไป
            }
        }

        return found
    }

    // ============================================================
    //  Helper: หา m3u8 URL ใน HTML (เหมือน PHP stream.php)
    // ============================================================
    private fun findM3u8(html: String): String? {
        // 1. atob(...) → base64 decode
        val atobMatch = Regex("""atob\s*\(\s*['"]([A-Za-z0-9+/=]{20,})['"]""").find(html)
        if (atobMatch != null) {
            try {
                val decoded = String(Base64.decode(atobMatch.groupValues[1], Base64.DEFAULT))
                if (decoded.startsWith("http") && decoded.contains(".m3u8"))
                    return decoded.trim()
            } catch (_: Exception) { }
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
    private fun buildInternalUrl(id: String) = "$mainUrl/stream/stream-$id.php"

    // ============================================================
    //  Helper: แปลงชื่อช่องเป็น logo slug (ลองเดา filename)
    //  เช่น "Sky Sports Football UK" → "sky_sports_football_uk"
    // ============================================================
    private fun titleToLogoSlug(title: String): String {
        return title.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .trim('_')
    }
}
