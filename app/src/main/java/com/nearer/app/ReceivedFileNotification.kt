package com.nearer.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Posts a truthful completion notification only when Android has granted permission. */
internal object ReceivedFileNotification {
    private const val CHANNEL_ID = "nearer_received_files"
    private const val CHANNEL_NAME = "Received files"

    fun show(context: Context, displayName: String, sizeBytes: Long): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false

        return runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Alerts when a nearby file has finished receiving"
                    }
                )
            }
            val openApp = PendingIntent.getActivity(
                context,
                4107,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val sizeLabel = when {
                sizeBytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
                sizeBytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                sizeBytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", sizeBytes / 1024.0)
                else -> "$sizeBytes B"
            }
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("File received")
                .setContentText("$displayName • $sizeLabel")
                .setStyle(Notification.BigTextStyle().bigText("$displayName has been received and verified. Size: $sizeLabel"))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .build()
            manager.notify((displayName.hashCode() and 0x7fffffff), notification)
            true
        }.getOrDefault(false)
    }
}
