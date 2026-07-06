// Streamed extension - maintained by timelapse4
// Originally based on work by Kraptor123

package com.timelapse4

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*

class Streamed() : MainAPI() {
    override var mainUrl = "https://streamed.pk"
    override var name = "Streamed"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Live)
    override val vpnStatus = VPNStatus.MightBeNeeded

    companion object {
        private const val TAG = "Streamed"
        // Single shared mapper instead of re-creating it in every function
        val mapper = jacksonObjectMapper().registerKotlinModule()
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/api/matches/live/popular" to "Live Popular",
        "${mainUrl}/api/matches/live" to "Live",
        "${mainUrl}/api/matches/all-today/popular" to "Today's Popular Matches",
        "${mainUrl}/api/matches/football/popular" to "Football",
        "${mainUrl}/api/matches/fight/popular" to "Fight",
        "${mainUrl}/api/matches/american-football/popular" to "American Football",
        "${mainUrl}/api/matches/basketball/popular" to "Basketball",
        "${mainUrl}/api/matches/tennis/popular" to "Tennis",
        "${mainUrl}/api/matches/hockey/popular" to "Hockey",
        "${mainUrl}/api/matches/baseball/popular" to "Baseball",
        "${mainUrl}/api/matches/darts/popular" to "Darts",
        "${mainUrl}/api/matches/motor-sports/popular" to "Motor Sports",
        "${mainUrl}/api/matches/golf/popular" to "Golf",
        "${mainUrl}/api/matches/billiards/popular" to "Billiards",
        "${mainUrl}/api/matches/afl/popular" to "AFL",
        "${mainUrl}/api/matches/cricket/popular" to "Cricket",
        "${mainUrl}/api/matches/other/popular" to "Other"
    )

    private fun posterUrlOf(poster: String?, id: String?): String {
        val p = poster ?: ""
        return if (p.contains("api")) {
            "${mainUrl}${poster}"
        } else {
            "${mainUrl}/api/images/badge/${id}.webp"
        }
    }

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).text
        val matches: List<Matches> = mapper.readValue(document)

        val items = matches
            .filter { it.sources?.isNotEmpty() == true }
            .mapNotNull { match ->
                val title = match.title ?: return@mapNotNull null
                val firstSourceId =
                    match.sources?.firstOrNull()?.id ?: match.id ?: return@mapNotNull null
                val href = "$mainUrl/watch/${firstSourceId}"

                newLiveSearchResponse(title, href, TvType.Live) {
                    this.posterUrl = posterUrlOf(match.poster, match.id)
                    this.posterHeaders = defaultHeaders
                }
            }

        return newHomePageResponse(
            list = HomePageList(
                request.name,
                list = items,
                isHorizontalImages = true,
            ), hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val txt = app.get("$mainUrl/api/matches/all").text
        val matches: List<Matches> = mapper.readValue(txt)

        if (matches.isEmpty()) return emptyList()

        val unique = matches
            .associateBy { it.id ?: it.title ?: java.util.UUID.randomUUID().toString() }
            .values
            .toList()

        val q = query.lowercase().trim()
        val normalizedQuery = normalizeText(q)

        val sorted = unique.sortedWith(compareByDescending<Matches> {
            val t = it.title?.lowercase() ?: ""
            when {
                t.startsWith(q) -> 2
                t.contains(q) -> 1
                else -> 0
            }
        }.thenBy { it.title ?: "" })

        val filtered = sorted.filter { match ->
            val titleNorm = normalizeText(match.title ?: "")
            val sourceMatch = match.sources?.any { src ->
                normalizeText(src.id ?: "").contains(normalizedQuery)
            } ?: false

            titleNorm.contains(normalizedQuery) || sourceMatch
        }

        if (filtered.isEmpty()) return emptyList()

        return filtered.mapNotNull { match ->
            val title = match.title ?: return@mapNotNull null
            val firstSourceId =
                match.sources?.firstOrNull()?.id ?: match.id ?: return@mapNotNull null
            val href = "$mainUrl/watch/${firstSourceId}"

            newLiveSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrlOf(match.poster, match.id)
                this.posterHeaders = defaultHeaders
            }
        }
    }

    // Generic accent/diacritic stripper (works for Turkish, Vietnamese, etc. titles)
    private fun normalizeText(s: String): String {
        val lower = s.lowercase()
        val stripped = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return stripped.replace("[^a-z0-9 ]".toRegex(), "").trim()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val sourceId = url.substringAfterLast("/")

        val txt = app.get("$mainUrl/api/matches/all").text
        val matches: List<Matches> = mapper.readValue(txt)

        val match = matches.find { m ->
            m.sources?.any { it.id == sourceId } == true || m.id == sourceId
        } ?: return null

        val title = match.title ?: "Unknown match"

        val description = match.date?.let { dateTimestamp ->
            val currentTime = System.currentTimeMillis()
            val matchTime = if (dateTimestamp > 1000000000000L) dateTimestamp else dateTimestamp * 1000

            when {
                matchTime > currentTime -> {
                    val remainingMinutes = (matchTime - currentTime) / (1000 * 60)
                    val hours = remainingMinutes / 60
                    val minutes = remainingMinutes % 60

                    when {
                        hours > 24 -> {
                            val days = hours / 24
                            val remainingHours = hours % 24
                            "Starts in: $days d $remainingHours h"
                        }
                        hours > 0 -> "Starts in: $hours h $minutes min"
                        minutes > 0 -> "Starts in: $minutes min"
                        else -> "Match starting soon"
                    }
                }
                else -> "Match is live or finished - long-press and refresh if no link is found."
            }
        } ?: "Match time unknown"

        val tags = match.category?.let { listOf(it) } ?: emptyList()

        val sourcesCount = match.sources?.size ?: 0
        val finalDescription = if (sourcesCount > 1) {
            "$description\n\nAvailable sources: $sourcesCount"
        } else {
            description
        }

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = posterUrlOf(match.poster, match.id)
            this.posterHeaders = defaultHeaders
            this.plot = finalDescription
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {

        val sourceId = data.substringAfterLast("/")
        Log.d(TAG, "sourceId=$sourceId")

        try {
            val txt = app.get("$mainUrl/api/matches/all").text
            val matches: List<Matches> = mapper.readValue(txt)

            val match = matches.find { m ->
                val hasSourceId = m.sources?.any { it.id == sourceId } == true
                val isMatchId = m.id == sourceId
                hasSourceId || isMatchId
            }

            if (match == null) {
                Log.d(TAG, "No match found for $sourceId")
                return@withContext false
            }

            Log.d(TAG, "${match.title} - ${match.sources?.size ?: 0} sources")

            fun viewersOf(s: Stream): Int {
                return try {
                    when (val v = s.viewers) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> 0
                    }
                } catch (e: Exception) {
                    0
                }
            }

            // Fetch every source's stream list in parallel instead of sequentially
            val streamJobs = match.sources.orEmpty().mapNotNull { source ->
                val sType = source.source
                val sId = source.id
                if (sType == null || sId == null) return@mapNotNull null

                async {
                    try {
                        val streamApiUrl = "$mainUrl/api/stream/$sType/$sId"
                        val sResponse = app.get(streamApiUrl).text
                        val streams: List<Stream> = mapper.readValue(
                            sResponse,
                            object : com.fasterxml.jackson.core.type.TypeReference<List<Stream>>() {}
                        )
                        streams.map { Pair(it, sType) }
                    } catch (e: Exception) {
                        emptyList<Pair<Stream, String>>()
                    }
                }
            }

            val allStreams = streamJobs.awaitAll().flatten()

            val streamsWithPositiveViewers = allStreams.filter { viewersOf(it.first) > 0 }
            val streamsToProcess: List<Pair<Stream, String>> =
                if (streamsWithPositiveViewers.size >= 2) {
                    streamsWithPositiveViewers.sortedByDescending { viewersOf(it.first) }
                } else {
                    val zeroViewerStreams = allStreams.filter { viewersOf(it.first) == 0 }
                    streamsWithPositiveViewers.sortedByDescending { viewersOf(it.first) } + zeroViewerStreams
                }

            Log.d(TAG, "streamsToProcess=${streamsToProcess.size}")

            val processedStreams = mutableSetOf<String>()

            // Resolve embed URLs in parallel too
            val extractorJobs = streamsToProcess.mapNotNull { (stream, _) ->
                val embedUrl = stream.embedUrl.toString()
                if (embedUrl.isEmpty() || !processedStreams.add(embedUrl)) return@mapNotNull null

                async {
                    try {
                        loadExtractor(
                            url = embedUrl,
                            referer = mainUrl,
                            subtitleCallback = { sub -> subtitleCallback.invoke(sub) },
                            callback = { link -> callback.invoke(link) }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "extractor failed for $embedUrl: ${e.message}")
                    }
                }
            }

            extractorJobs.awaitAll()

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "loadLinks error: ${e.message}")
            return@withContext false
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Matches(
        @JsonProperty("id") val id: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("category") val category: String?,
        @JsonProperty("date") val date: Long?,
        @JsonProperty("popular") val popular: Boolean?,
        @JsonProperty("sources") val sources: List<Sources>?,
        @JsonProperty("poster") val poster: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Sources(
        @JsonProperty("id") val id: String?,
        @JsonProperty("source") val source: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Stream(
        @JsonProperty("id") val id: String?,
        @JsonProperty("streamNo") val streamNo: Int?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("embedUrl") val embedUrl: String?,
        @JsonProperty("source") val source: String?,
        @JsonProperty("hd") val hd: Boolean?,
        // Any? because the API sometimes returns this as a number, sometimes as a string
        @JsonProperty("viewers") val viewers: Any?
    )
}
