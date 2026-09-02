package com.hubserieshd

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads [interceptUrl]'s matching request in a real, hidden WebView so that the
 * page's own JavaScript can run and construct whatever redirect chain it needs
 * (used here because hubserieshds.com sets its player <iframe> src purely via
 * client-side JS with no static token we can reverse-engineer).
 *
 * Not part of the pinned cloudstream core API in this repo, so it is
 * implemented locally as a plain OkHttp Interceptor.
 */
class WebViewResolver(
    private val interceptUrl: Regex,
    private val additionalUrls: List<Regex> = listOf(),
    private val timeoutMs: Long = 20_000L,
) : Interceptor {

    @SuppressLint("SetJavaScriptEnabled")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalUrl = request.url.toString()

        val activity = getActivity()
        if (activity == null) {
            return chain.proceed(request)
        }

        val latch = CountDownLatch(1)
        var interceptedUrl: String? = null
        var webView: WebView? = null

        activity.runOnUiThread {
            try {
                val wv = WebView(activity)
                webView = wv
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = request.header("User-Agent")
                    ?: wv.settings.userAgentString

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        req: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = req?.url?.toString()
                        if (url != null && interceptedUrl == null) {
                            val matches = interceptUrl.containsMatchIn(url) ||
                                    additionalUrls.any { it.containsMatchIn(url) }
                            if (matches) {
                                interceptedUrl = url
                                latch.countDown()
                            }
                        }
                        return super.shouldInterceptRequest(view, req)
                    }
                }

                val headers = mutableMapOf<String, String>()
                request.header("Referer")?.let { headers["Referer"] = it }
                wv.loadUrl(originalUrl, headers)
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(timeoutMs, TimeUnit.MILLISECONDS)

        activity.runOnUiThread {
            webView?.stopLoading()
            webView?.destroy()
        }

        val finalUrl = interceptedUrl ?: originalUrl
        val newRequest = request.newBuilder().url(finalUrl).build()
        return chain.proceed(newRequest)
    }
}
