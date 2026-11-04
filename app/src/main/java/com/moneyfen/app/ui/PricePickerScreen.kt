package com.moneyfen.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.moneyfen.app.app
import com.moneyfen.app.data.Snapshot
import com.moneyfen.core.PriceText
import kotlinx.coroutines.launch

/** Injected into the page: tapping an element outlines it and reports a CSS selector + its text. */
private const val PICK_JS = """
(function(){
  if (window.__mfPick) return; window.__mfPick = true;
  function path(el){
    var parts=[];
    while(el && el.nodeType==1 && el!==document.body && el!==document.documentElement){
      if(el.id && !/\d{3,}/.test(el.id) && document.querySelectorAll('#'+CSS.escape(el.id)).length==1){ parts.unshift('#'+CSS.escape(el.id)); return parts.join(' > '); }
      var i=1,s=el; while((s=s.previousElementSibling)) if(s.tagName==el.tagName) i++;
      parts.unshift(el.tagName.toLowerCase()+':nth-of-type('+i+')');
      el=el.parentElement;
    }
    return 'body > '+parts.join(' > ');
  }
  document.addEventListener('click', function(e){
    if (!window.__mfPickMode) return;
    e.preventDefault(); e.stopPropagation();
    var el=e.target;
    document.querySelectorAll('[data-mfpick]').forEach(function(x){ x.style.outline=''; x.removeAttribute('data-mfpick'); });
    el.style.outline='3px solid #e53935'; el.setAttribute('data-mfpick','1');
    MoneyFen.pick(path(el), (el.innerText||el.textContent||'').trim().substring(0,80), location.href);
  }, true);
})();
"""

private class Bridge(val onPick: (String, String, String) -> Unit) {
    @JavascriptInterface fun pick(selector: String, text: String, href: String) = onPick(selector, text, href)
}

/** Opens the price page inside the app; the user taps the price and it's remembered for live updates. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricePickerScreen(snap: Snapshot, accountId: Long) {
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val av = snap.accountById[accountId]
    val url = av?.account?.priceUrl?.takeIf { it.startsWith("http") }
    var selector by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf(url ?: "") }
    var pickMode by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val value = PriceText.firstNumber(picked)
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(tr("Tap the price", "دوس على السعر")) },
            navigationIcon = { IconButton(onClick = { val w = webView; if (w != null && w.canGoBack()) w.goBack() else nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        if (url == null) { Text(tr("Add the page link first and save.", "حط لينك الصفحة واحفظ الأول."), Modifier.padding(16.dp)); return }
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            androidx.compose.material3.SegmentedButton(!pickMode, {
                pickMode = false; webView?.evaluateJavascript("window.__mfPickMode=false", null)
            }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 2)) { Text(tr("🔐 Browse / log in", "🔐 تصفح / سجّل دخول")) }
            androidx.compose.material3.SegmentedButton(pickMode, {
                pickMode = true; webView?.evaluateJavascript(PICK_JS + ";window.__mfPickMode=true", null)
            }, androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 2)) { Text(tr("👆 Pick the price", "👆 اختار السعر")) }
        }
        Text(if (pickMode) tr("Tap the fund's price. It gets a red frame.", "دوس على سعر الصندوق. هيتعلّم بإطار أحمر.")
            else tr("If the site asks you to log in, log in here once (it's remembered), open the fund page, then switch to \"Pick the price\".",
                "لو الموقع طلب تسجيل دخول، سجّل هنا مرة واحدة (هيتفتكر)، افتح صفحة الصندوق، وبعدين اختار \"اختار السعر\"."),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(Bridge { s, t, h -> post { selector = s; picked = t; pageUrl = h } }, "MoneyFen")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String) {
                            android.webkit.CookieManager.getInstance().flush()
                            val js = PICK_JS + ";window.__mfPickMode=" + pickMode
                            view.evaluateJavascript(js, null)
                            view.postDelayed({ view.evaluateJavascript(js, null) }, 3000)
                        }
                    }
                    webView = this
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
        )
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (picked.isBlank()) tr("Nothing picked yet", "لسه مختارتش") else "\"$picked\"", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (value != null) Text(fmtPlain(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Button(enabled = value != null && selector != null && av != null, onClick = {
                    val a = av!!.account
                    android.webkit.CookieManager.getInstance().flush()
                    ctx.app.appScope.launch {
                        repo.saveAccount(a.copy(priceUrl = pageUrl.ifBlank { a.priceUrl }, priceAnchor = "css:" + selector, unitPrice = value,
                            autoPrice = true, priceAt = System.currentTimeMillis()))
                    }
                    nav.popBackStack()
                }) { Text(tr("Use this", "استخدم ده")) }
            }
        }
    }
}
