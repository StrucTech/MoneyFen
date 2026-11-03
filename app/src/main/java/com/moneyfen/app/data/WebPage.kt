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
    suspend fun rendered(ctx: Context, url: String, waitMs: Long = 4000): String =
        eval(ctx, url, "document.documentElement.outerHTML", waitMs)

    /** Text of the element at [selector] after the page has rendered (retries while it's still empty). */
    suspend fun elementText(ctx: Context, url: String, selector: String): String {
        val sel = kotlinx.serialization.json.JsonPrimitive(selector).toString()
        val js = "(function(){var e=document.querySelector($sel);return e?(e.innerText||e.textContent||''):''})()"
        return eval(ctx, url, js, 4000, retryWhileEmpty = true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun eval(ctx: Context, url: String, script: String, waitMs: Long = 4000, retryWhileEmpty: Boolean = false): String = withContext(Dispatchers.Main) {
        withTimeout(45_000) {
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
                    var tries = 0
                    fun attempt(view: WebView) {
                        if (done) return
                        view.evaluateJavascript(script) { raw ->
                            val v = runCatching { Json.decodeFromString<String>(raw) }.getOrDefault(raw ?: "")
                            if (retryWhileEmpty && v.isBlank() && ++tries < 6) handler.postDelayed({ attempt(view) }, 2000)
                            else finish(v)
                        }
                    }
                    override fun onPageFinished(view: WebView, u: String) {
                        handler.postDelayed({ attempt(view) }, waitMs)
                    }
                }
                cont.invokeOnCancellation { handler.post { if (!done) { done = true; runCatching { wv.destroy() } } } }
                wv.loadUrl(url)
            }
        }
    }
}
