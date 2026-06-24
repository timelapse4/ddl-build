package com.HubSeriesHD

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HubSeriesHDPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HubSeriesHD())
    }
}
