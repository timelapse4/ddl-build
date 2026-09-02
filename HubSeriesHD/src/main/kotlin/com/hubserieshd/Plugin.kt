package com.hubserieshd

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class HubSeriesHDPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HubSeriesHDProvider())
    }
}
