package com.jimytar.kiteauto.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.jimytar.kiteauto.R

/**
 * Typing for the car screen from the phone. With "Display over other apps" granted (lets a background app open a
 * window) the phone opens [TypeActivity] with the keyboard up; the heads-up notification with an inline reply
 * field is posted as well, as the fallback (TypeActivity cancels it). Text goes into the car page's focused field,
 * or runs as a search ([BrowserDisplay.typeOrSearch]).
 */
object PhoneTextInput {
    private const val CHANNEL = "car_text"
    const val NOTIFICATION_ID = 1
    private const val KEY_TEXT = "text"

    /** Ask the phone for text; [hint] is the field's placeholder (shown as the reply hint). */
    fun request(context: Context, hint: String) {
        if (android.provider.Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    Intent(context, TypeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(TypeActivity.EXTRA_HINT, hint)
                )
            }.onFailure { android.util.Log.w("KiteAuto", "Typing window: $it") }
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Typing for the car", NotificationManager.IMPORTANCE_HIGH)
        )
        val label = hint.ifBlank { "Search or URL" }
        val reply = PendingIntent.getBroadcast(
            context, 0, Intent(context, Receiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // mutable: RemoteInput fills it
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_search), "Type", reply
        ).addRemoteInput(RemoteInput.Builder(KEY_TEXT).setLabel(label).build()).build()
        nm.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_search)
                .setContentTitle("KiteAuto: type for the car")
                .setContentText(label)
                .addAction(action)
                .setAutoCancel(true)
                .setTimeoutAfter(120_000)
                .build()
        )
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim()
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            if (!text.isNullOrEmpty()) BrowserDisplay.current?.typeOrSearch(text)
        }
    }
}
