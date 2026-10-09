package com.avinash.yatramitra.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.avinash.yatramitra.MainActivity
import com.avinash.yatramitra.R

/** Shows trip notifications (Group chat, trip started, new expense, someone joined) on this phone. */
object Notifications {
    const val CHANNEL_ID = "trip_updates"

    /** SOS alerts get their own channel so people can keep them loud even if they quieten the rest. */
    const val SOS_CHANNEL_ID = "sos_alerts"

    /** Intent extra carrying the trip code to open when a notification is tapped. */
    const val EXTRA_OPEN_TRIP = "com.avinash.yatramitra.OPEN_TRIP"

    /** Intent extra naming the tab to show in that trip ("chat" for a Group chat message). */
    const val EXTRA_OPEN_TAB = "com.avinash.yatramitra.OPEN_TAB"

    /** The trip whose Group chat is on screen right now, if any: its chat messages aren't
     *  shown as notifications, since they're already in front of the user. */
    @Volatile
    var visibleChatTripCode: String? = null

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Trip updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Group chat messages, trip started, new expenses and people joining your trips"
        }
        val sos = NotificationChannel(SOS_CHANNEL_ID, "SOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Someone on your trip needs help (accident, breakdown, medical, police, fuel, cash)"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600)
        }
        context.getSystemService(NotificationManager::class.java)?.let {
            it.createNotificationChannel(channel)
            it.createNotificationChannel(sos)
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canPost() just above the notify() call
    fun show(context: Context, tripCode: String, type: String, title: String, body: String) {
        if (!canPost(context)) return
        // An SOS always shows, even with that chat open.
        if (type == "chat" && visibleChatTripCode.equals(tripCode, ignoreCase = true)) return
        val isSos = type == "sos"
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TRIP, tripCode)
            if (type == "chat" || isSos) putExtra(EXTRA_OPEN_TAB, "chat")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapAction = PendingIntent.getActivity(
            context,
            "$tripCode/$type".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, if (isSos) SOS_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (type == "chat" || isSos) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            .setColor(if (isSos) 0xFFD32F2F.toInt() else 0xFFF97316.toInt())
            .setAutoCancel(true)
            .setContentIntent(tapAction)
            .build()
        // One notification per trip and kind: a newer chat message replaces the older one instead
        // of stacking up dozens. Every SOS stays, though.
        val id = if (isSos) "$tripCode/sos/${System.currentTimeMillis()}".hashCode() else "$tripCode/$type".hashCode()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission was withdrawn between the check and the call; nothing to show.
        }
    }
}
