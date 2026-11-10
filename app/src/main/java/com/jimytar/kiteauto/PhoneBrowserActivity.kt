package com.jimytar.kiteauto

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import com.jimytar.kiteauto.web.BrowserView

/** Full-screen browser on the phone: used to sign in (cookies are shared with the car) and to test sites. */
class PhoneBrowserActivity : Activity() {
    private lateinit var browser: BrowserView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browser = BrowserView(this)
        setContentView(browser)
        if (savedInstanceState?.let { browser.webView.restoreState(it) } == null) {
            browser.load(intent.getStringExtra("url") ?: return finish())
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!browser.back()) finish()
            }
        }
    }

    @Deprecated("Pre-Android 13 back handling")
    override fun onBackPressed() {
        if (!browser.back()) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        browser.webView.saveState(outState)
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}
