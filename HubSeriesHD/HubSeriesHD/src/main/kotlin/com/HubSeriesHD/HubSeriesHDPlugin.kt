package com.HubSeriesHD

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class HubSeriesHDPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HubSeriesHD())
    }
}
