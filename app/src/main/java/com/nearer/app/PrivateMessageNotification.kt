package com.nearer.app

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Posts a generic private alert after a new encrypted inbox entry is committed. */
internal object PrivateMessageNotification {
    private const val CHANNEL_ID = "nearer_private_messages"

    fun show(context: Context, messageId: String, isNewMessage: Boolean, inboxPersisted: Boolean): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processForeground = activityManager?.runningAppProcesses.orEmpty().any { process ->
            process.processName == context.packageName &&
                process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        if (!ChatNotificationPolicy.shouldNotify(processForeground, isNewMessage, inboxPersisted)) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        return runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "NeaRer messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Private alerts for newly received nearby messages"
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
                )
            }
            val notificationId = ChatNotificationPolicy.notificationId(messageId)
            val openApp = PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("New message on NeaRer")
                .setContentText("Open NeaRer to read your message")
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            manager.notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }
}
