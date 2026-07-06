// Streamed extension - maintained by timelapse4
// Originally based on work by Kraptor123

package com.timelapse4

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class StreamedPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Streamed())
        registerExtractorAPI(EmbedStreams())
        registerExtractorAPI(EmbedSporty())
    }
}
