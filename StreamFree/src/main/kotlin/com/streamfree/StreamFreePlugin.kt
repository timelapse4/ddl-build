package com.streamfree

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StreamFreePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(StreamFreeProvider())
        registerExtractorAPI(StreamFreeExtractor())
    }
}
