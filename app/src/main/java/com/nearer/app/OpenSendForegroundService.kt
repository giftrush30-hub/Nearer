package com.nearer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps an explicitly user-started OpenSend session visible and less likely to be
 * stopped while the sender switches apps or locks the screen.
 *
 * The HTTP server and hotspot are still owned by MainActivity in this first pass;
 * this service is the foreground execution/liveness companion, not a transfer daemon.
 */
class OpenSendForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "nearer_opensend"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.nearer.app.OPENSEND_START"
        private const val ACTION_STOP = "com.nearer.app.OPENSEND_STOP"

        fun start(context: Context) {
            val intent = Intent(context, OpenSendForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OpenSendForegroundService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "NeaRer OpenSend", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when your phone is sharing files over its temporary local Wi-Fi network."
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val openApp = PendingIntent.getActivity(
            this,
            4103,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("NeaRer is sharing files")
            .setContentText("Your temporary local Wi-Fi download page is active.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
