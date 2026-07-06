// Streamed extension - maintained by timelapse4
// Originally based on work by Kraptor123

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.timelapse4.EmbedSporty
import com.timelapse4.EmbedStreams
import com.timelapse4.Streamed

@CloudstreamPlugin
class StreamedPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Streamed())
        registerExtractorAPI(EmbedStreams())
        registerExtractorAPI(EmbedSporty())
    }
}
