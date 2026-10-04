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
        private val STREAM_FOLDERS = listOf("stream","cast","watch","plus","casting","player")
        private val FALLBACK = listOf("https://dlive.sx","https://dlhd.sx","https://daddylivehd.sx")
    }

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "https://dlive.sx/",
        "Accept" to "text/html,application/xhtml+xml"
    )

    // categoryMap + logoMap เดิมของคุณให้คงไว้ทั้งหมดนะ ผมตัดออกให้สั้น
    private val categoryMap = linkedMapOf(
        "Sports UK" to listOf("【entity-TNT Sports¦canonical_name=TNT Sports】","【entity-Sky Sports¦canonical_name=Sky Sports】"),
        "Sports USA" to listOf("【entity-ESPN¦canonical_name=ESPN】","【entity-Fox Sports¦canonical_name=Fox Sports】","【entity-CBS¦canonical_name=CBS】","【entity-NBC¦canonical_name=NBC】","【entity-NFL¦canonical_name=NFL】","【entity-NBA¦canonical_name=NBA】","【entity-MLB¦canonical_name=MLB】"),
        "Sports 【entity-beIN¦canonical_name=beIN】" to listOf("【entity-beIN¦canonical_name=beIN】"),
        "TV USA" to listOf("ABC USA","【entity-CBS¦canonical_name=CBS】 USA","【entity-NBC¦canonical_name=NBC】 USA","FOX USA","【entity-CNN¦canonical_name=CNN】","【entity-HBO¦canonical_name=HBO】"),
        "Other Channels" to emptyList<String>()
    )

    override val mainPage = mainPageOf(CHANNEL_PAGE to "All Channels")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var html = ""
        for(domain in FALLBACK){
            try{
                mainUrl = domain
                html = app.get("$mainUrl$CHANNEL_PAGE", headers=headers, timeout=30).text
                if(html.contains("ID:")) break
            }catch(_:Exception){}
        }

        // parse ด้วย Regex เพราะหน้านี้ไม่มี tag สวยๆ บางทีเป็นแค่ text list
        // รูปแบบ: >ABC USA ID: 51< หรือ stream-51.php
        val regex = Regex("""([^<>]+?)\s+ID:\s*(\d+)""")
        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        categoryMap.keys.forEach { grouped[it] = mutableListOf() }

        for(m in regex.findAll(html)){
            val title = m.groupValues[1].trim().replace("""<.*?>""".toRegex(),"").trim()
            val id = m.groupValues[2].trim()
            if(title.length < 2) continue

            val item = newLiveSearchResponse(title, "$mainUrl/stream/stream-$id.php", TvType.Live){
                this.posterUrl = null // ใส่ logoMap ของคุณได้
            }
            var placed = false
            for((cat, keys) in categoryMap){
                if(cat=="Other Channels") continue
                if(keys.any{ title.contains(it, true)}){
                    grouped[cat]?.add(item); placed=true; break
                }
            }
            if(!placed) grouped["Other Channels"]?.add(item)
        }

        // ถ้ายังไม่ได้ ลอง fallback แบบ a[href]
        if(grouped.values.sumOf{it.size}==0){
            val doc = app.get("$mainUrl$CHANNEL_PAGE", headers=headers).document
            doc.select("a").forEach{ a->
                val href = a.attr("href")
                val id = Regex("""(\d+)""").find(href)?.groupValues?.get(1)?: return@forEach
                val title = a.text().trim()
                if(title.isBlank()) return@forEach
                val item = newLiveSearchResponse(title, "$mainUrl/stream/stream-$id.php", TvType.Live)
                grouped["Other Channels"]?.add(item)
            }
        }

        val lists = grouped.filter{it.value.isNotEmpty()}.map{ (k,v)->
            HomePageList(k, v.distinctBy{it.url}, isHorizontalImages=true)
        }
        return newHomePageResponse(lists)
    }

    override suspend fun search(query: String): List<SearchResponse>{
        return getMainPage(1, MainPageRequest("", CHANNEL_PAGE, "")).items.flatMap{ it.list }
           .filter{ it.name.contains(query, true)}
    }

    override suspend fun load(url: String): LoadResponse{
        val id = Regex("""stream-(\d+)""").find(url)?.groupValues?.get(1)?: "0"
        return newLiveStreamLoadResponse(url.split("/").last(), url, url)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile)->Unit, callback: (ExtractorLink)->Unit): Boolean{
        val id = Regex("""(\d+)""").find(data)?.groupValues?.get(1)?: return false
        for(folder in STREAM_FOLDERS){
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try{
                val html = app.get(pageUrl, headers=headers+mapOf("Referer" to "$mainUrl/")).text
                if(html.length < 1000) continue

                val m3u8 = findM3u8(html)?: extractAtob(html)?: continue
                callback(newExtractorLink(name, "$name [$folder]", m3u8, ExtractorLinkType.M3U8){
                    this.referer = pageUrl
                    this.headers = mapOf("Referer" to pageUrl, "User-Agent" to headers["User-Agent"]!!)
                })
                return true
            }catch(_:Exception){}
        }
        return false
    }

    private fun extractAtob(html: String): String?{
        Regex("""atob\(['"]([^'"]+)['"]\)""").findAll(html).forEach{ m->
            try{
                var d = String(Base64.decode(m.groupValues[1], Base64.DEFAULT))
                // decode ซ้อน
                if(!d.contains("http") && d.length>20){
                    try{ d = String(Base64.decode(d.trim(), Base64.DEFAULT)) }catch(_:Exception){}
                }
                Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]+)""").find(d)?.let{ return it.groupValues[1] }
            }catch(_:Exception){}
        }
        return null
    }
    private fun findM3u8(h: String): String?{
        return Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]+)""").find(h)?.groupValues?.get(1)
    }
}
