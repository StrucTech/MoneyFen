package com.moneyfen.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/** Loads a page in an invisible WebView (runs its JavaScript) and returns the rendered HTML. */
object WebPage {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun rendered(ctx: Context, url: String, waitMs: Long = 4000): String = withContext(Dispatchers.Main) {
        withTimeout(30_000) {
            suspendCancellableCoroutine { cont ->
                val wv = WebView(ctx.applicationContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                val handler = Handler(Looper.getMainLooper())
                var done = false
                fun finish(html: String) {
                    if (done) return
                    done = true
                    runCatching { wv.stopLoading(); wv.destroy() }
                    if (cont.isActive) cont.resume(html)
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, u: String) {
                        handler.postDelayed({
                            if (!done) view.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                                finish(runCatching { Json.decodeFromString<String>(raw) }.getOrDefault(raw ?: ""))
                            }
                        }, waitMs)
                    }
                }
                cont.invokeOnCancellation { handler.post { if (!done) { done = true; runCatching { wv.destroy() } } } }
                wv.loadUrl(url)
            }
        }
    }
}
