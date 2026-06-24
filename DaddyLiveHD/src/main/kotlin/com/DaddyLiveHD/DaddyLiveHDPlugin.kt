package com.DaddyLiveHD

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DaddyLiveHDPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(DaddyLiveHD())
    }
}
