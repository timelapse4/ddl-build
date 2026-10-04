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
        private val STREAM_FOLDERS = listOf("stream", "cast", "watch", "plus", "casting", "player")
        private val FALLBACK_DOMAINS = listOf(
            "https://dlive.sx",
            "https://dlhd.sx",
            "https://daddylivehd.sx"
        )
    }

    private val siteHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    //... categoryMap และ logoMap เดิมของคุณใช้ได้เลย ให้คงไว้...

    override val mainPage = mainPageOf(CHANNEL_PAGE to "All Channels")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var doc: org.jsoup.nodes.Document? = null
        for(domain in FALLBACK_DOMAINS) {
            try {
                mainUrl = domain
                doc = app.get("$mainUrl$CHANNEL_PAGE", headers = siteHeaders).document
                if(doc.select("a").size > 50) break
            } catch(e: Exception) { continue }
        }
        val links = doc!!.select("a[href*='id='], a[href*='stream-']")
        //... โค้ด grouped เดิมของคุณ...
        val grouped = mutableMapOf<String, MutableList<SearchResponse>>()
        // ใส่ logic เดิมต่อได้เลย
        return newHomePageResponse(emptyList())
    }

    // ===== จุดที่แก้สำคัญ: loadLinks =====
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = extractIdFromUrl(data)?: return false
        var found = false

        for (folder in STREAM_FOLDERS) {
            val pageUrl = "$mainUrl/$folder/stream-$id.php"
            try {
                val html = app.get(pageUrl, headers = siteHeaders).text
                if(html.length < 500) continue

                // 1. หา m3u8 ตรงๆ
                findM3u8(html)?.let { m3u8 ->
                    callback(newExtractorLink(name, "$name [$folder]", m3u8, ExtractorLinkType.M3U8) {
                        this.referer = pageUrl
                        this.quality = Qualities.Unknown.value
                        this.headers = mapOf("Referer" to pageUrl, "User-Agent" to siteHeaders["User-Agent"]!!)
                    })
                    found = true
                    break
                }

                // 2. ตาม iframe 1-2 ชั้น
                val iframes = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""").findAll(html).map { it.groupValues[1] }.toList()
                for (src in iframes) {
                    val iframeUrl = if(src.startsWith("http")) src else "https://${
                        Regex("""https?://([^/]+)""").find(src)?.groupValues?.get(1)?: "dlive.sx"
                    }$src"
                    // ต้องไล่แบบจริงๆ
                    val fullIframeUrl = if(src.startsWith("http")) src else "$mainUrl/${src.trimStart('/')}"
                    val iHtml = app.get(fullIframeUrl, headers = siteHeaders + mapOf("Referer" to pageUrl)).text

                    // แกะ atob แบบใหม่ - ตัวใหม่เข้ารหัส 2 รอบ
                    val decoded = extractAndDecode(iHtml)
                    decoded?.let { m3u8 ->
                        if(m3u8.contains(".m3u8")) {
                            callback(newExtractorLink(name, "$name [$folder-embed]", m3u8, ExtractorLinkType.M3U8) {
                                this.referer = fullIframeUrl // สำคัญมาก ต้องใช้ embed url เป็น referer
                                this.headers = mapOf("Referer" to fullIframeUrl, "User-Agent" to siteHeaders["User-Agent"]!!)
                            })
                            found = true
                            break
                        }
                    }
                    findM3u8(iHtml)?.let { m3u8 ->
                        callback(newExtractorLink(name, "$name [$folder]", m3u8, ExtractorLinkType.M3U8) {
                            this.referer = fullIframeUrl
                            this.headers = mapOf("Referer" to fullIframeUrl)
                        })
                        found = true
                        break
                    }
                }
                if(found) break
            } catch(e: Exception) { }
        }
        return found
    }

    private fun extractAndDecode(html: String): String? {
        // หา atob ทั้งหมด
        val atobRegex = Regex("""atob\s*\(\s*['"]([^'"]+)['"]\s*\)""")
        for(match in atobRegex.findAll(html)) {
            try {
                var decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
                // บางที base64 ซ้อน 2 ชั้น
                if(decoded.contains("atob") ||!decoded.contains("http")) {
                    val inner = Regex("""['"]([^'"]+\.m3u8[^'"]*)['"]""").find(decoded)
                    if(inner!= null) return inner.groupValues[1]
                    try {
                        if(decoded.length % 4 == 0) {
                            decoded = String(Base64.decode(decoded, Base64.DEFAULT))
                        }
                    } catch(_: Exception) {}
                }
                if(decoded.contains(".m3u8") && decoded.contains("http")) {
                    return Regex("""(https?://[^\s'"<>]+\.m3u8[^\s'"<>]*)""").find(decoded)?.groupValues?.get(1)?: decoded.trim()
                }
            } catch(_: Exception) {}
        }
        return null
    }

    private fun findM3u8(html: String): String? {
        val patterns = listOf(
            """['"]?(?:file|source|src|hls|player)["']?\s*[:=]\s*['"]([^'"]+\.m3u8[^'"]*)['"]""",
            """hls\.loadSource\(['"]([^'"]+)['"]\)""",
            """(https?://[^\s'"<>]+\.m3u8[^\s'"<>]+)"""
        )
        for(p in patterns) {
            Regex(p, RegexOption.IGNORE_CASE).find(html)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun extractIdFromUrl(url: String): String? {
        Regex("""stream-(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""id=(\d+)""").find(url)?.let { return it.groupValues[1] }
        Regex("""/(\d+)$""").find(url)?.let { return it.groupValues[1] }
        return null
    }
}
