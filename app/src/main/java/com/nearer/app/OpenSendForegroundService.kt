package com.nearer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns the complete sender-side OpenSend session. The service, not MainActivity,
 * holds the hotspot reservation and HTTP server so the share survives Activity
 * recreation and the user can switch apps while the notification remains visible.
 */
class OpenSendForegroundService : Service() {
    interface Listener {
        fun onOpenSendStateChanged(session: OpenSendSession?, message: String?)
    }

    inner class LocalBinder : Binder() {
        fun addListener(listener: Listener) {
            listeners.add(listener)
            listener.onOpenSendStateChanged(currentSession, currentMessage)
        }

        fun removeListener(listener: Listener) {
            listeners.remove(listener)
        }

        fun startSession(files: List<OpenSendFile>) = this@OpenSendForegroundService.startSession(files)
        fun stopSession() = this@OpenSendForegroundService.stopSession()
        fun currentSession(): OpenSendSession? = currentSession
    }

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
            val intent = Intent(context, OpenSendForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private val listeners = CopyOnWriteArraySet<Listener>()
    @Volatile private var sessionManager: OpenSendSessionManager? = null
    @Volatile private var currentSession: OpenSendSession? = null
    @Volatile private var currentMessage: String? = null

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
            stopSession()
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

    private fun startSession(files: List<OpenSendFile>) {
        require(files.isNotEmpty()) { "Choose at least one file to share." }
        sessionManager?.close()
        currentSession = null
        currentMessage = "Starting your temporary local Wi-Fi sharing network…"
        publishState()

        val manager = OpenSendSessionManager(applicationContext)
        sessionManager = manager
        manager.start(
            files = files,
            onReady = { session ->
                if (sessionManager !== manager) return@start
                currentSession = session
                currentMessage = "OpenSend is ready. Share the Wi-Fi details and download link with the receiver."
                publishState()
            },
            onFailure = { message ->
                if (sessionManager !== manager) return@start
                manager.close()
                sessionManager = null
                currentSession = null
                currentMessage = message
                publishState()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        )
    }

    private fun stopSession() {
        sessionManager?.close()
        sessionManager = null
        currentSession = null
        currentMessage = "Sharing stopped. The temporary download address is no longer available."
        publishState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishState() {
        listeners.forEach { listener ->
            try { listener.onOpenSendStateChanged(currentSession, currentMessage) } catch (_: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessionManager?.close()
        sessionManager = null
        currentSession = null
        listeners.clear()
        super.onDestroy()
    }
}
