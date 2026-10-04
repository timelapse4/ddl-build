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

    companion object {
        private const val CHANNEL_PAGE = "/24-7-channels.php"
    }

    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://dlive.sx/",
        "Accept" to "text/html,application/xhtml+xml"
    )

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


    private val logoMap = mapOf(
        // ตัวอย่างโลโก้หลักๆ - ใส่เพิ่มจากไฟล์เดิมได้
        "51" to "abc_usa.png",
        "302" to "a_e_usa.png",
        "303" to "amc_usa.png",
        "304" to "animal_planet.png",
        "356" to "bbc_one_uk.png",
        "350" to "itv_1_uk.png",
        "354" to "channel_4_uk.png",
        "355" to "channel_5_uk.png",
        "53" to "nbc_usa.png",
        "52" to "cbs_usa.png",
        "54" to "fox_usa.png",
        "345" to "cnn_usa.png",
        "321" to "hbo_usa.png"
    )

    private fun getLogoUrl(id: String): String? {
        val filename = logoMap[id] ?: return null
        return "https://dlhd.pk/logos/" + filename
    }

    override val mainPage = mainPageOf(
        CHANNEL_PAGE to "All Channels"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val html = app.get("${mainUrl}${request.data}", headers = siteHeaders).text
        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        val regex = Regex("""([^<>\n]{3,}?)\s+ID:\s*(\d+)""")
        for (m in regex.findAll(html)) {
            val rawTitle = m.groupValues[1].trim()
            val title = rawTitle.replace(Regex("<.*?>"), "").trim()
            val id = m.groupValues[2].trim()
            if (title.length < 2) continue

            val searchItem = newLiveSearchResponse(
                name = title,
                url = "${mainUrl}/stream/stream-${id}.php",
                type = TvType.Live
            ) {
                this.posterUrl = getLogoUrl(id)
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

        val homeLists = grouped.filter { it.value.isNotEmpty() }.map { (cat, items) ->
            HomePageList(
                name = cat,
                list = items.distinctBy { it.url },
                isHorizontalImages = false
            )
        }
        return newHomePageResponse(homeLists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val html = app.get("${mainUrl}/24-7-channels.php", headers = siteHeaders).text
        val regex = Regex("""([^<>\n]{3,}?)\s+ID:\s*(\d+)""")
        return regex.findAll(html).mapNotNull { m ->
            val title = m.groupValues[1].trim()
            if (!title.contains(query, ignoreCase = true)) return@mapNotNull null
            val id = m.groupValues[2]
            newLiveSearchResponse(title, "${mainUrl}/stream/stream-${id}.php", TvType.Live) {
                this.posterUrl = getLogoUrl(id)
            }
        }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("""stream-(\d+)""").find(url)?.groupValues?.get(1) ?: "0"
        val title = "Channel $id"
        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = getLogoUrl(id)
        }
    }

    private fun extractM3u8(html: String): String? {
        Regex("""atob\(['"]([^'"]+)['"]\)""").findAll(html).forEach { m ->
            try {
                var d = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (!d.contains("http")) {
                    try { d = String(Base64.decode(d.trim(), Base64.DEFAULT)) } catch (_:Exception) {}
                }
                Regex("""https?://[^\s'"<>]+\.m3u8[^\s'"<>]+""").find(d)?.let { return it.value }
            } catch (_:Exception) {}
        }
        return Regex("""https?://[^\s'"<>]+\.m3u8[^\s'"<>]+""").find(html)?.value
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = Regex("""(\d+)""").find(data)?.value ?: return false
        val folders = listOf("stream", "cast", "watch", "plus", "casting", "player")
        for (folder in folders) {
            val pageUrl = "${mainUrl}/$folder/stream-${id}.php"
            try {
                val html1 = app.get(pageUrl, headers = siteHeaders).text
                if (html1.length < 500) continue
                extractM3u8(html1)?.let { m3u8 ->
                    callback.invoke(newExtractorLink(name, "$name [$folder]", m3u8, ExtractorLinkType.M3U8) {
                        this.referer = pageUrl
                        this.quality = Qualities.Unknown.value
                    })
                    return true
                }
                val iframes = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""").findAll(html1).map { it.groupValues[1] }.toList()
                for (src in iframes) {
                    val iframeUrl = if (src.startsWith("http")) src else "${mainUrl}/${src.trimStart('/')}"
                    val html2 = app.get(iframeUrl, headers = siteHeaders + mapOf("Referer" to pageUrl)).text
                    extractM3u8(html2)?.let { m3u8 ->
                        callback.invoke(newExtractorLink(name, "$name [$folder-iframe]", m3u8, ExtractorLinkType.M3U8) {
                            this.referer = iframeUrl
                            this.quality = Qualities.Unknown.value
                        })
                        return true
                    }
                    val innerSrc = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""").find(html2)?.groupValues?.get(1)
                    if (innerSrc != null) {
                        val innerUrl = if (innerSrc.startsWith("http")) innerSrc else "${mainUrl}/${innerSrc.trimStart('/')}"
                        val html3 = app.get(innerUrl, headers = siteHeaders + mapOf("Referer" to iframeUrl)).text
                        extractM3u8(html3)?.let { m3u8 ->
                            callback.invoke(newExtractorLink(name, "$name [$folder-nested]", m3u8, ExtractorLinkType.M3U8) {
                                this.referer = innerUrl
                                this.quality = Qualities.Unknown.value
                            })
                            return true
                        }
                    }
                }
            } catch (_:Exception) {}
        }
        return false
    }
}
