package com.DaddyLiveHD

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DaddyLiveHDPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DaddyLiveHD())
    }
}
