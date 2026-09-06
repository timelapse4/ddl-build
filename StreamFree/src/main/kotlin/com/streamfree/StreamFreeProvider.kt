package com.streamfree

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class StreamFreeProvider : MainAPI() {
    override var mainUrl = "https://streamfree.top"
    override var name = "StreamFree"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Live)

    private val apiUrl = "$mainUrl/api/v1"

    data class Stream(
        val name: String,
        val category: String? = null,
        val league: String? = null,
        val stream_key: String,
        val match_timestamp: Long? = null,
        val sources: List<String> = emptyList(),
        val thumbnail_url: String? = null
    )

    data class StreamsResponse(val count: Int? = null, val streams: List<Stream> = emptyList())
    data class SourcesResponse(val stream_key: String? = null, val sources: List<String> = emptyList())

    // category slug -> display name, taken from GET /api/v1/categories
    override val mainPage = mainPageOf(
        "soccer" to "Soccer",
        "basketball" to "Basketball",
        "hockey" to "Hockey",
        "combat" to "Combat Sports",
        "baseball" to "Baseball",
        "football" to "American Football",
        "racing" to "Racing",
        "tennis" to "Tennis",
        "cricket" to "Cricket"
    )

    private fun Stream.toSearchResponse(): LiveSearchResponse {
        val displayName = if (!league.isNullOrBlank()) "$name ($league)" else name
        return newLiveSearchResponse(
            name = displayName,
            url = this.stream_key,
            type = TvType.Live
        ) {
            this.posterUrl = thumbnail_url
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("$apiUrl/streams?category=${request.data}")
            .parsedSafe<StreamsResponse>()
        val list = res?.streams?.map { it.toSearchResponse() } ?: emptyList()
        return newHomePageResponse(request.name, list, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$apiUrl/streams").parsedSafe<StreamsResponse>()
        return res?.streams
            ?.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (it.league?.contains(query, ignoreCase = true) == true)
            }
            ?.map { it.toSearchResponse() }
            ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        // `url` is the bare stream_key
        val stream = app.get("$apiUrl/streams/$url").parsedSafe<Stream>()
        val title = stream?.let { s -> if (!s.league.isNullOrBlank()) "${s.name} (${s.league})" else s.name } ?: url
        // strmfree.st (the domain the API's own `sources` list points to) is currently dead,
        // so instead we drive the embed page hosted directly on streamfree.top:
        // https://streamfree.top/embed/{category}/{stream_key}
        val category = stream?.category ?: "soccer"
        val embedUrl = "$mainUrl/embed/$category/$url"
        return newLiveStreamLoadResponse(
            name = title,
            url = embedUrl,
            dataUrl = embedUrl
        ) {
            this.posterUrl = stream?.thumbnail_url
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // `data` is the streamfree.top embed page URL itself
        return loadExtractor(data, mainUrl, subtitleCallback, callback)
    }
}
