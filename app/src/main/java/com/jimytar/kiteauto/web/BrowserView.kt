package com.jimytar.kiteauto.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * WebView plus fullscreen-video handling. Used both on the car surface and on the phone;
 * all instances share the app's WebView profile, so a login done on the phone works in the car.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BrowserView(context: Context) : FrameLayout(context) {
    val webView = WebView(context)
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /**
     * Car screen: the WebView lives in a Presentation window, where Chromium's native <select> popup (a Dialog)
     * can't be added and crashes the app. When true, pages get an in-page option list instead.
     */
    var inPageSelects = false

    /** Called (main thread) when a text field in the page gets focus; arg = its placeholder/label. */
    var onTextFocus: ((String) -> Unit)? = null

    init {
        // Debug builds: inspect pages via chrome://inspect / Chrome DevTools Protocol over adb.
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
        addView(webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Google refuses sign-in from embedded WebViews; drop the WebView markers from the UA.
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
        }
        // Only reports "a text field got focus" (plus its label); exposes no data or actions to pages.
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun onTextFocus(hint: String) {
                post { onTextFocus?.invoke(hint) }
            }
        }, "KiteAuto")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(TEXT_FOCUS_JS, null)
                if (inPageSelects) view.evaluateJavascript(IN_PAGE_SELECT_JS, null)
                if (android.net.Uri.parse(url).host?.endsWith("youtube.com") == true) {
                    view.evaluateJavascript(YOUTUBE_BIG_CONTROLS_JS, null)
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                exitFullscreen()
                fullscreenView = view
                fullscreenCallback = callback
                addView(view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
                webView.visibility = GONE
            }

            override fun onHideCustomView() = exitFullscreen()
        }
    }

    fun load(url: String) {
        exitFullscreen()
        webView.loadUrl(url)
    }

    /** Leaves fullscreen video, else goes back in history. Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        if (fullscreenView != null) {
            fullscreenCallback?.onCustomViewHidden()
            exitFullscreen()
            return true
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun reload() = webView.reload()

    /**
     * Put [text] into the page's last focused text field and submit it (form submit / Enter).
     * [result] gets false when there is no such field (caller then searches instead).
     */
    fun typeText(text: String, result: (Boolean) -> Unit) {
        webView.evaluateJavascript("($TYPE_TEXT_JS)(${org.json.JSONObject.quote(text)})") { result(it == "true") }
    }

    /** Open the site's own search box (YouTube: its search button); [result] false if none found. */
    fun openSiteSearch(result: (Boolean) -> Unit) {
        webView.evaluateJavascript(OPEN_SEARCH_JS) { result(it == "true") }
    }

    /** Forget the last focused field, so the next [typeText] reports false. */
    fun clearTextTarget() = webView.evaluateJavascript("window.__kiteLast = null", null)

    /** What the page is playing, read from its <video> and Media Session metadata; null if nothing. */
    fun readMedia(result: (PageMedia?) -> Unit) {
        webView.evaluateJavascript(READ_MEDIA_JS) { json ->
            result(runCatching {
                val o = org.json.JSONObject(org.json.JSONTokener(json).nextValue() as String)
                if (!o.getBoolean("video")) null else PageMedia(
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    artworkUrl = o.getString("art").ifEmpty { null },
                    playing = o.getBoolean("playing"),
                    positionMs = (o.getDouble("pos") * 1000).toLong(),
                    durationMs = (o.getDouble("dur") * 1000).toLong(),
                )
            }.getOrNull())
        }
    }

    fun play() = webView.evaluateJavascript("document.querySelector('video')?.play()", null)
    fun pause() = webView.evaluateJavascript("document.querySelector('video')?.pause()", null)
    fun seekTo(ms: Long) =
        webView.evaluateJavascript("(v=>v&&(v.currentTime=${ms / 1000.0}))(document.querySelector('video'))", null)

    /** The site's own "next" button (YouTube labels it "Next video"/"Next"). */
    fun next() = webView.evaluateJavascript(
        "document.querySelector('[aria-label^=\"Next\"], .ytp-next-button')?.click()", null
    )

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        fullscreenCallback = null
        removeView(view)
        webView.visibility = VISIBLE
    }

    fun destroy() {
        exitFullscreen()
        removeAllViews()
        webView.destroy()
    }

    private companion object {
        /** Report focus on editable fields to the app (remembered as the target for typed text). */
        const val TEXT_FOCUS_JS = """(function () {
  if (window.__kiteFocus) return;
  window.__kiteFocus = true;
  document.addEventListener('focusin', function (e) {
    var t = e.target;
    if (!t.matches || !t.matches('input:not([type=checkbox]):not([type=radio]):not([type=button])' +
        ':not([type=submit]):not([type=range]):not([type=hidden]),textarea,[contenteditable=""],[contenteditable=true]')) return;
    window.__kiteLast = t;
    KiteAuto.onTextFocus(t.placeholder || t.getAttribute('aria-label') || '');
  }, true);
})();"""

        /** Replace the native <select> popup with a touch-sized in-page list (single-choice selects). */
        const val IN_PAGE_SELECT_JS = """(function () {
  if (window.__kiteSelect) return;
  window.__kiteSelect = true;
  function open(sel) {
    var host = document.fullscreenElement || document.body;
    var back = document.createElement('div');
    back.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.6);' +
      'display:flex;align-items:center;justify-content:center';
    var box = document.createElement('div');
    box.style.cssText = 'background:#202124;color:#fff;border-radius:16px;max-height:80vh;overflow:auto;' +
      'min-width:40vw;max-width:80vw;font:20px sans-serif;padding:8px 0';
    Array.prototype.forEach.call(sel.options, function (o, i) {
      if (o.hidden) return;
      var row = document.createElement('div');
      row.textContent = o.label || o.text;
      row.style.cssText = 'padding:16px 24px;' + (o.disabled ? 'opacity:.4;' : '') +
        (i === sel.selectedIndex ? 'background:#3c4043;font-weight:bold;' : '');
      if (!o.disabled) row.addEventListener('click', function (e) {
        e.stopPropagation();
        back.remove();
        if (sel.selectedIndex !== i) {
          sel.selectedIndex = i;
          sel.dispatchEvent(new Event('input', { bubbles: true }));
          sel.dispatchEvent(new Event('change', { bubbles: true }));
        }
      });
      box.appendChild(row);
    });
    back.addEventListener('click', function () { back.remove(); });
    back.appendChild(box);
    host.appendChild(back);
  }
  function intercept(e) {
    var sel = e.target && e.target.closest && e.target.closest('select');
    if (!sel || sel.multiple || sel.size > 1 || sel.disabled) return;
    e.preventDefault(); // stops Chromium's native popup (and the click that would follow a touch)
    e.stopPropagation();
    if (e.type === 'touchend' || e.type === 'click') { sel.blur(); open(sel); }
  }
  ['pointerdown', 'mousedown', 'touchstart', 'touchend', 'click'].forEach(function (t) {
    document.addEventListener(t, intercept, { capture: true, passive: false });
  });
})();"""

        const val OPEN_SEARCH_JS = """(function () {
  var b = document.querySelector('button[aria-label="Search YouTube"], button[aria-label="Search"]');
  if (b && b.getClientRects().length) { b.click(); return true; }
  var i = document.querySelector('input[type=search], input[name=q], input[name=search_query]');
  if (i) { i.focus(); return document.activeElement === i; }
  return false;
})()"""

        const val TYPE_TEXT_JS = """function (text) {
  var t = window.__kiteLast;
  if (!t || !t.isConnected) return false;
  t.focus();
  if (t.isContentEditable) t.textContent = text;
  else Object.getOwnPropertyDescriptor(Object.getPrototypeOf(t), 'value').set.call(t, text);
  t.dispatchEvent(new Event('input', { bubbles: true }));
  t.dispatchEvent(new Event('change', { bubbles: true }));
  if (t.form) { t.form.requestSubmit ? t.form.requestSubmit() : t.form.submit(); }
  else {
    var k = { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true };
    t.dispatchEvent(new KeyboardEvent('keydown', k));
    t.dispatchEvent(new KeyboardEvent('keyup', k));
  }
  return true;
}"""

        /**
         * YouTube's player buttons are ~48 CSS px, small to hit on a car screen at reduced page zoom. Scale them
         * (transform keeps the layout; the hit area scales too). YouTube is a single-page app: one injection per
         * page load covers later videos.
         */
        const val YOUTUBE_BIG_CONTROLS_JS = """(function () {
  if (document.getElementById('kite-big-controls')) return;
  var s = document.createElement('style');
  s.id = 'kite-big-controls';
  s.textContent =
    '.player-controls-middle-core-buttons { transform: scale(1.7); }' +
    '.fullscreen-icon { transform: scale(1.7); transform-origin: right bottom; }' +
    '.player-settings-icon, .ytmClosedCaptioningButtonButton { transform: scale(1.4); transform-origin: right top; }';
  document.head.appendChild(s);
})();"""

        const val READ_MEDIA_JS = """(function () {
  var v = document.querySelector('video');
  var m = navigator.mediaSession && navigator.mediaSession.metadata;
  var art = m && m.artwork && m.artwork.length ? m.artwork[m.artwork.length - 1].src : '';
  var id = /youtube\.com$/.test(location.hostname) && new URLSearchParams(location.search).get('v');
  if (!art && id) art = 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
  if (art) art = new URL(art, location.href).href;
  return JSON.stringify({
    art: art,
    video: !!v,
    title: ((m && m.title) || document.title || '').replace(/ - YouTube$/, ''),
    artist: (m && m.artist) || location.hostname,
    playing: !!(v && !v.paused && !v.ended),
    pos: v ? v.currentTime || 0 : 0,
    dur: v && isFinite(v.duration) ? v.duration : 0
  });
})()"""
    }
}

/** Snapshot of the media playing in a page. */
data class PageMedia(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)
