package com.jimytar.kiteauto.car

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService
import com.jimytar.kiteauto.R
import com.jimytar.kiteauto.data.Services

/**
 * Legacy "projection" entry point (unofficial Android Auto SDK, aauto.aar): a full-screen custom app that is
 * not a navigation app, so Waze/Maps keep guidance and the HUD. The browser renders through [BrowserDisplay]
 * into a full-screen SurfaceView; the car's real touch events are forwarded to it unchanged.
 */
class KiteCarActivity : CarActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var browser: BrowserDisplay
    private lateinit var toolbar: View
    private lateinit var servicesPanel: FrameLayout
    private lateinit var servicesRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var voice: VoiceInput
    private lateinit var edgeHandle: View
    private val hideToolbar = Runnable {
        toolbar.visibility = View.GONE
        edgeHandle.visibility = View.VISIBLE
    }

    // Knob (rotary controller) cursor: tilt moves it, press taps under it, rotation scrolls the page.
    private lateinit var cursor: View
    private val hideCursor = Runnable { cursor.visibility = View.GONE }

    // Left-edge gesture state: touches starting in the edge strip open the toolbar and never reach the page.
    private var edgeGesture = false
    private var edgeDownX = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setIgnoreConfigChanges(-1) // all config changes: keep the page alive
        carUiController.statusBarController.hideAppHeader()
        carUiController.menuController.hideMenuButton()
        browser = BrowserDisplay.shared(this)

        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                browser.attach(holder.surface, width, height, resources.displayMetrics.densityDpi)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) = browser.detachSurface()
        })
        surfaceView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                edgeGesture = event.x < dp(EDGE_DP)
                edgeDownX = event.x
            }
            if (edgeGesture) {
                // Swipe right from the edge, or a tap on it, shows the toolbar.
                val moved = event.x - edgeDownX
                if (event.actionMasked == MotionEvent.ACTION_MOVE && moved > dp(EDGE_SWIPE_DP)) showToolbar()
                if (event.actionMasked == MotionEvent.ACTION_UP && moved < dp(EDGE_SWIPE_DP)) showToolbar()
            } else {
                browser.dispatchTouch(event)
            }
            true
        }

        servicesPanel = buildServicesPanel()
        status = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xDD202124.toInt())
            }
            visibility = View.GONE
        }
        voice = VoiceInput(this) { text ->
            status.text = text
            status.visibility = if (text == null) View.GONE else View.VISIBLE
        }
        toolbar = buildToolbar()
        // Hint where the toolbar lives: a thin pill on the left edge (touches pass through to the edge gesture).
        edgeHandle = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(0x80FFFFFF.toInt())
            }
        }
        cursor = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF)
                setStroke(dp(3), Color.WHITE)
            }
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        val root = FrameLayout(this)
        setContentView(root.apply {
            // Holds input focus so knob (D-pad / rotary) keys reach onKeyDown; nothing else is focusable.
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
            addView(surfaceView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // Vertically centred; buttons small enough to stay clear of the site's header and bottom bar.
            addView(edgeHandle, FrameLayout.LayoutParams(dp(5), dp(56), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = dp(5)
            })
            addView(toolbar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START))
            addView(servicesPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            addView(cursor, FrameLayout.LayoutParams(dp(CURSOR_DP), dp(CURSOR_DP)))
        })
        showToolbar()
        root.requestFocus()
        // Knob: rotation would arrive as AXIS_SCROLL (not forwarded by AA so far); the touch surface on top of
        // the knob arrives as raw touchpad motion (SOURCE_TOUCHPAD) and drives the cursor like a laptop trackpad.
        // A tap on it comes separately as DPAD_CENTER (handled in onKeyDown: tap under the cursor).
        root.setOnGenericMotionListener { _, ev ->
            if (ev.actionMasked != MotionEvent.ACTION_MOVE || ev.eventTime - lastMotionLog > 250) {
                lastMotionLog = ev.eventTime
                android.util.Log.i("KiteAuto", "Motion src=0x${Integer.toHexString(ev.source)} act=${ev.actionMasked} " +
                    "x=${ev.x} y=${ev.y} scroll=${ev.getAxisValue(MotionEvent.AXIS_SCROLL)} " +
                    "v=${ev.getAxisValue(MotionEvent.AXIS_VSCROLL)} h=${ev.getAxisValue(MotionEvent.AXIS_HSCROLL)} " +
                    "dev=${ev.device?.name} rangeX=${ev.device?.getMotionRange(MotionEvent.AXIS_X, ev.source)?.range}")
            }
            when {
                ev.isFromSource(android.view.InputDevice.SOURCE_TOUCHPAD) -> { trackpad(ev); true }
                // BMW iDrive: knob input arrives as mouse-wheel scrolls (AXIS_VSCROLL > 0 = up).
                ev.actionMasked == MotionEvent.ACTION_SCROLL && ev.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0f -> {
                    rotate(-ev.getAxisValue(MotionEvent.AXIS_VSCROLL))
                    true
                }
                ev.actionMasked == MotionEvent.ACTION_SCROLL && ev.getAxisValue(MotionEvent.AXIS_SCROLL) != 0f -> {
                    rotate(if (ev.getAxisValue(MotionEvent.AXIS_SCROLL) < 0) 1f else -1f)
                    true
                }
                else -> false
            }
        }
    }

    /** Back, Services, Search, Mic, Reload — fades out after a few idle seconds, comes back on any touch. */
    private fun buildToolbar() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(button(R.drawable.ic_back) { browser.back() })
        addView(button(R.drawable.ic_list) {
            if (servicesPanel.visibility == View.VISIBLE) servicesPanel.visibility = View.GONE else openServicesPanel()
        })
        addView(button(R.drawable.ic_search) {
            voice.cancel()
            browser.startSearch()
        })
        addView(button(R.drawable.ic_mic) { voice.listen(browser::typeOrSearch) })
        addView(button(R.drawable.ic_reload) { browser.reload() })
    }

    private fun button(icon: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC202124.toInt())
        }
        setOnClickListener { onClick(); showToolbar() }
        isFocusable = false // the knob drives the cursor; never let D-pad focus land on our buttons
        layoutParams = LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)).apply { bottomMargin = dp(4) }
    }

    /** Services picker: a centred, sideways-scrolling row of tiles over the dimmed page; tap outside closes. */
    private fun buildServicesPanel() = FrameLayout(this).apply {
        visibility = View.GONE
        setBackgroundColor(0xB3000000.toInt())
        setOnClickListener { visibility = View.GONE }
        servicesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xF0202124.toInt())
            }
            addView(servicesRow)
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(64)
            rightMargin = dp(24)
        })
    }

    /** Show the services picker with the current list (edited on the phone at any time). */
    private fun openServicesPanel() {
        servicesRow.removeAllViews()
        Services(this).load().forEach { s -> servicesRow.addView(serviceTile(s)) }
        servicesPanel.visibility = View.VISIBLE
    }

    /** Round badge with the service's initial (colour derived from its name) and the name below. */
    private fun serviceTile(s: com.jimytar.kiteauto.data.Service) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.TRANSPARENT)
        }
        isClickable = true
        isFocusable = false
        setOnClickListener {
            browser.load(s.url)
            servicesPanel.visibility = View.GONE
        }
        addView(TextView(context).apply {
            text = s.name.trim().take(1).uppercase()
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(TILE_COLORS[Math.floorMod(s.name.hashCode(), TILE_COLORS.size)])
            }
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(TextView(context).apply {
            text = s.name
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(dp(104), WRAP_CONTENT))
    }

    private fun showToolbar() {
        toolbar.visibility = View.VISIBLE
        edgeHandle.visibility = View.GONE
        handler.removeCallbacks(hideToolbar)
        handler.postDelayed(hideToolbar, TOOLBAR_HIDE_MS)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        android.util.Log.i("KiteAuto", "Key down $keyCode (${android.view.KeyEvent.keyCodeToString(keyCode)}) repeat=${event.repeatCount}")
        val dir = TILT_DIRS[keyCode]
        when {
            dir != null -> startTilt(keyCode, dir)
            // Rotation: the BMW sends mouse-wheel scrolls (see the generic motion listener); some cars send keys.
            keyCode == android.view.KeyEvent.KEYCODE_SOFT_RIGHT || keyCode == android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT -> rotate(1f)
            keyCode == android.view.KeyEvent.KEYCODE_SOFT_LEFT || keyCode == android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> rotate(-1f)
            keyCode == android.view.KeyEvent.KEYCODE_TAB -> rotate(if (event.isShiftPressed) -1f else 1f)
            keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER -> {
                if (cursor.visibility == View.VISIBLE) tapAtCursor() else moveCursor(0f, 0f)
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    /** Swallow the key-up of every key the knob handler consumed, so Android Auto doesn't act on it. */
    /**
     * Held tilt: the BMW sends one key-down, a second after ~0.5 s, then nothing until key-up. So the ring keeps
     * moving on our own timer from the first key-down until the key-up, speeding up while held.
     */
    private var tiltKey = 0
    private var tiltDir = Pair(0f, 0f)
    private var tiltSteps = 0
    private val tiltTick = object : Runnable {
        override fun run() {
            tiltSteps++
            val step = dp(GLIDE_STEP_DP) * minOf(1f + tiltSteps / GLIDE_RAMP_STEPS, GLIDE_MAX_FACTOR)
            moveCursor(tiltDir.first * step, tiltDir.second * step)
            handler.postDelayed(this, TILT_REPEAT_MS)
        }
    }

    private fun startTilt(keyCode: Int, dir: Pair<Float, Float>) {
        if (tiltKey == keyCode) return // the car's own repeat while we're already moving
        stopTilt()
        tiltKey = keyCode
        tiltDir = dir
        tiltSteps = 0
        moveCursor(dir.first * dp(CURSOR_STEP_DP), dir.second * dp(CURSOR_STEP_DP))
        handler.postDelayed(tiltTick, TILT_HOLD_MS)
    }

    private fun stopTilt() {
        handler.removeCallbacks(tiltTick)
        tiltKey = 0
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        android.util.Log.i("KiteAuto", "Key up $keyCode")
        if (keyCode == tiltKey) stopTilt()
        return if (keyCode in KNOB_KEYS) true else super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean, inTouchMode: Boolean) {
        android.util.Log.i("KiteAuto", "Window focus $hasFocus touchMode=$inTouchMode")
        super.onWindowFocusChanged(hasFocus, inTouchMode)
        if (hasFocus) (cursor.parent as View).requestFocus()
    }

    /** Show the cursor (centre of the screen the first time) and move it, clamped to the screen. */
    private fun moveCursor(dx: Float, dy: Float) {
        val root = cursor.parent as View
        if (cursor.visibility != View.VISIBLE && cursor.x == 0f && cursor.y == 0f) {
            cursor.x = (root.width - cursor.width) / 2f
            cursor.y = (root.height - cursor.height) / 2f
        }
        val maxY = (root.height - cursor.height).toFloat()
        // Knob rotation doesn't reach projection apps, so tilting against the top/bottom edge scrolls the page.
        if (cursor.visibility == View.VISIBLE && ((dy < 0 && cursor.y <= 0f) || (dy > 0 && cursor.y >= maxY))) {
            browser.dispatchScroll(cursor.x + cursor.width / 2f, cursor.y + cursor.height / 2f,
                if (dy > 0) SCROLL_LINES else -SCROLL_LINES)
        }
        cursor.x = (cursor.x + dx).coerceIn(0f, (root.width - cursor.width).toFloat())
        cursor.y = (cursor.y + dy).coerceIn(0f, maxY)
        cursor.visibility = View.VISIBLE
        handler.removeCallbacks(hideCursor)
        handler.postDelayed(hideCursor, CURSOR_HIDE_MS)
    }

    private fun cursorCenter() = Pair(cursor.x + cursor.width / 2f, cursor.y + cursor.height / 2f)

    private fun tapAtCursor() {
        val (x, y) = cursorCenter()
        val down = android.os.SystemClock.uptimeMillis()
        val d = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
        val u = MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, x, y, 0)
        browser.dispatchTouch(d)
        browser.dispatchTouch(u)
        d.recycle()
        u.recycle()
        moveCursor(0f, 0f) // keep it visible a bit longer
    }

    private var lastMotionLog = 0L
    private var padX = 0f
    private var padY = 0f

    /** Relative cursor movement from the knob's touch surface, scaled so a full swipe crosses ~the screen. */
    private fun trackpad(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { padX = ev.x; padY = ev.y; moveCursor(0f, 0f) }
            MotionEvent.ACTION_MOVE -> {
                val root = cursor.parent as View
                val range = ev.device?.getMotionRange(MotionEvent.AXIS_X, ev.source)
                val gain = if (range != null && range.range > 0) root.width / range.range else TRACKPAD_GAIN
                moveCursor((ev.x - padX) * gain, (ev.y - padY) * gain)
                padX = ev.x
                padY = ev.y
            }
        }
    }

    /** Knob rotation scrolls the page under the cursor (or the screen centre). */
    private fun rotate(direction: Float) {
        val root = cursor.parent as View
        val (x, y) = if (cursor.visibility == View.VISIBLE) cursorCenter() else Pair(root.width / 2f, root.height / 2f)
        browser.dispatchScroll(x, y, direction * SCROLL_LINES)
    }

    override fun onBackPressed() {
        if (servicesPanel.visibility == View.VISIBLE) servicesPanel.visibility = View.GONE
        else if (!browser.back()) super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tiltKey = 0
        voice.cancel()
        browser.detachSurface() // keep playing; the session's browser outlives this screen
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()


    private companion object {
        const val TOOLBAR_HIDE_MS = 5000L
        const val BUTTON_DP = 40
        const val EDGE_DP = 28 // left strip reserved for the toolbar gesture
        const val EDGE_SWIPE_DP = 24
        const val CURSOR_DP = 28
        const val CURSOR_STEP_DP = 18
        const val CURSOR_HIDE_MS = 6000L
        const val SCROLL_LINES = 3f
        const val TILT_HOLD_MS = 350L   // hold this long before the ring starts gliding
        const val TILT_REPEAT_MS = 50L
        // Glide speed: GLIDE_STEP_DP per tick (~240 dp/s), ramping to GLIDE_MAX_FACTOR× (~720 dp/s) over ~1 s.
        const val GLIDE_STEP_DP = 12
        const val GLIDE_RAMP_STEPS = 10f
        const val GLIDE_MAX_FACTOR = 3f
        val TILT_DIRS = mapOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP to Pair(0f, -1f),
            android.view.KeyEvent.KEYCODE_DPAD_DOWN to Pair(0f, 1f),
            android.view.KeyEvent.KEYCODE_DPAD_LEFT to Pair(-1f, 0f),
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT to Pair(1f, 0f),
            android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT to Pair(-1f, -1f),
            android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT to Pair(1f, -1f),
            android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT to Pair(-1f, 1f),
            android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT to Pair(1f, 1f),
        )
        const val TRACKPAD_GAIN = 2f // fallback when the touchpad doesn't report its size
        val KNOB_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT, android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT, android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            android.view.KeyEvent.KEYCODE_SOFT_LEFT, android.view.KeyEvent.KEYCODE_SOFT_RIGHT,
            android.view.KeyEvent.KEYCODE_NAVIGATE_NEXT, android.view.KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
            android.view.KeyEvent.KEYCODE_TAB, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
        )
        val TILE_COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFF3949AB.toInt(), 0xFF00897B.toInt(),
            0xFF43A047.toInt(), 0xFFF4511E.toInt(), 0xFF6D4C41.toInt(), 0xFF1E88E5.toInt(),
        )
    }
}

/** Android Auto binds this to show [KiteCarActivity] (listed in the AA launcher as a projection app). */
class KiteProjectionService : CarActivityService() {
    override fun getCarActivity(): Class<out CarActivity> = KiteCarActivity::class.java

    override fun onDestroy() {
        android.util.Log.i("KiteAuto", "Projection service destroyed")
        BrowserDisplay.releaseShared() // Android Auto session over: stop the page
        super.onDestroy()
    }
}
