package com.streamfree

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities

// Handles the embed pages hosted directly on streamfree.top, e.g.
// https://streamfree.top/embed/soccer/ghana-vs-england
// (the strmfree.st mirror the API's own `sources` list points to is currently down,
// so this scrapes the m3u8 straight out of streamfree.top's embed player instead)
class StreamFreeExtractor : ExtractorApi() {
    override val name = "StreamFree"
    override val mainUrl = "https://streamfree.top"
    override val requiresReferer = true

    private val m3u8Regex = Regex("""https?://[^"'\\\s]+\.m3u8[^"'\\\s]*""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ref = referer ?: "$mainUrl/"
        val doc = app.get(url, referer = ref).text

        val quality = Regex("""(\d{3,4})p""").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: Qualities.Unknown.value

        m3u8Regex.findAll(doc)
            .map { it.value }
            .distinct()
            .forEach { link ->
                callback(
                    ExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link,
                        referer = ref,
                        quality = quality,
                        type = INFER_TYPE
                    )
                )
            }
    }
}
