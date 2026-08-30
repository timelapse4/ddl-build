package com.timstreams

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.regex.Pattern

/**
 * timstreams.st provider — built on /api/streams (NOT /api/channels).
 *
 * /api/streams returns a JSON array of category groups:
 *   [{"category":"Events","events":[ ... ]}, {"category":"Replays","events":[...]},
 *    {"category":"24/7","events":[...]}]
 *
 * Inside "Events", each item's "genre" is the SPORT code:
 *   1 = Soccer (sub_genre distinguishes league: 1=EPL, 3=Bundesliga, 5=Serie A, 6=MLS, etc.)
 *   2 = Motorsport
 *   9 = Baseball
 *   (other codes seen so far map to "Other Sports" until confirmed)
 *
 * Inside "24/7", "genre" means something else entirely (general content type,
 * not sport) so those entries are grouped by country "flag" instead, same as before.
 *
 * Embed page decode (XOR-obfuscated JS -> m3u8) is unchanged from the original
 * Go reference implementation.
 */
class TimStreamsProvider : MainAPI() {
    override var mainUrl = "https://timstreams.st"
    override var name = "TimStreams"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true

    private val streamsApi = "$mainUrl/api/streams"
    private val embedReferer = "https://epiembeds.online/"

    // ==================== JSON models ====================

    data class StreamOption(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null
    )

    data class EventItem(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("genre") val genre: Int? = null,
        @JsonProperty("sub_genre") val subGenre: Int? = null,
        @JsonProperty("flag") val flag: String? = null,
        @JsonProperty("vip") val vip: Boolean? = null,
        @JsonProperty("viewers") val viewers: Int? = null,
        @JsonProperty("streams") val streams: List<StreamOption>? = null
    )

    data class CategoryGroup(
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("events") val events: List<EventItem>? = null
    )

    // A flattened row carrying which top-level category ("Events"/"Replays"/"24/7")
    // each item came from, since that's not stored on EventItem itself.
    data class FlatItem(val category: String, val event: EventItem)

    // ==================== Fetch + cache ====================

    private var cache: List<FlatItem>? = null
    private var cacheAt: Long = 0L
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes — event lists change more often than static channels

    private suspend fun fetchAll(): List<FlatItem> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheAt < cacheTtlMs) return it }

        val resp = app.get(
            streamsApi,
            referer = mainUrl,
            headers = mapOf("Accept" to "application/json,*/*")
        )
        val groups = parseJson<List<CategoryGroup>>(resp.text)
        val flat = groups.flatMap { g ->
            val cat = g.category ?: "Other"
            (g.events ?: emptyList()).map { FlatItem(cat, it) }
        }
        cache = flat
        cacheAt = now
        return flat
    }

    private fun hasFreeStream(e: EventItem): Boolean =
        e.vip != true && e.streams?.any { it.vip != true && !it.url.isNullOrBlank() } == true

    // Sport-code mapping for "Events" category only. Unconfirmed codes fall back
    // to "Other Sports" rather than guessing.
    private fun sportName(genre: Int?): String = when (genre) {
        1 -> "Soccer"
        2 -> "Motorsport"
        9 -> "Baseball"
        else -> "Other Sports"
    }

    // ==================== Home ====================

    override val mainPage = mainPageOf(
        "home" to "TimStreams"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val all = fetchAll().filter { hasFreeStream(it.event) }

        val lists = mutableListOf<HomePageList>()

        // --- Events, grouped by sport (this is the "Sports" categorization) ---
        val events = all.filter { it.category.equals("Events", ignoreCase = true) }
        val bySport = events.groupBy { sportName(it.event.genre) }
        // Fixed order so Soccer/Motorsport/Baseball show up first & consistently,
        // then anything else ("Other Sports") last.
        val sportOrder = listOf("Soccer", "Motorsport", "Baseball", "Other Sports")
        for (sport in sportOrder) {
            val items = bySport[sport] ?: continue
            val cards = items.mapNotNull { toSearchResponse(it.event) }
            if (cards.isNotEmpty()) lists.add(HomePageList(sport, cards))
        }

        // --- 24/7 channels, grouped by country flag (unchanged behavior) ---
        val channels247 = all.filter { it.category.equals("24/7", ignoreCase = true) }
        val byFlag = channels247.groupBy { fi ->
            fi.event.flag?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "OTHER"
        }
        for ((flag, items) in byFlag.entries.sortedBy { it.key }) {
            val cards = items.mapNotNull { toSearchResponse(it.event) }
            if (cards.isNotEmpty()) lists.add(HomePageList(flag, cards))
        }

        // --- Replays, single row ---
        val replays = all.filter { it.category.equals("Replays", ignoreCase = true) }
        val replayCards = replays.mapNotNull { toSearchResponse(it.event) }
        if (replayCards.isNotEmpty()) lists.add(HomePageList("Replays", replayCards))

        return newHomePageResponse(lists, hasNext = false)
    }

    private fun toSearchResponse(e: EventItem): SearchResponse? {
        val slug = e.url ?: return null
        return newLiveSearchResponse(e.name ?: slug, slug, TvType.Live) {
            this.posterUrl = e.logo
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val all = fetchAll().filter { hasFreeStream(it.event) }
        return all.filter {
            it.event.name?.contains(query, ignoreCase = true) == true
        }.mapNotNull { toSearchResponse(it.event) }
    }

    // CloudStream's fixUrl() auto-prepends mainUrl to any non-absolute url passed
    // to newLiveSearchResponse/newLiveStreamLoadResponse, so load()/loadLinks()
    // receive "https://timstreams.st/<slug>" even though we only ever stored the
    // bare slug. Strip that back off before comparing against event.url.
    private fun toSlug(rawUrl: String): String {
        return rawUrl
            .removePrefix("$mainUrl/")
            .removePrefix(mainUrl)
            .removePrefix("/")
    }

    // ==================== Load ====================

    override suspend fun load(url: String): LoadResponse {
        val slug = toSlug(url)
        val item = fetchAll().find { it.event.url == slug }
            ?: throw ErrorLoadingException("ไม่พบช่อง/แมทช์: $slug")

        return newLiveStreamLoadResponse(item.event.name ?: slug, slug, slug) {
            this.posterUrl = item.event.logo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val slug = toSlug(data)
        val item = fetchAll().find { it.event.url == slug } ?: return false
        val freeStream = item.event.streams?.firstOrNull { it.vip != true && !it.url.isNullOrBlank() }
            ?: return false
        val embedUrl = freeStream.url ?: return false

        val html = app.get(embedUrl, referer = mainUrl).text
        val m3u8 = decodeAndExtractM3u8(html) ?: return false

        callback(
            newExtractorLink(
                this.name,
                item.event.name ?: this.name,
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
}
