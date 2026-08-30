package com.timstreams

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TimStreamsPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(TimStreamsProvider())
    }
}
