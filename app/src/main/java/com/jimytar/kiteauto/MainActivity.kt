package com.jimytar.kiteauto

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jimytar.kiteauto.data.CarZoom
import com.jimytar.kiteauto.data.Service
import com.jimytar.kiteauto.data.Services

/** Phone-side setup: edit the streaming services shown in the car, open them on the phone to log in. */
class MainActivity : Activity() {
    private lateinit var services: Services
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        services = Services(this)
        val pad = dp(16)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = getString(R.string.setup_help)
                setPadding(0, 0, 0, pad)
            })
            addView(zoomRow())
            addView(Button(context).apply {
                text = "Allow keyboard pop-up for the car"
                setOnClickListener {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName"))
                    )
                }
                tag = "overlay"
            })
            addView(list)
            addView(Button(context).apply {
                text = "Add service"
                setOnClickListener { edit(-1) }
            })
        }
        root.fitsSystemWindows = true
        setContentView(ScrollView(this).apply { addView(root) })
        render()
        // Typing for the car arrives as a notification; the car toolbar's mic needs the microphone.
        requestPermissions(
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS, android.Manifest.permission.RECORD_AUDIO), 1
        )
    }

    /** "Car page zoom: 73%  [−] [+]" — applies the next time KiteAuto opens on the car screen. */
    private fun zoomRow(): LinearLayout {
        val zoom = CarZoom(this)
        val label = TextView(this).apply { textSize = 16f }
        fun show() { label.text = "Car page zoom: ${zoom.percent}%" }
        show()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "−"
                setOnClickListener { zoom.percent -= 1; show() }
            })
            addView(Button(context).apply {
                text = "+"
                setOnClickListener { zoom.percent += 1; show() }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // The pop-up button is only needed until "Display over other apps" is granted.
        window.decorView.findViewWithTag<View>("overlay")?.visibility =
            if (android.provider.Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
    }

    private fun render() {
        list.removeAllViews()
        services.load().forEachIndexed { i, s ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "${s.name}\n${s.url}"
                    textSize = 16f
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(context).apply {
                    text = "Open"
                    setOnClickListener {
                        startActivity(Intent(context, PhoneBrowserActivity::class.java).putExtra("url", s.url))
                    }
                })
                addView(Button(context).apply {
                    text = "Edit"
                    setOnClickListener { edit(i) }
                })
            })
        }
    }

    /** Add (index -1) or edit/delete a service. */
    private fun edit(index: Int) {
        val current = services.load()
        val existing = current.getOrNull(index)
        val name = EditText(this).apply { hint = "Name"; setText(existing?.name) }
        val url = EditText(this).apply {
            hint = "https://…"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(existing?.url)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(name)
            addView(url)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add service" else "Edit service")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val u = url.text.toString().trim()
                if (n.isEmpty() || u.isEmpty()) return@setPositiveButton
                val s = Service(n, Services.toUrl(u))
                services.save(if (existing == null) current + s else current.toMutableList().also { it[index] = s })
                render()
            }
            .setNegativeButton("Cancel", null)
        if (existing != null) {
            dialog.setNeutralButton("Delete") { _, _ ->
                services.save(current.filterIndexed { i, _ -> i != index })
                render()
            }
        }
        dialog.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
