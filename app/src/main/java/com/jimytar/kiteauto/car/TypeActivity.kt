package com.jimytar.kiteauto.car

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import com.jimytar.kiteauto.R

/**
 * Small phone window that opens with the keyboard up when a text field is focused on the car screen
 * ([PhoneTextInput]). Go sends the text to the car page; the mic button dictates.
 */
class TypeActivity : Activity() {
    private lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phone in the car: screen may be off. Wake it, show over the lock screen and dismiss the keyguard
        // (instant while Smart Lock keeps the phone unlocked; otherwise Android asks for the PIN).
        setTurnScreenOn(true)
        setShowWhenLocked(true)
        getSystemService(android.app.KeyguardManager::class.java).requestDismissKeyguard(this, null)
        getSystemService(NotificationManager::class.java).cancel(PhoneTextInput.NOTIFICATION_ID)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        field = EditText(this).apply {
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_GO || enter) { send(text.toString()); true } else false
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_mic)
                setOnClickListener { dictate() }
            })
        })
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.TOP)
        applyHint(intent)
        field.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        getSystemService(NotificationManager::class.java).cancel(PhoneTextInput.NOTIFICATION_ID)
        applyHint(intent)
    }

    private fun applyHint(intent: Intent) {
        title = "Type for the car"
        field.hint = intent.getStringExtra(EXTRA_HINT).orEmpty().ifBlank { "Search or URL" }
    }

    private fun dictate() {
        runCatching {
            startActivityForResult(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH),
                REQUEST_SPEECH,
            )
        }
    }

    @Deprecated("Activity result API needs androidx.activity; not worth the dependency here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH || resultCode != RESULT_OK) return
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::send)
    }

    private fun send(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) BrowserDisplay.current?.typeOrSearch(t)
        finish()
    }

    companion object {
        const val EXTRA_HINT = "hint"
        private const val REQUEST_SPEECH = 1
    }
}
