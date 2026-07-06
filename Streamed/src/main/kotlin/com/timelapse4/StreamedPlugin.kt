// Streamed extension - maintained by timelapse4
// Originally based on work by Kraptor123

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.timelapse4.EmbedSporty
import com.timelapse4.EmbedStreams
import com.timelapse4.Streamed

@CloudstreamPlugin
class StreamedPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Streamed())
        registerExtractorAPI(EmbedStreams(context))
        registerExtractorAPI(EmbedSporty(context))
    }
}
