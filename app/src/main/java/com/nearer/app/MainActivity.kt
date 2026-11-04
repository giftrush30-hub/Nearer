package com.nearer.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private val Midnight = Color(0xFF101A2B)
private val Panel = Color(0xFF1A2940)
private val Mint = Color(0xFF69E0C0)
private val Muted = Color(0xFFA8B6C9)
private val White = Color(0xFFF6F8FC)

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var scanning by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Ready to find nearby Android phones.")
    private val nearbyDevices = mutableStateListOf<WifiP2pDevice>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        scanning = false
                        statusMessage = "Turn on Wi-Fi to discover nearby phones."
                    } else {
                        statusMessage = "Wi-Fi Direct is ready. Tap Find people nearby."
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (hasDiscoveryPermission()) {
                        wifiManager.requestPeers(channel) { peers ->
                            nearbyDevices.clear()
                            nearbyDevices.addAll(peers.deviceList)
                            scanning = false
                            statusMessage = if (nearbyDevices.isEmpty()) {
                                "No NeaRer-compatible devices found yet. Keep both phones nearby and scan again."
                            } else {
                                "${nearbyDevices.size} nearby device(s) found. Connection approval comes next."
                            }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                    scanning = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    if (scanning) statusMessage = "Searching nearby…"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiManager.initialize(this, mainLooper, null)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                primary = Mint,
                onPrimary = Midnight,
                onBackground = White,
                onSurface = White
            )) {
                NeaRerHome(
                    status = statusMessage,
                    scanning = scanning,
                    devices = nearbyDevices,
                    onFindNearby = ::startDiscovery
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    private fun hasDiscoveryPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startDiscovery() {
        if (!hasDiscoveryPermission()) {
            val permissions = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            ActivityCompat.requestPermissions(this, permissions, REQUEST_DISCOVERY_PERMISSION)
            statusMessage = "Allow nearby-device permission, then tap Find people nearby again."
            return
        }

        val activeChannel = channel ?: run {
            statusMessage = "The nearby connection service is not ready. Please reopen NeaRer."
            return
        }

        scanning = true
        statusMessage = "Starting nearby search…"
        wifiManager.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                statusMessage = "Search started. Looking for nearby Wi-Fi Direct devices…"
            }

            override fun onFailure(reason: Int) {
                scanning = false
                statusMessage = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi is busy. Wait a moment and try again."
                    WifiP2pManager.ERROR -> "Wi-Fi Direct reported an error. Check that Wi-Fi is on."
                    WifiP2pManager.P2P_UNSUPPORTED -> "This phone does not support Wi-Fi Direct."
                    else -> "Could not start discovery (code $reason). Check Wi-Fi and try again."
                }
            }
        })
    }

    companion object {
        private const val REQUEST_DISCOVERY_PERMISSION = 410
    }
}

@androidx.compose.runtime.Composable
private fun NeaRerHome(
    status: String,
    scanning: Boolean,
    devices: List<WifiP2pDevice>,
    onFindNearby: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Midnight) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NeaRer", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Your people. Nearby. Offline.", color = Muted, fontSize = 13.sp)
                }
                Surface(shape = CircleShape, color = Panel, modifier = Modifier.size(46.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Text("N", color = Mint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = Panel) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("OFFLINE CONNECTION", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Bring nearby people closer.", color = White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Find nearby Android phones using Wi-Fi Direct. Discovery happens locally and does not need mobile data.",
                        color = Muted, fontSize = 14.sp, lineHeight = 21.sp
                    )
                    Button(
                        onClick = onFindNearby,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !scanning,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text(if (scanning) "Searching nearby…" else "Find people nearby", fontWeight = FontWeight.Bold)
                    }
                    Text(status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEARBY DEVICES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("${devices.size} found", color = Mint, fontSize = 11.sp)
            }

            if (devices.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Panel) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No devices listed yet", color = White, fontWeight = FontWeight.SemiBold)
                        Text("Try scanning with another Android phone nearby with Wi-Fi turned on.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(devices, key = { it.deviceAddress }) { device ->
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Panel) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color(0xFF244B4A), modifier = Modifier.size(38.dp)) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Text("N", color = Mint, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(device.deviceName.ifBlank { "Nearby Android device" }, color = White, fontWeight = FontWeight.SemiBold)
                                    Text(deviceStatus(device.status), color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            Text("NEARER  •  DIRECT DEVICE DISCOVERY", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

private fun deviceStatus(status: Int): String = when (status) {
    WifiP2pDevice.AVAILABLE -> "Available"
    WifiP2pDevice.INVITED -> "Connection invitation pending"
    WifiP2pDevice.CONNECTED -> "Connected"
    WifiP2pDevice.FAILED -> "Connection failed"
    WifiP2pDevice.UNAVAILABLE -> "Temporarily unavailable"
    else -> "Nearby device"
}
