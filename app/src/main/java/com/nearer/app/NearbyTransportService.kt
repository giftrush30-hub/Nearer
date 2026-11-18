package com.nearer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.bluetooth.BluetoothManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns Nearby Connections independently of MainActivity. Incoming messages are
 * durably staged before they can be acknowledged; the Activity drains that
 * inbox and removes each event only after its existing chat/community storage
 * callback confirms a successful commit.
 */
data class WifiDirectPeerSnapshot(val name: String, val address: String, val status: Int)

/** UI callbacks for the single service-owned Wi-Fi Direct control plane. */
interface WifiDirectUiListener {
    fun onStateChanged(state: String, detail: String)
    fun onPeersChanged(peers: List<WifiDirectPeerSnapshot>)
    fun onLinkReady(isGroupOwner: Boolean, groupOwnerAddress: java.net.InetAddress)
}

class NearbyTransportService : Service() {
    inner class LocalBinder : Binder() {
        fun transport(): NearbyConnectionsTransport = nearbyTransport

        fun sendHyperTransferTcpFile(endpointId: String, uri: android.net.Uri, displayName: String): Long? {
            val peerId = nearbyTransport.stableIdentityForEndpoint(endpointId)
            return hyperTransferTcpManager?.sendFile(endpointId, peerId, uri, displayName)
        }

        fun cancelHyperTransferTcpFile(payloadId: Long) {
            hyperTransferTcpManager?.cancel(payloadId)
        }

        internal fun sendHyperTransferTcpOffer(endpointId: String, offer: HyperTransferTcpOfferCodec.Offer): Boolean {
            val peerId = nearbyTransport.stableIdentityForEndpoint(endpointId)
            if (hyperTransferApprovals.recordOutgoingOffer(endpointId, peerId, offer) != HyperTransferApprovalLedger.OfferResult.ADDED) return false
            val sent = nearbyTransport.sendHyperTransferTcpOffer(endpointId, offer)
            if (!sent) hyperTransferApprovals.removeOffer(endpointId, peerId, offer.transferId, HyperTransferApprovalLedger.Direction.OUTGOING)
            return sent
        }

        fun respondToHyperTransferTcpOffer(endpointId: String, transferId: String, accepted: Boolean): Boolean {
            val peerId = nearbyTransport.stableIdentityForEndpoint(endpointId)
            return hyperTransferTcpManager?.decideIncoming(endpointId, peerId, transferId, accepted) == true
        }

        /** One-shot approval for the exact offer; callers must still authenticate the socket identity. */
        internal fun consumeHyperTransferApproval(
            endpointId: String,
            peerId: String,
            offer: HyperTransferTcpOfferCodec.Offer,
            outgoing: Boolean
        ): Boolean = hyperTransferApprovals.consumeApproval(
            endpointId, peerId, offer,
            if (outgoing) HyperTransferApprovalLedger.Direction.OUTGOING else HyperTransferApprovalLedger.Direction.INCOMING
        )

        fun attachWifiDirectUiListener(listener: WifiDirectUiListener) {
            wifiDirectUiListener = listener
            listener.onStateChanged(wifiDirectState, wifiDirectDetail)
            listener.onPeersChanged(wifiDirectPeers)
            wifiDirectLink?.let { listener.onLinkReady(it.isGroupOwner, it.groupOwnerAddress) }
        }

        fun detachWifiDirectUiListener(listener: WifiDirectUiListener) {
            if (wifiDirectUiListener === listener) wifiDirectUiListener = null
        }

        fun startWifiDirect() {
            val coordinator = ensureWifiDirectCoordinator()
            if (!wifiDirectStarted) wifiDirectStarted = coordinator.start()
        }

        fun discoverWifiDirectPeers() {
            startWifiDirect()
            if (wifiDirectStarted) wifiDirectCoordinator?.discoverPeers()
        }

        fun connectWifiDirectPeer(address: String) {
            startWifiDirect()
            if (wifiDirectStarted) wifiDirectCoordinator?.connectToPeer(address)
        }

        fun stopWifiDirectDiscovery() {
            wifiDirectCoordinator?.stopDiscovery()
        }

        fun disconnectWifiDirectGroup() {
            wifiDirectCoordinator?.removeGroup()
        }

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
            replayInboxEntries()
        }

        fun detachUiListener(listener: NearbyConnectionsTransport.Listener) {
            if (uiListener === listener) {
                uiListener = null
                inFlightMessageIds.clear()
            }
        }

        fun replayPendingMessages() {
            replayInboxEntries()
        }
    }

    companion object {
        private const val CHANNEL_ID = "nearer_nearby_connection"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "com.nearer.app.NEARBY_START"

        // Commit before starting the service so restart recovery sees the active flag.
        @Suppress("ApplySharedPref")
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
    /** Approval state lives with the foreground service, not the Activity lifecycle. */
    private val hyperTransferApprovals = HyperTransferApprovalLedger()
    @Volatile private var hyperTransferTcpManager: HyperTransferTcpSessionManager? = null
    @Volatile private var wifiDirectUiListener: WifiDirectUiListener? = null
    @Volatile private var wifiDirectCoordinator: WifiDirectConnectionCoordinator? = null
    @Volatile private var wifiDirectStarted = false
    @Volatile private var wifiDirectState = WifiDirectConnectionCoordinator.State.IDLE.name
    @Volatile private var wifiDirectDetail = "Wi-Fi Direct has not been initialized."
    @Volatile private var wifiDirectPeers: List<WifiDirectPeerSnapshot> = emptyList()
    @Volatile private var wifiDirectLink: WifiDirectConnectionCoordinator.Link? = null

    private fun ensureWifiDirectCoordinator(): WifiDirectConnectionCoordinator {
        wifiDirectCoordinator?.let { return it }
        return synchronized(this) {
            wifiDirectCoordinator ?: WifiDirectConnectionCoordinator(
                applicationContext,
                object : WifiDirectConnectionCoordinator.Listener {
                    override fun onStateChanged(state: WifiDirectConnectionCoordinator.State, detail: String) {
                        wifiDirectState = state.name
                        wifiDirectDetail = detail
                        if (state == WifiDirectConnectionCoordinator.State.DISCONNECTED ||
                            state == WifiDirectConnectionCoordinator.State.FAILED ||
                            state == WifiDirectConnectionCoordinator.State.CLOSED) {
                            wifiDirectLink = null
                        }
                        wifiDirectUiListener?.onStateChanged(state.name, detail)
                    }

                    override fun onPeersChanged(peers: List<WifiDirectConnectionCoordinator.Peer>) {
                        val snapshot = peers.map { WifiDirectPeerSnapshot(it.name, it.address, it.status) }
                        wifiDirectPeers = snapshot
                        wifiDirectUiListener?.onPeersChanged(snapshot)
                    }

                    override fun onLinkReady(link: WifiDirectConnectionCoordinator.Link) {
                        wifiDirectLink = link
                        hyperTransferTcpManager?.onLinkReady()
                        wifiDirectUiListener?.onLinkReady(link.isGroupOwner, link.groupOwnerAddress)
                    }
                }
            ).also { wifiDirectCoordinator = it }
        }
    }

    private fun inboxKey(peerId: String, messageId: String): String = "${NeaRerIdentity.normalize(peerId).orEmpty()}|$messageId"

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
            hyperTransferTcpManager?.clearEndpoint(id)
            hyperTransferApprovals.clearEndpoint(id)
            uiListener?.onDisconnected(id, stableId, name)
        }

        override fun onMessage(
            id: String, senderName: String, messageId: String, body: String, acknowledge: () -> Boolean
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
            acknowledge: () -> Boolean
        ) {
            if (body.startsWith("NR_TYPING:")) {
                val peerId = nearbyTransport.stableIdentityForEndpoint(id)
                val listener = uiListener
                if (listener != null && peerId.isNotBlank()) {
                    runCatching {
                        listener.onMessageWithReply(
                            peerId, senderName, messageId, body,
                            replyToMessageId, replyToPreview, replyToSender, acknowledge
                        )
                    }.onFailure { runCatching { acknowledge() } }
                } else {
                    runCatching { acknowledge() }
                }
                return
            }
            val peerId = nearbyTransport.stableIdentityForEndpoint(id)
            if (peerId.isBlank()) {
                nearbyTransport.releasePendingIncomingMessage(messageId)
                return
            }
            val entry = NearbyIncomingMessageInbox.Entry(
                messageId, peerId, senderName, body,
                replyToMessageId, replyToPreview, replyToSender, mesh = false
            )
            val isNewInboxEntry = inbox.snapshot().none {
                it.peerId == entry.peerId && it.messageId == entry.messageId
            }
            if (!inbox.put(entry)) {
                nearbyTransport.releasePendingIncomingMessage(peerId, messageId)
                uiListener?.onStatus("NeaRer could not securely save an incoming message for later delivery. The sender has not been acknowledged.")
                return
            }
            // Notify only after durable inbox commit. The text remains generic for lock-screen privacy.
            if (ChatNotificationPolicy.isUserVisibleMessageBody(body)) {
                PrivateMessageNotification.show(applicationContext, messageId, isNewInboxEntry, inboxPersisted = true)
            }
            // The encrypted inbox is the durable delivery boundary; acknowledge only after it commits.
            val dedupeCommitted = runCatching { acknowledge() }.getOrDefault(false)
            val acknowledgementAllowed = NearbyInboxCommitPolicy.canAcknowledge(inboxCommitted = true, dedupeCommitted = dedupeCommitted)
            dispatchInboxEntry(entry, acknowledgementAllowed)
        }

        override fun onMeshMessage(
            originPeerId: String, senderName: String, messageId: String, body: String, acknowledge: () -> Boolean
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
            acknowledge: () -> Boolean
        ) {
            if (body.startsWith("NR_TYPING:")) {
                val listener = uiListener
                if (listener != null) {
                    runCatching {
                        listener.onMeshMessageWithReply(
                            originPeerId, senderName, messageId, body,
                            replyToMessageId, replyToPreview, replyToSender, acknowledge
                        )
                    }.onFailure { runCatching { acknowledge() } }
                } else {
                    runCatching { acknowledge() }
                }
                return
            }
            val entry = NearbyIncomingMessageInbox.Entry(
                messageId, originPeerId, senderName, body,
                replyToMessageId, replyToPreview, replyToSender, mesh = true
            )
            val isNewInboxEntry = inbox.snapshot().none {
                it.peerId == entry.peerId && it.messageId == entry.messageId
            }
            if (!inbox.put(entry)) {
                nearbyTransport.releasePendingIncomingMessage(originPeerId, messageId)
                uiListener?.onStatus("NeaRer could not securely save an incoming nearby message. Delivery was not acknowledged.")
                return
            }
            // Mesh control packets are durable protocol events, not user-visible alerts.
            if (ChatNotificationPolicy.isUserVisibleMessageBody(body)) {
                PrivateMessageNotification.show(applicationContext, messageId, isNewInboxEntry, inboxPersisted = true)
            }
            // The encrypted inbox is the durable delivery boundary; acknowledge only after it commits.
            val dedupeCommitted = runCatching { acknowledge() }.getOrDefault(false)
            dispatchInboxEntry(entry, dedupeCommitted)
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

        override fun onIncomingHyperTransferOffer(
            id: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer
        ) {
            when (hyperTransferApprovals.recordIncomingOffer(id, peerId, offer)) {
                HyperTransferApprovalLedger.OfferResult.ADDED -> hyperTransferTcpManager?.receiveOffer(id, peerId, offer)
                HyperTransferApprovalLedger.OfferResult.DUPLICATE ->
                    uiListener?.onFileStatus("This HyperTransfer offer is already awaiting your decision.")
                HyperTransferApprovalLedger.OfferResult.CONFLICT,
                HyperTransferApprovalLedger.OfferResult.REJECTED ->
                    uiListener?.onFileStatus("A duplicate, conflicting, or excessive HyperTransfer offer was rejected.")
            }
        }

        override fun onHyperTransferOfferDecision(
            id: String, peerId: String, decision: HyperTransferTcpOfferCodec.Decision
        ) {
            if (hyperTransferApprovals.recordRemoteDecision(id, peerId, decision)) {
                hyperTransferTcpManager?.remoteDecision(id, peerId, decision)
                uiListener?.onHyperTransferOfferDecision(id, peerId, decision)
            } else {
                uiListener?.onFileStatus("A stale or mismatched HyperTransfer approval response was ignored.")
            }
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
        hyperTransferTcpManager = HyperTransferTcpSessionManager(
            context = applicationContext,
            coordinator = { wifiDirectCoordinator },
            currentLink = { wifiDirectLink },
            sendOffer = { endpointId, offer ->
                val peerId = nearbyTransport.stableIdentityForEndpoint(endpointId)
                val registered = hyperTransferApprovals.recordOutgoingOffer(endpointId, peerId, offer) ==
                    HyperTransferApprovalLedger.OfferResult.ADDED
                if (!registered) false else {
                    val sent = nearbyTransport.sendHyperTransferTcpOffer(endpointId, offer)
                    if (!sent) hyperTransferApprovals.removeOffer(
                        endpointId, peerId, offer.transferId, HyperTransferApprovalLedger.Direction.OUTGOING
                    )
                    sent
                }
            },
            respondToOffer = { endpointId, transferId, accepted ->
                val peerId = nearbyTransport.stableIdentityForEndpoint(endpointId)
                if (!hyperTransferApprovals.decideIncoming(endpointId, peerId, transferId, accepted)) false
                else {
                    val sent = nearbyTransport.respondToHyperTransferTcpOffer(endpointId, transferId, accepted)
                    if (!sent) hyperTransferApprovals.rollbackIncomingDecision(endpointId, peerId, transferId, accepted)
                    sent
                }
            },
            consumeApproval = { endpointId, peerId, offer, direction ->
                hyperTransferApprovals.consumeApproval(endpointId, peerId, offer, direction)
            },
            listener = object : HyperTransferTcpSessionManager.Listener {
                override fun onIncomingOffer(endpointId: String, peerId: String, offer: HyperTransferTcpOfferCodec.Offer) {
                    uiListener?.onIncomingHyperTransferOffer(endpointId, peerId, offer)
                }
                override fun onProgress(
                    endpointId: String, payloadId: Long, name: String, transferred: Long,
                    total: Long, incoming: Boolean, speed: TransferSpeedSnapshot
                ) {
                    serviceListener.onFileTransferUpdate(endpointId, payloadId, name, transferred, total, incoming, speed)
                }
                override fun onReceived(endpointId: String, name: String, file: File) {
                    serviceListener.onFileReceived(endpointId, name, file)
                }
                override fun onStatus(message: String) {
                    serviceListener.onFileStatus(message)
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this,
            4104,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val preferences = getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        val onboardingComplete = preferences.getBoolean("onboarding_complete", false)
        val permissionsGranted = NearbyPermissionPolicy.runtimePermissions(Build.VERSION.SDK_INT).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        val bluetoothEnabled = runCatching {
            (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        }.getOrDefault(false)
        val wifiEnabled = runCatching {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
        }.getOrDefault(false)
        val locationServicesEnabled = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            runCatching {
                val location = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) location?.isLocationEnabled == true
                else {
                    @Suppress("DEPRECATION")
                    location?.let { it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } == true
                }
            }.getOrDefault(false)
        } else true
        val readyForDiscovery = NearbyServiceStartPolicy.shouldStartDiscovery(
            onboardingComplete = onboardingComplete,
            permissionsGranted = permissionsGranted,
            radioAvailable = SmartConnectPolicy.hasUsableRadio(bluetoothEnabled, wifiEnabled),
            sdkInt = Build.VERSION.SDK_INT,
            locationServicesEnabled = locationServicesEnabled
        )
        val notificationText = if (readyForDiscovery) "Nearby discovery and message recovery can continue while you use other apps." else "Nearby discovery is paused. Open NeaRer to review permissions, Bluetooth and Wi-Fi."
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(if (readyForDiscovery) "NeaRer nearby is ready" else "NeaRer nearby is paused")
            .setContentText(notificationText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, notification)
        if (preferences.getBoolean("nearby_service_active", false) && readyForDiscovery) {
            runCatching { nearbyTransport.start() }.onFailure { error ->
                lastStatus = "Nearby background service could not start discovery: " + (error.localizedMessage ?: "check permissions")
            }
        } else if (!readyForDiscovery) {
            // A permission/radio change must stop new advertisements/discovery,
            // while already-authenticated peer connections can finish safely.
            nearbyTransport.stopSearching()
            lastStatus = when {
                !onboardingComplete -> "Complete NeaRer setup before starting nearby discovery."
                !permissionsGranted -> "Nearby discovery is paused until the required nearby-device permissions are granted."
                !locationServicesEnabled -> "Nearby discovery is paused until Location is enabled on this Android version."
                else -> "Nearby discovery is paused. Turn on Bluetooth or Wi-Fi to continue."
            }
        }
        return START_STICKY
    }

    /**
     * Re-establishes the durable dedupe boundary before replaying an inbox event.
     * Direct messages also retry their ACK when the peer is currently connected.
     * Mesh messages persist the origin-scoped dedupe record; their final receipt
     * remains owned by the mesh receipt queue.
     */
    private fun replayInboxEntries() {
        val pending = inbox.snapshot()
        pending.forEach { inFlightMessageIds.remove(inboxKey(it.peerId, it.messageId)) }
        pending.forEach { entry ->
            val dedupeCommitted = if (entry.mesh) {
                nearbyTransport.persistMeshDeliveryAndReceipt(entry.peerId, entry.messageId)
            } else {
                nearbyTransport.acknowledgePersistedIncomingMessage(entry.peerId, entry.messageId)
            }
            dispatchInboxEntry(entry, dedupeCommitted)
        }
    }

    private fun dispatchInboxEntry(entry: NearbyIncomingMessageInbox.Entry, dedupeCommitted: Boolean = false) {
        val listener = uiListener ?: return
        val key = inboxKey(entry.peerId, entry.messageId)
        if (!inFlightMessageIds.add(key)) return
        val removeAfterUiCommit: () -> Boolean = {
            // A durable dedupe record must exist before the inbox copy can be
            // retired. If that write failed, keep the event for a later replay.
            val removed = NearbyInboxCommitPolicy.canRetireInbox(
                inboxCommitted = true, dedupeCommitted = dedupeCommitted, chatStoreCommitted = true
            ) && inbox.remove(entry.peerId, entry.messageId)
            inFlightMessageIds.remove(key)
            removed
        }
        try {
            if (entry.mesh) {
                listener.onMeshMessageWithReply(
                    entry.peerId, entry.senderName, entry.messageId, entry.body,
                    entry.replyToMessageId, entry.replyToPreview, entry.replyToSender,
                    removeAfterUiCommit
                )
            } else {
                // Passing the stable ID is intentional: stableIdentityForEndpoint()
                // accepts it when replaying a persisted event after process death.
                listener.onMessageWithReply(
                    entry.peerId, entry.senderName, entry.messageId, entry.body,
                    entry.replyToMessageId, entry.replyToPreview, entry.replyToSender,
                    removeAfterUiCommit
                )
            }
        } catch (_: Exception) {
            inFlightMessageIds.remove(key)
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
        wifiDirectUiListener = null
        inFlightMessageIds.clear()
        wifiDirectCoordinator?.close()
        wifiDirectCoordinator = null
        wifiDirectStarted = false
        hyperTransferTcpManager?.close()
        hyperTransferTcpManager = null
        hyperTransferApprovals.clearAll()
        nearbyTransport.stop()
        super.onDestroy()
    }
}
