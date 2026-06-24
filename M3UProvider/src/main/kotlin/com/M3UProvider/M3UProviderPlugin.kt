package com.M3UProvider

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class M3UProviderPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(M3UProvider())
    }
}
