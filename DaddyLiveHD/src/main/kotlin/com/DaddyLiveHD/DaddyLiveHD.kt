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

    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://dlive.sx/"
    )

    // รายชื่อช่องแบบฝัง ไม่ต้องโหลดจากเว็บ
    private val channelNames = mapOf(
            "31" to "TNT Sports 1 UK"
            "32" to "TNT Sports 2 UK"
            "33" to "TNT Sports 3 UK"
            "34" to "TNT Sports 4 UK"
            "35" to "Sky Sports Football UK"
            "36" to "Sky Sports+ Plus"
            "37" to "Sky Sports Action UK"
            "38" to "Sky Sports Main Event"
            "46" to "Sky Sports Tennis UK"
            "130" to "Sky Sports Premier League"
            "60" to "Sky Sports F1 UK"
            "65" to "Sky Sports Cricket"
            "70" to "Sky Sports Golf UK"
            "366" to "Sky Sports News UK"
            "449" to "Sky Sports MIX UK"
            "554" to "Sky Sports Racing UK"
            "276" to "LaLigaTV UK"
            "230" to "DAZN 1 UK"
            "451" to "Viaplay Sports 1 UK"
            "550" to "Viaplay Sports 2 UK"
            "377" to "MUTV UK"
            "826" to "Liverpool TV (LFC TV)"
            "771" to "Premier Sports Ireland 1"
            "799" to "Premier Sports Ireland 2"
            "44" to "ESPN USA"
            "45" to "ESPN2 USA"
            "316" to "ESPNU USA"
            "288" to "ESPNews"
            "375" to "ESPN Deportes"
            "39" to "Fox Sports 1 USA"
            "758" to "Fox Sports 2 USA"
            "756" to "FOX Soccer Plus"
            "308" to "CBS Sports Network"
            "910" to "CBS Sports Golazo"
            "405" to "NFL Network"
            "667" to "NFL RedZone"
            "404" to "NBA TV USA"
            "399" to "MLB Network USA"
            "663" to "NHL Network USA"
            "318" to "GOLF Channel USA"
            "40" to "Tennis Channel"
            "425" to "BeIN SPORTS USA"
            "385" to "SEC Network USA"
            "664" to "ACC Network USA"
            "397" to "BIG TEN Network"
            "66" to "TUDN USA"
            "376" to "WWE Network"
            "757" to "Fight Network"
            "43" to "PDC TV"
            "765" to "MSG USA"
            "759" to "SportsNet New York (SNY)"
            "762" to "NESN USA"
            "763" to "YES Network USA"
            "770" to "Marquee Sports Network"
            "776" to "Chicago Sports Network"
            "777" to "NBC Sports Philadelphia"
            "921" to "Space City Home Network"
            "920" to "Root Sports Northwest"
            "982" to "Spectrum SportsNet USA"
            "764" to "Spectrum Sportsnet LA"
            "890" to "FanDuel Sports AZ"
            "891" to "FanDuel Sports Detroit"
            "892" to "FanDuel Sports Florida"
            "893" to "FanDuel Sports Great Lakes"
            "894" to "FanDuel Sports Indiana"
            "895" to "FanDuel Sports Kansas City"
            "896" to "FanDuel Sports Midwest"
            "897" to "FanDuel Sports New Orleans"
            "898" to "FanDuel Sports North"
            "899" to "FanDuel Sports Ohio"
            "900" to "FanDuel Sports Oklahoma"
            "902" to "FanDuel Sports SoCal"
            "903" to "FanDuel Sports South"
            "904" to "FanDuel Sports Southeast"
            "905" to "FanDuel Sports Sun"
            "906" to "FanDuel Sports West"
            "907" to "FanDuel Sports Wisconsin"
            "61" to "beIN Sports MENA English 1"
            "90" to "beIN Sports MENA English 2"
            "91" to "beIN Sports 1 Arabic"
            "92" to "beIN Sports 2 Arabic"
            "93" to "beIN Sports 3 Arabic"
            "94" to "beIN Sports 4 Arabic"
            "95" to "beIN Sports 5 Arabic"
            "96" to "beIN Sports 6 Arabic"
            "97" to "beIN Sports 7 Arabic"
            "98" to "beIN Sports 8 Arabic"
            "99" to "beIN Sports 9 Arabic"
            "100" to "beIN SPORTS XTRA 1"
            "494" to "beIN Sports MAX 4 France"
            "495" to "beIN Sports MAX 5 France"
            "496" to "beIN Sports MAX 6 France"
            "497" to "beIN Sports MAX 7 France"
            "498" to "beIN Sports MAX 8 France"
            "499" to "beIN Sports MAX 9 France"
            "500" to "beIN Sports MAX 10 France"
            "116" to "beIN SPORTS 1 France"
            "117" to "beIN SPORTS 2 France"
            "118" to "beIN SPORTS 3 France"
            "62" to "beIN SPORTS 1 Turkey"
            "63" to "beIN SPORTS 2 Turkey"
            "64" to "beIN SPORTS 3 Turkey"
            "67" to "beIN SPORTS 4 Turkey"
            "1010" to "bein Sports 5 Turkey"
            "578" to "BeIN Sports HD Qatar"
            "372" to "beIN SPORTS en Espanol"
            "491" to "beIN SPORTS Australia 1"
            "492" to "beIN SPORTS Australia 2"
            "493" to "beIN SPORTS Australia 3"
            "712" to "beIN Sports 1 Malaysia"
            "713" to "beIN Sports 2 Malaysia"
            "714" to "beIN Sports 3 Malaysia"
            "271" to "Canal+ MotoGP France"
            "273" to "Canal+ Formula 1"
            "121" to "Canal+ France"
            "122" to "Canal+ Sport France"
            "463" to "Canal+ Foot France"
            "464" to "Canal+ Sport360"
            "119" to "RMC Sport 1 France"
            "120" to "RMC Sport 2 France"
            "960" to "DAZN Ligue 1 France"
            "772" to "Eurosport 1 France"
            "773" to "Eurosport 2 France"
            "84" to "Movistar Laliga"
            "435" to "Movistar Liga de Campeones"
            "436" to "Movistar Deportes Spain"
            "438" to "Movistar Deportes 2 Spain"
            "526" to "Movistar Deportes 3 Spain"
            "527" to "Movistar Deportes 4 Spain"
            "445" to "DAZN 1 Spain"
            "446" to "DAZN 2 Spain"
            "447" to "DAZN 3 Spain"
            "448" to "DAZN 4 Spain"
            "537" to "DAZN F1 ES"
            "538" to "DAZN LaLiga"
            "524" to "EuroSport 1 Spain"
            "525" to "EuroSport 2 Spain"
            "530" to "GOL PLAY Spain"
            "529" to "Teledeporte Spain"
            "523" to "Real Madrid TV Spain"
            "522" to "Barca TV Spain"
            "240" to "Sky Sports 1 DE"
            "241" to "Sky Sports 2 DE"
            "558" to "Sky Sport Bundesliga 1 HD"
            "946" to "Sky Sport Bundesliga 2"
            "947" to "Sky Sport Bundesliga 3"
            "948" to "Sky Sport Bundesliga 4"
            "949" to "Sky Sport Bundesliga 5"
            "274" to "Sky Sports F1 DE"
            "426" to "DAZN 1 Bar DE"
            "427" to "DAZN 2 Bar DE"
            "641" to "Sport 1 Germany"
            "571" to "SportDigital Fussball"
            "460" to "Sky Sport MAX Italy"
            "461" to "Sky Sport UNO Italy"
            "462" to "Sky Sport Arena Italy"
            "869" to "Sky Sport 24 Italy"
            "870" to "Sky Sport Calcio Italy"
            "871" to "Sky Calcio 1 Italy"
            "872" to "Sky Calcio 2 Italy"
            "873" to "Sky Calcio 3 Italy"
            "874" to "Sky Calcio 4 Italy"
            "875" to "Sky Sport Basket Italy"
            "577" to "Sky Sport F1 Italy"
            "575" to "Sky Sport MotoGP Italy"
            "878" to "EuroSport 1 Italy"
            "879" to "EuroSport 2 Italy"
            "877" to "DAZN ZONA Italy"
            "882" to "Rai Sport Italy"
            "48" to "Canal+ Sport Poland"
            "73" to "Canal+ Sport 2 Poland"
            "259" to "Canal+ Sport 3 Poland"
            "983" to "Canal+ Extra 1-7 Poland"
            "47" to "Polsat Sport Poland"
            "50" to "Polsat Sport 2 Poland"
            "129" to "Polsat Sport 3 Poland"
            "991" to "Polsat Sport Premium 1"
            "992" to "Polsat Sport Premium 2"
            "993" to "Polsat Sport Extra 1"
            "994" to "Polsat Sport Extra 2"
            "995" to "Polsat Sport Extra 3"
            "996" to "Polsat Sport Extra 4"
            "997" to "Polsat Sport Fight"
            "998" to "Polsat Sport NEWS"
            "71" to "Eleven Sports 1 Poland"
            "72" to "Eleven Sports 2 Poland"
            "428" to "Eleven Sports 3 Poland"
            "999" to "Eleven Sports 4 Poland"
            "57" to "EuroSport 1 Poland"
            "58" to "EuroSport 2 Poland"
            "128" to "TVP Sport Poland"
            "49" to "Sport TV1 Portugal"
            "74" to "Sport TV2 Portugal"
            "454" to "Sport TV3 Portugal"
            "289" to "Sport TV4 Portugal"
            "290" to "Sport TV5 Portugal"
            "291" to "Sport TV6 Portugal"
            "455" to "Eleven Sports 1 Portugal"
            "456" to "Eleven Sports 2 Portugal"
            "457" to "Eleven Sports 3 Portugal"
            "458" to "Eleven Sports 4 Portugal"
            "459" to "Eleven Sports 5 Portugal"
            "380" to "Benfica TV PT"
            "41" to "EuroSport 1 Greece"
            "42" to "EuroSport 2 Greece"
            "631" to "Nova Sports 1 Greece"
            "632" to "Nova Sports 2 Greece"
            "633" to "Nova Sports 3 Greece"
            "634" to "Nova Sports 4 Greece"
            "635" to "Nova Sports 5 Greece"
            "636" to "Nova Sports 6 Greece"
            "599" to "Nova Sports Premier League"
            "622" to "Cosmote Sport 1 HD"
            "623" to "Cosmote Sport 2 HD"
            "624" to "Cosmote Sport 3 HD"
            "625" to "Cosmote Sport 4 HD"
            "626" to "Cosmote Sport 5 HD"
            "627" to "Cosmote Sport 6 HD"
            "628" to "Cosmote Sport 7 HD"
            "629" to "Cosmote Sport 8 HD"
            "630" to "Cosmote Sport 9 HD"
            "400" to "Digi Sport 1 Romania"
            "401" to "Digi Sport 2 Romania"
            "402" to "Digi Sport 3 Romania"
            "403" to "Digi Sport 4 Romania"
            "439" to "Orange Sport 1 Romania"
            "440" to "Orange Sport 2 Romania"
            "441" to "Orange Sport 3 Romania"
            "442" to "Orange Sport 4 Romania"
            "583" to "Prima Sport 1"
            "584" to "Prima Sport 2"
            "585" to "Prima Sport 3"
            "586" to "Prima Sport 4"
            "134" to "Arena Sport 1 Premium"
            "135" to "Arena Sport 2 Premium"
            "429" to "Arena Sport 1 Serbia"
            "430" to "Arena Sport 2 Serbia"
            "431" to "Arena Sport 3 Serbia"
            "581" to "Arena Sport 4 Serbia"
            "940" to "Arena Sport 5-10 Serbia"
            "432" to "Arena Sport 1 Croatia"
            "433" to "Arena Sport 2 Croatia"
            "434" to "Arena Sport 3 Croatia"
            "580" to "Arena Sport 4 Croatia"
            "579" to "Arena Sport 1 BiH"
            "101" to "Sport Klub 1 Croatia"
            "102" to "Sport Klub 2 Croatia"
            "103" to "Sport Klub 3 Croatia"
            "104" to "Sport Klub 4 Croatia"
            "582" to "Nova Sport Serbia"
            "779" to "Max Sport 1 Croatia"
            "780" to "Max Sport 2 Croatia"
            "465" to "Diema Sport Bulgaria"
            "466" to "Diema Sport 2 Bulgaria"
            "467" to "Diema Sport 3 Bulgaria"
            "468" to "Nova Sport Bulgaria"
            "472" to "Max Sport 1 Bulgaria"
            "473" to "Max Sport 2 Bulgaria"
            "474" to "Max Sport 3 Bulgaria"
            "475" to "Max Sport 4 Bulgaria"
            "600" to "Abu Dhabi Sports 1 UAE"
            "601" to "Abu Dhabi Sports 2 UAE"
            "604" to "Dubai Sports 1 UAE"
            "605" to "Dubai Sports 2 UAE"
            "606" to "Dubai Sports 3 UAE"
            "781" to "Alkass One"
            "782" to "Alkass Two"
            "783" to "Alkass Three"
            "784" to "Alkass Four"
            "611" to "OnTime Sports"
            "614" to "SSC Sport 1"
            "615" to "SSC Sport 2"
            "616" to "SSC Sport 3"
            "617" to "SSC Sport 4"
            "618" to "SSC Sport 5"
            "619" to "SSC Sport Extra 1"
            "620" to "SSC Sport Extra 2"
            "621" to "SSC Sport Extra 3"
            "412" to "SuperSport Grandstand"
            "413" to "SuperSport PSL"
            "414" to "SuperSport Premier League"
            "415" to "SuperSport LaLiga"
            "416" to "SuperSport Variety 1"
            "417" to "SuperSport Variety 2"
            "418" to "SuperSport Variety 3"
            "419" to "SuperSport Variety 4"
            "420" to "SuperSport Action"
            "421" to "SuperSport Rugby"
            "422" to "SuperSport Golf"
            "423" to "SuperSport Tennis"
            "424" to "SuperSport Motorsport"
            "56" to "Supersport Football"
            "368" to "SuperSport Cricket"
            "123" to "Astro SuperSport 1"
            "124" to "Astro SuperSport 2"
            "125" to "Astro SuperSport 3"
            "126" to "Astro SuperSport 4"
            "267" to "Star Sports 1 IN"
            "268" to "Star Sports Hindi IN"
            "885" to "SONY TEN 1"
            "886" to "SONY TEN 2"
            "887" to "SONY TEN 3"
            "450" to "PTV Sports"
            "269" to "A Sport PK"
            "741" to "Ten Sports PK"
            "270" to "T Sports BD"
            "346" to "Willow Cricket"
            "598" to "Willow 2 Cricket"
            "369" to "Fox Cricket"
            "111" to "TSN1"
            "112" to "TSN2"
            "113" to "TSN3"
            "114" to "TSN4"
            "115" to "TSN5"
            "406" to "Sportsnet Ontario"
            "407" to "Sportsnet West"
            "408" to "Sportsnet East"
            "409" to "Sportsnet 360"
            "410" to "Sportsnet World"
            "411" to "Sportsnet One"
            "839" to "RDS CA"
            "840" to "RDS 2 CA"
            "833" to "TVA Sports"
            "834" to "TVA Sports 2"
            "78" to "SporTV Brasil"
            "79" to "SporTV2 Brasil"
            "80" to "SporTV3 Brasil"
            "81" to "ESPN Brasil"
            "82" to "ESPN2 Brasil"
            "83" to "ESPN3 Brasil"
            "149" to "ESPN Argentina"
            "150" to "ESPN2 Argentina"
            "787" to "Fox Sports Argentina"
            "388" to "TNT Sports Argentina"
            "746" to "TyC Sports Argentina"
            "925" to "ESPN 1 MX"
            "926" to "ESPN 2 MX"
            "929" to "Fox Sports 1 MX"
            "930" to "Fox Sports 2 MX"
            "933" to "Claro Sports MX"
            "935" to "TUDN MX"
            "588" to "Sky Sport 1 NZ"
            "589" to "Sky Sport 2 NZ"
            "590" to "Sky Sport 3 NZ"
            "591" to "Sky Sport 4 NZ"
            "592" to "Sky Sport 5 NZ"
            "820" to "FOX Sports 502 AU"
            "821" to "FOX Sports 503 AU"
            "822" to "FOX Sports 504 AU"
            "823" to "FOX Sports 505 AU"
            "231" to "Eurosport 1 SW"
            "232" to "Eurosport 2 SW"
            "703" to "TV4 Sport Live 1"
            "704" to "TV4 Sport Live 2"
            "705" to "TV4 Sport Live 3"
            "706" to "TV4 Sport Live 4"
            "707" to "TV4 Sportkanalen"
            "808" to "TV2 Sport X Denmark"
            "809" to "TV3 Sport Denmark"
            "810" to "TV2 Sport Denmark"
            "379" to "ESPN 1 NL"
            "386" to "ESPN 2 NL"
            "888" to "ESPN 3 NL"
            "393" to "Ziggo Sport NL"
            "398" to "Ziggo Sport 2 NL"
            "919" to "Ziggo Sport 3 NL"
            "396" to "Ziggo Sport 4 NL"
            "383" to "Ziggo Sport 5 NL"
            "901" to "Ziggo Sport 6 NL"
            "140" to "Sport 1 Israel"
            "141" to "Sport 2 Israel"
            "142" to "Sport 3 Israel"
            "143" to "Sport 4 Israel"
            "144" to "Sport 5 Israel"
            "145" to "Sport 5 PLUS Israel"
            "146" to "Sport 5 Live Israel"
            "147" to "Sport 5 Star Israel"
            "148" to "Sport 5 Gold Israel"
            "136" to "Match Football 1 Russia"
            "137" to "Match Football 2 Russia"
            "138" to "Match Football 3 Russia"
            "127" to "Match TV Russia"
            "1011" to "A Spor Turkey"
            "889" to "TRT Spor TR"
            "911" to "Cytavision Sports 1 Cyprus"
            "912" to "Cytavision Sports 2 Cyprus"
            "913" to "Cytavision Sports 3 Cyprus"
            "1021" to "Nova Sport 1 CZ"
            "1022" to "Nova Sport 2 CZ"
            "1023" to "Nova Sport 3 CZ"
            "1024" to "Nova Sport 4 CZ"
            "1033" to "CT Sport CZ"
            "1020" to "Canal+ Sport CZ"
            "1030" to "Premier Sport 1 CZ"
            "1031" to "Premier Sport 2 CZ"
            "1052" to "JOJ Spor SK"
            "1063" to "Canal+ Sport SK"
            "356" to "BBC One UK"
            "357" to "BBC Two UK"
            "358" to "BBC Three UK"
            "359" to "BBC Four UK"
            "349" to "BBC News Channel HD"
            "350" to "ITV 1 UK"
            "351" to "ITV 2 UK"
            "352" to "ITV 3 UK"
            "353" to "ITV 4 UK"
            "354" to "Channel 4 UK"
            "355" to "Channel 5 UK"
            "361" to "Sky Witness HD"
            "362" to "Sky Atlantic"
            "363" to "E4 Channel"
            "348" to "Dave"
            "687" to "Gold UK"
            "688" to "Film4 UK"
            "682" to "Sky Showcase UK"
            "683" to "Sky Arts UK"
            "684" to "Sky Comedy UK"
            "685" to "Sky Crime"
            "686" to "Sky History"
            "708" to "Sky MAX UK"
            "364" to "RTE 1"
            "365" to "RTE 2"
            "51" to "ABC USA"
            "52" to "CBS USA"
            "53" to "NBC USA"
            "54" to "FOX USA"
            "345" to "CNN USA"
            "327" to "MSNBC"
            "347" to "Fox News"
            "309" to "CNBC USA"
            "336" to "TBS USA"
            "338" to "TNT USA"
            "303" to "AMC USA"
            "302" to "A&E USA"
            "317" to "FX USA"
            "307" to "Bravo USA"
            "315" to "E! Entertainment"
            "326" to "Lifetime Network"
            "320" to "Hallmark Channel"
            "382" to "HGTV"
            "384" to "The Food Network"
            "337" to "TLC"
            "313" to "Discovery Channel"
            "304" to "Animal Planet"
            "322" to "History USA"
            "328" to "National Geographic"
            "294" to "Science Channel"
            "373" to "SYFY USA"
            "339" to "Cartoon Network"
            "312" to "Disney Channel"
            "330" to "NICK"
            "310" to "Comedy Central"
            "371" to "MTV USA"
            "344" to "VH1 USA"
            "321" to "HBO USA"
            "689" to "HBO2 USA"
            "374" to "Cinemax USA"
            "333" to "Showtime USA"
            "335" to "Starz"
            "970" to "Starz Cinema"
            "975" to "Starz Encore"
            "334" to "Paramount Network"
            "671" to "Sky Cinema Premiere UK"
            "672" to "Sky Cinema Select UK"
            "673" to "Sky Cinema Hits UK"
            "677" to "Sky Cinema Action UK"
            "678" to "Sky Cinema Comedy UK"
            "680" to "Sky Cinema Drama UK"
            "860" to "Sky Cinema Uno Italy"
            "861" to "Sky Cinema Action Italy"
            "862" to "Sky Cinema Comedy Italy"
            "864" to "Sky Cinema Romance Italy"
            "469" to "TF1 France"
            "470" to "M6 France"
            "950" to "France 2"
            "951" to "France 3"
            "952" to "France 4"
            "953" to "France 5"
            "956" to "C8 France"
            "957" to "BFM TV France"
            "958" to "Arte France"
            "850" to "Rai 1 Italy"
            "851" to "Rai 2 Italy"
            "852" to "Rai 3 Italy"
            "854" to "Italia 1 Italy"
            "855" to "La7 Italy"
            "881" to "Sky UNO Italy"
            "533" to "TVE La 1 Spain"
            "532" to "Telecinco Spain"
            "531" to "Antena 3 Spain"
            "727" to "ZDF DE"
            "740" to "RTL DE"
            "729" to "SAT.1 DE"
            "730" to "ProSieben DE"
            "719" to "RTP 1 Portugal"
            "720" to "RTP 2 Portugal"
            "722" to "SIC Portugal"
            "723" to "TVI Portugal"
            "479" to "bTV Bulgaria"
            "480" to "Nova TV Bulgaria"
            "476" to "BNT 1 Bulgaria"
            "602" to "CTV Canada"
            "832" to "CBC CA"
            "836" to "Global CA"
            "1000" to "ATV Turkey"
            "1001" to "Kanal D Turkey"
            "1002" to "Show TV Turkey"
            "801" to "DR1 Denmark"
            "802" to "DR2 Denmark"
            "817" to "TV2 Denmark"
            "390" to "RTL7 Netherland"
            "378" to "Veronica NL"
            "546" to "Channel 9 Israel"
            "549" to "Channel 12 Israel"
            "833" to "TVA Sports"
            "1034" to "Nova HD CZ"
            "1035" to "CT1 HD CZ"
            "1050" to "JOJ SK"
            "760" to "Globo SP"
            "761" to "Globo RIO"
            "934" to "Azteca Uno MX"
            "924" to "Las Estrellas"
            "843" to "Prima TV RO"
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
        "51" to "abc_usa.png",
        "52" to "cbs_usa.png",
        "53" to "nbc_usa.png",
        "54" to "fox_usa.png",
        "302" to "a_e_usa.png",
        "303" to "amc_usa.png",
        "345" to "cnn_usa.png",
        "321" to "hbo_usa.png",
        "356" to "bbc_one_uk.png",
        "350" to "itv_1_uk.png",
        "354" to "channel_4_uk.png"
    )

    private fun getLogoUrl(id: String): String? {
        val f = logoMap[id] ?: return null
        return "https://dlhd.pk/logos/$f"
    }

    override val mainPage = mainPageOf("channels" to "Live")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // ไม่โหลดจากเว็บแล้ว ใช้จาก channelNames เลย
        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for ((id, title) in channelNames) {
            val item = newLiveSearchResponse(
                name = title,
                url = "${mainUrl}/stream/stream-${id}.php",
                type = TvType.Live
            ) {
                this.posterUrl = getLogoUrl(id)
            }
            var placed = false
            for ((cat, keys) in categoryMap) {
                if (cat == "Other Channels") continue
                if (keys.any { k -> title.contains(k, ignoreCase = true) }) {
                    grouped[cat]?.add(item); placed = true; break
                }
            }
            if (!placed) grouped["Other Channels"]?.add(item)
        }

        val lists = grouped.filter { it.value.isNotEmpty() }.map { (cat, items) ->
            HomePageList(cat, items.sortedBy { it.name }, isHorizontalImages = false)
        }
        return newHomePageResponse(lists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return channelNames.filter { it.value.contains(query, true) }.map { (id, title) ->
            newLiveSearchResponse(title, "${mainUrl}/stream/stream-${id}.php", TvType.Live) {
                this.posterUrl = getLogoUrl(id)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("stream-(\\d+)").find(url)?.groupValues?.get(1) ?: "0"
        val title = channelNames[id] ?: "Channel $id"
        return newLiveStreamLoadResponse(name = title, url = url, dataUrl = url) {
            this.posterUrl = getLogoUrl(id)
        }
    }

    private fun extractM3u8(html: String): String? {
        Regex("atob\\(['\"]([^'\"]+)['\"]\\)").findAll(html).forEach { m ->
            try {
                var d = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                if (!d.contains("http")) {
                    try { d = String(Base64.decode(d.trim(), Base64.DEFAULT)) } catch (_:Exception) {}
                }
                Regex("https?://[^\\s'"<>]+\\.m3u8[^\\s'"<>]+").find(d)?.let { return it.value }
            } catch (_:Exception) {}
        }
        return Regex("https?://[^\\s'"<>]+\\.m3u8[^\\s'"<>]+").find(html)?.value
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, sub: (SubtitleFile)->Unit, cb: (ExtractorLink)->Unit): Boolean {
        val id = Regex("(\\d+)").find(data)?.value ?: return false
        for (folder in listOf("stream","cast","watch","plus","casting","player")) {
            val pageUrl = "${mainUrl}/$folder/stream-${id}.php"
            try {
                val html1 = app.get(pageUrl, headers = siteHeaders).text
                extractM3u8(html1)?.let { m3u8 ->
                    cb.invoke(newExtractorLink(name, "$name [$folder]", m3u8, ExtractorLinkType.M3U8) { this.referer = pageUrl })
                    return true
                }
                val iframes = Regex("<iframe[^>]+src=['\"]([^'\"]+)['\"]").findAll(html1).map { it.groupValues[1] }.toList()
                for (src in iframes) {
                    val iframeUrl = if (src.startsWith("http")) src else "${mainUrl}/${src.trimStart('/')}"
                    val html2 = app.get(iframeUrl, headers = siteHeaders + mapOf("Referer" to pageUrl)).text
                    extractM3u8(html2)?.let { m3u8 ->
                        cb.invoke(newExtractorLink(name, "$name", m3u8, ExtractorLinkType.M3U8) { this.referer = iframeUrl })
                        return true
                    }
                }
            } catch (_:Exception) {}
        }
        return false
    }
}
