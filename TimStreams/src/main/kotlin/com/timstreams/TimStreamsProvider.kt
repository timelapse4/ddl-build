package com.timstreams

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.regex.Pattern

/**
 * tim-streams.com provider — built on /api/channels.
 *
 * /api/channels returns ONE JSON object:
 *   {"channels":[{url,name,logo,genre,flag?,vip,viewers,streams:[{name,url,vip}]}, ...],
 *    "genres":[{"id":1,"name":"Entertainment"},{"id":2,"name":"Sports"}, ...]}
 *
 * Channels are grouped on the home page by "genre" (names come from "genres").
 *
 * Embed page decode (XOR-obfuscated JS -> m3u8) is unchanged from the original
 * Go reference implementation.
 */
class TimStreamsProvider : MainAPI() {
    override var mainUrl = "https://tim-streams.com"
    override var name = "TimStreams"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true

    private val channelsApi = "$mainUrl/api/channels"

    // ==================== JSON models ====================

    data class StreamOption(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null
    )

    data class ChannelItem(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("genre") val genre: Int? = null,
        @JsonProperty("flag") val flag: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null,
        @JsonProperty("viewers") val viewers: Int? = null,
        @JsonProperty("streams") val streams: List<StreamOption>? = null
    )

    data class GenreItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null
    )

    data class ChannelsResponse(
        @JsonProperty("channels") val channels: List<ChannelItem>? = null,
        @JsonProperty("genres") val genres: List<GenreItem>? = null
    )

    // ==================== Fetch + cache ====================

    private var cache: ChannelsResponse? = null
    private var cacheAt: Long = 0L
    private val cacheTtlMs = 5 * 60 * 1000L

    private suspend fun fetchAll(): ChannelsResponse {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheAt < cacheTtlMs) return it }

        val resp = app.get(
            channelsApi,
            referer = mainUrl,
            headers = mapOf("Accept" to "application/json,*/*")
        )
        val data = parseJson<ChannelsResponse>(resp.text)
        cache = data
        cacheAt = now
        return data
    }

    private fun hasFreeStream(c: ChannelItem): Boolean =
        c.vip != true && c.streams?.any { it.vip != true && !it.url.isNullOrBlank() } == true

    // Logos: some are relative ("/247img/x.png"), some contain "&amp;amp;" junk.
    private fun fixLogo(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(Regex("(&amp;)+"), "&")
        return if (cleaned.startsWith("/")) mainUrl + cleaned else cleaned
    }

    // ==================== Home ====================

    override val mainPage = mainPageOf(
        "home" to "TimStreams"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = fetchAll()
        val free = (data.channels ?: emptyList()).filter { hasFreeStream(it) }
        val genreNames = (data.genres ?: emptyList())
            .mapNotNull { g -> g.id?.let { it to (g.name ?: "Genre $it") } }
            .toMap()

        val byGenre = free.groupBy { it.genre }
        // Sports (id 2) first, then the rest by id, unknown genres last.
        val order = byGenre.keys.sortedWith(
            compareBy<Int?> { if (it == 2) 0 else 1 }.thenBy { it ?: Int.MAX_VALUE }
        )

        val lists = mutableListOf<HomePageList>()
        for (g in order) {
            val title = genreNames[g] ?: "Other"
            val cards = byGenre[g].orEmpty().mapNotNull { toSearchResponse(it) }
            if (cards.isNotEmpty()) lists.add(HomePageList(title, cards))
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    private fun toSearchResponse(c: ChannelItem): SearchResponse? {
        val slug = c.url ?: return null
        return newLiveSearchResponse(c.name ?: slug, slug, TvType.Live) {
            this.posterUrl = fixLogo(c.logo)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return (fetchAll().channels ?: emptyList())
            .filter { hasFreeStream(it) && it.name?.contains(query, ignoreCase = true) == true }
            .mapNotNull { toSearchResponse(it) }
    }

    // CloudStream's fixUrl() auto-prepends mainUrl to any non-absolute url passed
    // to newLiveSearchResponse/newLiveStreamLoadResponse, so load()/loadLinks()
    // receive "https://tim-streams.com/<slug>" even though we only ever stored the
    // bare slug. Strip that back off before comparing against channel.url.
    private fun toSlug(rawUrl: String): String {
        return rawUrl
            .removePrefix("$mainUrl/")
            .removePrefix(mainUrl)
            .removePrefix("/")
    }

    // ==================== Load ====================

    override suspend fun load(url: String): LoadResponse {
        val slug = toSlug(url)
        val item = (fetchAll().channels ?: emptyList()).find { it.url == slug }
            ?: throw ErrorLoadingException("ไม่พบช่อง/แมทช์: $slug")

        return newLiveStreamLoadResponse(item.name ?: slug, slug, slug) {
            this.posterUrl = fixLogo(item.logo)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val slug = toSlug(data)
        val item = (fetchAll().channels ?: emptyList()).find { it.url == slug } ?: return false
        val freeStream = item.streams?.firstOrNull { it.vip != true && !it.url.isNullOrBlank() }
            ?: return false
        val embedUrl = freeStream.url ?: return false

        // Embed host changed (now e.g. ehgembxe.cfd), so derive the referer from it.
        val embedReferer = Regex("^https?://[^/]+").find(embedUrl)?.value?.plus("/") ?: "$mainUrl/"

        val html = app.get(embedUrl, referer = mainUrl).text
        val m3u8 = decodeAndExtractM3u8(html) ?: extractM3u8Fallback(html) ?: return false

        callback(
            newExtractorLink(
                this.name,
                item.name ?: this.name,
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

    // ==================== XOR de-obfuscation (unchanged, ported from Go proxy) ====================

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

    // ==================== Fallbacks when the XOR pattern is not found ====================

    private fun findM3u8(text: String): String? {
        val cleaned = text
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
        val m = m3u8Pattern.matcher(cleaned)
        return if (m.find()) m.group().trimEnd('"', ',', '\'', ';', ')') else null
    }

    private fun extractM3u8Fallback(html: String): String? {
        // 1) plain m3u8 URL anywhere in the page (also handles JSON-escaped slashes)
        findM3u8(html)?.let { return it }

        // 2) base64 strings passed to atob("...")
        val atob = Pattern.compile("""atob\(\s*["']([A-Za-z0-9+/=]{16,})["']\s*\)""").matcher(html)
        while (atob.find()) {
            val decoded = try {
                String(android.util.Base64.decode(atob.group(1), android.util.Base64.DEFAULT))
            } catch (e: Exception) { continue }
            findM3u8(decoded)?.let { return it }
        }
        return null
    }
}
