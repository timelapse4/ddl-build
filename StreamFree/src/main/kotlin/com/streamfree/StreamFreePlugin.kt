package com.streamfree

import android.content.Context
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StreamFreePlugin : BasePlugin() {
    override fun load(context: Context) {
        registerMainAPI(StreamFreeProvider())
        registerExtractorAPI(StreamFreeExtractor())
    }
}
