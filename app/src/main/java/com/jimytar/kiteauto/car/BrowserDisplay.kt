package com.jimytar.kiteauto.car

import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import com.jimytar.kiteauto.data.CarZoom
import com.jimytar.kiteauto.data.Services
import com.jimytar.kiteauto.web.BrowserView

/**
 * A [BrowserView] rendered into a car surface through a private [VirtualDisplay] + [Presentation].
 *
 * Rendering through our own display lets the page use a zoomed density ([CarZoom]) independent of the car's,
 * and keeps the page (and its audio) alive while the car surface comes and goes: the display is only
 * re-pointed and resized. One instance per Android Auto session ([shared]), so the page keeps playing while the
 * car screen shows something else.
 */
class BrowserDisplay private constructor(private val context: Context) {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    var browser: BrowserView? = null
        private set

    /** Media buttons (steering wheel, phone) for whatever this browser plays. */
    private val media = CarMediaSession(context, this)

    /** Point the browser at [surface] (size in px, [carDpi] = the car screen's density). */
    fun attach(surface: Surface, width: Int, height: Int, carDpi: Int) {
        val zoom = CarZoom(context).percent
        val dpi = carDpi * zoom / 100
        Log.i(TAG, "Surface ${width}x$height @${carDpi}dpi, zoom $zoom%")
        val existing = display
        if (existing != null) {
            existing.resize(width, height, dpi)
            existing.surface = surface
            return
        }
        val dm = context.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "KiteAuto", width, height, dpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )
        display = vd
        val p = Presentation(context, vd.display)
        val b = BrowserView(p.context)
        b.onTextFocus = { hint -> PhoneTextInput.request(context, hint) }
        b.inPageSelects = true
        p.setContentView(b)
        p.show()
        presentation = p
        browser = b
        b.load(Services(context).load().first().url)
    }

    /** The car surface is gone: keep the display (and playback) alive, just stop drawing. */
    fun detachSurface() {
        display?.surface = null
    }

    val size: Point?
        get() {
            val size = Point()
            @Suppress("DEPRECATION")
            display?.display?.getRealSize(size) ?: return null
            return size
        }

    /** Mouse-wheel scroll at ([x],[y]) surface px; [lines] > 0 scrolls down. */
    fun dispatchScroll(x: Float, y: Float, lines: Float) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y
            setAxisValue(MotionEvent.AXIS_VSCROLL, -lines)
        })
        val now = android.os.SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_MOUSE, 0)
        presentation?.window?.decorView?.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    /** Deliver a touch event in surface coordinates. */
    fun dispatchTouch(event: MotionEvent) {
        presentation?.window?.decorView?.dispatchTouchEvent(event)
    }

    fun load(url: String) = browser?.load(url)

    /** Leaves fullscreen video or goes back a page; false when there is nothing to go back to. */
    fun back(): Boolean = browser?.back() ?: false

    fun reload() = browser?.reload()

    /** Text from the phone or voice: into the page's focused field, else a search ([Services.toUrl]). */
    fun typeOrSearch(text: String) {
        val b = browser ?: return
        b.typeText(text) { filled -> if (!filled) b.load(Services.toUrl(text)) }
    }

    /**
     * Toolbar search: open the site's own search box (focusing it brings up the phone keyboard like tapping it);
     * sites without one get a plain phone prompt whose text runs as a search.
     */
    fun startSearch() {
        val b = browser ?: return
        b.openSiteSearch { opened ->
            if (!opened) {
                b.clearTextTarget()
                PhoneTextInput.request(context, "")
            }
        }
    }

    fun release() {
        media.release()
        browser?.destroy()
        presentation?.dismiss()
        display?.release()
        browser = null
        presentation = null
        display = null
    }

    companion object {
        private const val TAG = "KiteAuto"
        private var instance: BrowserDisplay? = null

        /** The session's browser, created on first use. */
        fun shared(context: Context): BrowserDisplay =
            instance ?: BrowserDisplay(context.applicationContext).also { instance = it }

        /** The session's browser if one exists (the car screen has been opened). */
        val current: BrowserDisplay? get() = instance

        /** End of the Android Auto session. */
        fun releaseShared() {
            instance?.release()
            instance = null
        }
    }
}
