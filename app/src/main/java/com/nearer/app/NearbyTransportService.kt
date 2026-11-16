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
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns Nearby Connections independently of MainActivity. Incoming messages are
 * durably staged before they can be acknowledged; the Activity drains that
 * inbox and removes each event only after its existing chat/community storage
 * callback confirms a successful commit.
 */
class NearbyTransportService : Service() {
    inner class LocalBinder : Binder() {
        fun transport(): NearbyConnectionsTransport = nearbyTransport

        fun attachUiListener(listener: NearbyConnectionsTransport.Listener) {
            uiListener = listener
            nearbyTransport.setListener(serviceListener)
            nearbyTransport.discoveredPeersSnapshot().forEach { peer ->
                listener.onFound(peer.endpointId, peer.displayName, peer.stablePeerId)
            }
            nearbyTransport.connectedPeersSnapshot().forEach { peer ->
                listener.onConnected(peer.endpointId, peer.displayName, peer.stablePeerId)
            }
            lastStatus?.let(listener::onStatus)
            inbox.snapshot().forEach(::dispatchInboxEntry)
        }

        fun detachUiListener(listener: NearbyConnectionsTransport.Listener) {
            if (uiListener === listener) {
                uiListener = null
                inFlightMessageIds.clear()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "nearer_nearby_connection"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "com.nearer.app.NEARBY_START"

        fun start(context: Context) {
            context.getSharedPreferences("nearer_local", Context.MODE_PRIVATE).edit()
                .putBoolean("nearby_service_active", true)
                .commit()
            ContextCompat.startForegroundService(
                context,
                Intent(context, NearbyTransportService::class.java).setAction(ACTION_START)
            )
        }
    }

    private val binder = LocalBinder()
    private lateinit var nearbyTransport: NearbyConnectionsTransport
    private lateinit var inbox: NearbyIncomingMessageInbox
    @Volatile private var uiListener: NearbyConnectionsTransport.Listener? = null
    @Volatile private var lastStatus: String? = null
    private val inFlightMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val acknowledgementCallbacks = ConcurrentHashMap<String, () -> Unit>()

    private val serviceListener = object : NearbyConnectionsTransport.ListenerAdapter() {
        override fun onStatus(message: String) {
            lastStatus = message
            uiListener?.onStatus(message)
        }

        override fun onFound(id: String, name: String, stableId: String) {
            uiListener?.onFound(id, name, stableId)
        }

        override fun onLost(id: String, stableId: String) {
            uiListener?.onLost(id, stableId)
        }

        override fun onRequest(id: String, name: String, token: String) {
            val peerId = nearbyTransport.stableIdentityForEndpoint(id)
            val listener = uiListener
            if (listener != null) {
                listener.onRequest(id, name, token)
            } else if (TrustedPeerKeyStore.hasPinnedPeer(applicationContext, peerId)) {
                // Previously trusted peers may reconnect in the background. A
                // first-time peer still requires the Activity's in-person code check.
                nearbyTransport.accept(id)
            }
        }

        override fun onConnected(id: String, name: String, stableId: String) {
            uiListener?.onConnected(id, name, stableId)
        }

        override fun onDisconnected(id: String, stableId: String, name: String) {
            uiListener?.onDisconnected(id, stableId, name)
        }

        override fun onMessage(
            id: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit
        ) {
            onMessageWithReply(id, senderName, messageId, body, null, null, null, acknowledge)
        }

        override fun onMessageWithReply(
            id: String,
            senderName: String,
            messageId: String,
            body: String,
            replyToMessageId: String?,
            replyToPreview: String?,
            replyToSender: String?,
            acknowledge: () -> Unit
        ) {
            val peerId = nearbyTransport.stableIdentityForEndpoint(id)
            if (peerId.isBlank()) return
            val entry = NearbyIncomingMessageInbox.Entry(
                messageId, peerId, senderName, body,
                replyToMessageId, replyToPreview, replyToSender, mesh = false
            )
            if (!inbox.put(entry)) {
                uiListener?.onStatus("NeaRer could not securely save an incoming message for later delivery. The sender has not been acknowledged.")
                return
            }
            acknowledgementCallbacks[messageId] = acknowledge
            dispatchInboxEntry(entry)
        }

        override fun onMeshMessage(
            originPeerId: String, senderName: String, messageId: String, body: String, acknowledge: () -> Unit
        ) {
            onMeshMessageWithReply(originPeerId, senderName, messageId, body, null, null, null, acknowledge)
        }

        override fun onMeshMessageWithReply(
            originPeerId: String,
            senderName: String,
            messageId: String,
            body: String,
            replyToMessageId: String?,
            replyToPreview: String?,
            replyToSender: String?,
            acknowledge: () -> Unit
        ) {
            val entry = NearbyIncomingMessageInbox.Entry(
                messageId, originPeerId, senderName, body,
                replyToMessageId, replyToPreview, replyToSender, mesh = true
            )
            if (!inbox.put(entry)) {
                uiListener?.onStatus("NeaRer could not securely save an incoming nearby message. Delivery was not acknowledged.")
                return
            }
            acknowledgementCallbacks[messageId] = acknowledge
            dispatchInboxEntry(entry)
        }

        override fun onMessageStatus(messageId: String, status: String) {
            uiListener?.onMessageStatus(messageId, status)
        }

        override fun onMeshRoutesChanged(recipients: List<MeshRecipient>) {
            uiListener?.onMeshRoutesChanged(recipients)
        }

        override fun onFileProgress(
            id: String, payloadId: Long, name: String, transferred: Long, total: Long, incoming: Boolean
        ) {
            uiListener?.onFileProgress(id, payloadId, name, transferred, total, incoming)
        }

        override fun onFileTransferUpdate(
            id: String,
            payloadId: Long,
            name: String,
            transferred: Long,
            total: Long,
            incoming: Boolean,
            snapshot: TransferSpeedSnapshot
        ) {
            uiListener?.onFileTransferUpdate(id, payloadId, name, transferred, total, incoming, snapshot)
        }

        override fun onFileReceived(id: String, name: String, file: File) {
            uiListener?.onFileReceived(id, name, file)
            // The file is already in NeaRer's private received directory. If no
            // Activity is attached, restoreReceivedFiles() discovers it on rebind.
        }

        override fun onFileStatus(message: String) {
            uiListener?.onFileStatus(message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        inbox = NearbyIncomingMessageInbox(applicationContext)
        createNotificationChannel()
        val preferences = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val ultraSpeedMode = preferences.getBoolean("ultra_speed_mode", false)
        nearbyTransport = NearbyConnectionsTransport(
            applicationContext,
            ultraSpeedMode,
            serviceListener
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this,
            4104,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("NeaRer nearby is ready")
            .setContentText("Nearby discovery and message recovery can continue while you use other apps.")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
                .getBoolean("nearby_service_active", false)) {
            runCatching { nearbyTransport.start() }
                .onFailure { lastStatus = "Nearby background service could not start discovery: ${it.localizedMessage ?: "check permissions"}" }
        }
        return START_STICKY
    }

    private fun dispatchInboxEntry(entry: NearbyIncomingMessageInbox.Entry) {
        val listener = uiListener ?: return
        if (!inFlightMessageIds.add(entry.messageId)) return
        val acknowledgeAfterUiCommit = {
            val removed = inbox.remove(entry.messageId)
            if (removed) {
                val originalAck = acknowledgementCallbacks.remove(entry.messageId)
                if (originalAck != null) {
                    originalAck()
                } else {
                    nearbyTransport.acknowledgePersistedIncomingMessage(entry.peerId, entry.messageId)
                }
            }
            inFlightMessageIds.remove(entry.messageId)
        }
        try {
            if (entry.mesh) {
                listener.onMeshMessageWithReply(
                    entry.peerId, entry.senderName, entry.messageId, entry.body,
                    entry.replyToMessageId, entry.replyToPreview, entry.replyToSender,
                    acknowledgeAfterUiCommit
                )
            } else {
                // Passing the stable ID is intentional: stableIdentityForEndpoint()
                // accepts it when replaying a persisted event after process death.
                listener.onMessageWithReply(
                    entry.peerId, entry.senderName, entry.messageId, entry.body,
                    entry.replyToMessageId, entry.replyToPreview, entry.replyToSender,
                    acknowledgeAfterUiCommit
                )
            }
        } catch (_: Exception) {
            inFlightMessageIds.remove(entry.messageId)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "NeaRer nearby connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your approved nearby connection available and protects incoming message recovery."
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        uiListener = null
        inFlightMessageIds.clear()
        acknowledgementCallbacks.clear()
        nearbyTransport.stop()
        super.onDestroy()
    }
}
