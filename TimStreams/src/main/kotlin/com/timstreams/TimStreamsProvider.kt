package com.timstreams

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.regex.Pattern

/**
 * timstreams.st provider.
 *
 * ที่มาของตรรกะทั้งหมด (moved from a working Go reference implementation):
 *   - รายชื่อช่อง:  GET https://timstreams.st/api/channels
 *   - channel.streams[].url รูปแบบ: https://epiembeds.online/embed/<slug>
 *   - หน้า embed มี JS array ที่ XOR-obfuscate ไว้ ต้อง decode เพื่อดึง .m3u8 ออกมา
 *
 * หมายเหตุสำคัญ: ตัว decode และ segment (.ts) ทั้งหมดยังต้องแนบ Referer/Origin
 * เป็น https://epiembeds.online/ เสมอ ไม่งั้น CDN จะปฏิเสธ request
 */
class TimStreamsProvider : MainAPI() {
    override var mainUrl = "https://timstreams.st"
    override var name = "TimStreams"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true

    private val channelsApi = "$mainUrl/api/channels"
    private val embedReferer = "https://epiembeds.online/"

    // ==================== JSON models ====================

    data class Stream(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null
    )

    data class Channel(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("genre") val genre: Int? = null,
        @JsonProperty("flag") val flag: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null,
        @JsonProperty("viewers") val viewers: Int? = null,
        @JsonProperty("streams") val streams: List<Stream>? = null
    )

    data class ChannelListResponse(
        @JsonProperty("channels") val channels: List<Channel>? = null
    )

    // เก็บ cache ช่องไว้กันยิง API ซ้ำถี่เกินไป (คล้าย chanCache ใน Go เดิม)
    private var channelCache: List<Channel>? = null
    private var channelCacheAt: Long = 0L
    private val channelCacheTtlMs = 10 * 60 * 1000L // 10 นาที เหมือนต้นฉบับ

    private suspend fun fetchChannels(): List<Channel> {
        val now = System.currentTimeMillis()
        channelCache?.let {
            if (now - channelCacheAt < channelCacheTtlMs) return it
        }
        val resp = app.get(
            channelsApi,
            referer = mainUrl,
            headers = mapOf("Accept" to "application/json,*/*")
        )
        val parsed = parseJson<ChannelListResponse>(resp.text)
        val list = parsed.channels ?: emptyList()
        channelCache = list
        channelCacheAt = now
        return list
    }

    private fun hasFreeStream(c: Channel): Boolean =
        c.vip != true && c.streams?.any { it.vip != true && !it.url.isNullOrBlank() } == true

    // ==================== Home / Search ====================

    override val mainPage = mainPageOf(
        "home" to "TimStreams"
    )

    // The channels API only exposes "flag" (country) and a numeric "genre" code —
    // there's no league name (e.g. "Premier League") in this JSON, so we group by
    // country flag as the closest available category. Real league grouping would
    // need whatever endpoint the site itself uses to build those sections, which
    // isn't reachable here.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels().filter { hasFreeStream(it) }

        val grouped = channels.groupBy { c ->
            c.flag?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "OTHER"
        }

        val lists = grouped.entries
            .sortedBy { it.key }
            .mapNotNull { (flag, chansInGroup) ->
                val items = chansInGroup.mapNotNull { c ->
                    val slug = c.url ?: return@mapNotNull null
                    newLiveSearchResponse(c.name ?: slug, slug, TvType.Live) {
                        this.posterUrl = c.logo
                    }
                }
                if (items.isEmpty()) null else HomePageList(flag, items)
            }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channels = fetchChannels().filter { hasFreeStream(it) }
        return channels.filter {
            it.name?.contains(query, ignoreCase = true) == true
        }.mapNotNull { c ->
            val slug = c.url ?: return@mapNotNull null
            newLiveSearchResponse(c.name ?: slug, slug, TvType.Live) {
                this.posterUrl = c.logo
            }
        }
    }

    // CloudStream's fixUrl() auto-prepends mainUrl to any non-absolute url passed
    // to newLiveSearchResponse/newLiveStreamLoadResponse. That means load()/loadLinks()
    // receive "https://timstreams.st/<slug>" even though we only ever stored the bare
    // slug from the API. Strip that back off before comparing against channel.url.
    private fun toSlug(rawUrl: String): String {
        return rawUrl
            .removePrefix("$mainUrl/")
            .removePrefix(mainUrl)
            .removePrefix("/")
    }

    // ==================== Load ====================

    override suspend fun load(url: String): LoadResponse {
        val slug = toSlug(url)
        val channel = fetchChannels().find { it.url == slug }
            ?: throw ErrorLoadingException("ไม่พบช่อง: $slug")

        return newLiveStreamLoadResponse(channel.name ?: slug, slug, slug) {
            this.posterUrl = channel.logo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val slug = toSlug(data)
        val channel = fetchChannels().find { it.url == slug } ?: return false
        val freeStream = channel.streams?.firstOrNull { it.vip != true && !it.url.isNullOrBlank() }
            ?: return false
        val embedUrl = freeStream.url ?: return false

        val html = app.get(embedUrl, referer = mainUrl).text
        val m3u8 = decodeAndExtractM3u8(html) ?: return false

        callback(
            newExtractorLink(
                this.name,
                channel.name ?: this.name,
                m3u8
            ) {
                this.referer = embedReferer
                this.quality = Qualities.Unknown.value
                this.headers = mapOf(
                    "Referer" to embedReferer,
                    "Origin" to embedReferer.trimEnd('/')
                )
            }
        )
        return true
    }

    // ==================== XOR de-obfuscation (ported 1:1 from the Go proxy) ====================
    //
    // Go เดิม:
    //   var NAME = [n, n, n, ...]
    //   String.fromCharCode(((arr[i] ^ xor) - sub + 256) % 256)
    // เราหา 3 อย่าง: อาร์เรย์ตัวเลข, ตัวแปร xor, ตัวแปร sub แล้ว decode เป็น string,
    // จากนั้น regex หา .m3u8 URL ในสตริงที่ decode ได้

    private val arrPattern = Pattern.compile("""var\s+([A-Za-z_$][\w$]*)\s*=\s*\[([\d,\s]+)]""")
    private val loopPattern = Pattern.compile(
        """String\.fromCharCode\(\(\([\w\[\]]+\s*\^\s*(\w+)\)\s*-\s*(\w+)\s*\+\s*256\)\s*(?:%|&)\s*(?:256|255)\)"""
    )
    private val m3u8Pattern = Pattern.compile("""https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""")

    private fun decodeAndExtractM3u8(html: String): String? {
        val arrMatcher = arrPattern.matcher(html)
        if (!arrMatcher.find()) return null
        val nums = arrMatcher.group(2)
            .replace(" ", "")
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
        if (nums.isEmpty()) return null

        val loopMatcher = loopPattern.matcher(html)
        if (!loopMatcher.find()) return null
        val xorVar = loopMatcher.group(1)
        val subVar = loopMatcher.group(2)

        val xorMatcher = Pattern.compile("""${Pattern.quote(xorVar)}\s*=\s*(\d+)""").matcher(html)
        val subMatcher = Pattern.compile("""${Pattern.quote(subVar)}\s*=\s*(\d+)""").matcher(html)
        if (!xorMatcher.find() || !subMatcher.find()) return null
        val xor = xorMatcher.group(1).toInt()
        val sub = subMatcher.group(1).toInt()

        val sb = StringBuilder(nums.size)
        for (v in nums) {
            val ch = ((v xor xor) - sub + 256) % 256
            sb.append(ch.toChar())
        }
        val decoded = sb.toString()

        val m3u8Matcher = m3u8Pattern.matcher(decoded)
        return if (m3u8Matcher.find()) {
            m3u8Matcher.group().trimEnd('"', ',', '\'', ';', ')')
        } else null
    }
}
