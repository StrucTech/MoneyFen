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
    e.preventDefault(); e.stopPropagation();
    var el=e.target;
    document.querySelectorAll('[data-mfpick]').forEach(function(x){ x.style.outline=''; x.removeAttribute('data-mfpick'); });
    el.style.outline='3px solid #e53935'; el.setAttribute('data-mfpick','1');
    MoneyFen.pick(path(el), (el.innerText||el.textContent||'').trim().substring(0,80));
  }, true);
})();
"""

private class Bridge(val onPick: (String, String) -> Unit) {
    @JavascriptInterface fun pick(selector: String, text: String) = onPick(selector, text)
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
    val value = PriceText.firstNumber(picked)
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0),
            title = { Text(tr("Tap the price", "دوس على السعر")) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        if (url == null) { Text(tr("Add the page link first and save.", "حط لينك الصفحة واحفظ الأول."), Modifier.padding(16.dp)); return }
        Text(tr("Wait for the page to load, then tap the fund's price. It gets a red frame.", "استنى الصفحة تحمّل، وبعدين دوس على سعر الصندوق. هيتعلّم بإطار أحمر."),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(Bridge { s, t -> post { selector = s; picked = t } }, "MoneyFen")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String) {
                            view.evaluateJavascript(PICK_JS, null)
                            view.postDelayed({ view.evaluateJavascript(PICK_JS, null) }, 3000)
                        }
                    }
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
                    ctx.app.appScope.launch {
                        repo.saveAccount(a.copy(priceAnchor = "css:" + selector, unitPrice = value, autoPrice = true, priceAt = System.currentTimeMillis()))
                    }
                    nav.popBackStack()
                }) { Text(tr("Use this", "استخدم ده")) }
            }
        }
    }
}
