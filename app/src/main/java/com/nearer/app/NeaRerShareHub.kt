package com.nearer.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import java.util.Locale

private val ShareNavy = Color(0xFF101A2B)
private val SharePanel = Color(0xFF1A2940)
private val ShareBlue = Color(0xFF3478F6)
private val ShareGold = Color(0xFFFFD12D)
private val ShareText = Color(0xFFF6F8FC)
private val ShareMuted = Color(0xFFA8B6C9)

@Composable
fun NeaRerShareHub(
    apps: List<ShareableApp>,
    appsLoading: Boolean,
    appNotice: String,
    onLoadApps: () -> Unit,
    onShareApps: (List<String>) -> Unit,
    onShareAppsToAnyPhone: (List<String>) -> Unit,
    openSendSession: OpenSendSession?,
    openSendNotice: String,
    onPickOpenSendFiles: () -> Unit,
    onStopOpenSend: () -> Unit,
    pendingApp: AppShareArchiveInfo?,
    onInstallPendingApp: () -> Unit
) {
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    val selectedApps = apps.filter { it.packageName in selectedPackages }
    val anyPhoneEligible = selectedApps.isNotEmpty() && selectedApps.all { it.packageFiles.size == 1 }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("NEARER SHARE", color = ShareGold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text("Share with every phone.", color = ShareText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Send files and apps to NeaRer users nearby, or let any phone receive through its browser.", color = ShareMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }

        Card(colors = CardDefaults.cardColors(containerColor = SharePanel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("OpenSend", color = ShareText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("For Android and iPhone phones without NeaRer", color = ShareMuted, fontSize = 11.sp)
                    }
                    Text("NO APP", color = ShareGold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Choose files, then NeaRer creates a temporary local Wi-Fi network and a private download page. The receiver joins the Wi-Fi and opens the link in a browser.", color = ShareMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(
                    onClick = onPickOpenSendFiles,
                    enabled = openSendSession == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ShareBlue)
                ) { Text("Choose files to share") }
                if (openSendSession != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("YOUR SHARE IS LIVE", color = ShareGold, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    Text("Wi-Fi name: ${openSendSession.ssid}", color = ShareText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Password: ${openSendSession.password ?: "No password"}", color = ShareText, fontSize = 13.sp)
                    Text("Download page: ${openSendSession.downloadUrl}", color = ShareBlue, fontSize = 12.sp)
                    Text("${openSendSession.fileCount} file(s) • ${formatShareBytes(openSendSession.totalBytes)}", color = ShareMuted, fontSize = 11.sp)
                    val wifiPayload = if (openSendSession.password.isNullOrBlank()) {
                        "WIFI:T:nopass;S:${openSendSession.ssid};P:;;"
                    } else {
                        "WIFI:T:WPA;S:${openSendSession.ssid};P:${openSendSession.password};;"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            QrCodeTile(wifiPayload)
                            Text("Join Wi-Fi", color = ShareMuted, fontSize = 10.sp)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            QrCodeTile(openSendSession.downloadUrl)
                            Text("Open download page", color = ShareMuted, fontSize = 10.sp)
                        }
                    }
                    Text("The receiver first joins the Wi-Fi network. Then they scan the download-page QR or type the link in their browser. QR scanning does not transfer the file by itself.", color = ShareMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    OutlinedButton(onClick = onStopOpenSend, modifier = Modifier.fillMaxWidth()) { Text("Stop sharing", color = ShareText) }
                }
                Text(openSendNotice, color = ShareMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SharePanel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Share installed apps", color = ShareText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Choose apps already on this Android phone", color = ShareMuted, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onLoadApps, enabled = !appsLoading) {
                        if (appsLoading) CircularProgressIndicator(Modifier.size(16.dp), color = ShareGold, strokeWidth = 2.dp)
                        else Text("Refresh", color = ShareText)
                    }
                }
                Text("Privacy: when you tap Refresh, NeaRer reads installed app names and package details on this phone to build the list. The list stays on this device unless you choose an app to send. NeaRer never copies app accounts, chats, passwords or private app data. APK installation is Android-only; iPhones and iPads cannot install APK files.", color = ShareMuted, fontSize = 11.sp, lineHeight = 16.sp)
                if (apps.isEmpty() && !appsLoading) {
                    Text("Tap Refresh to load apps available for sharing.", color = ShareMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            Row(
                                Modifier.fillMaxWidth().background(ShareNavy, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.packageName in selectedPackages,
                                    onCheckedChange = { checked ->
                                        selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                                    }
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(app.label, color = ShareText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${app.versionName ?: "Version unknown"} • ${app.packageFiles.size} APK part(s) • ${formatShareBytes(app.totalBytes)}", color = ShareMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    Text("${selectedPackages.size} app(s) selected", color = ShareGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (selectedApps.any { it.packageFiles.size > 1 }) {
                        Text("Some selected apps are split into several APK parts. They can be sent as a complete set to another NeaRer phone, but browser-only installation is supported only for single-APK apps.", color = ShareGold, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    Button(
                        onClick = { onShareAppsToAnyPhone(selectedPackages.toList()) },
                        enabled = anyPhoneEligible && openSendSession == null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ShareGold, contentColor = ShareNavy)
                    ) { Text("Send selected apps to any Android phone", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { onShareApps(selectedPackages.toList()) },
                        enabled = selectedPackages.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send to a nearby NeaRer phone", color = ShareText) }
                }
                Text(appNotice, color = ShareMuted, fontSize = 11.sp, lineHeight = 16.sp)
                if (pendingApp != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("APP RECEIVED AND VERIFIED", color = ShareGold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text(pendingApp.label, color = ShareText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${pendingApp.packageName} • ${pendingApp.apkFiles.size} APK part(s) • ${formatShareBytes(pendingApp.totalBytes)}", color = ShareMuted, fontSize = 10.sp)
                    Text("Only continue if you trust the person who sent this app. Android will show its own installation confirmation.", color = ShareMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    Button(onClick = onInstallPendingApp, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ShareBlue)) {
                        Text("Review and install")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeTile(value: String) {
    val bitmap = remember(value) { createShareQr(value) }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(116.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(6.dp))
    } else {
        Box(Modifier.size(116.dp).background(Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text("QR unavailable", color = ShareNavy, fontSize = 10.sp)
        }
    }
}

private fun createShareQr(value: String): Bitmap? = try {
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 260, 260)
    Bitmap.createBitmap(260, 260, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until 260) for (y in 0 until 260) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
} catch (_: WriterException) { null }

private fun formatShareBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
