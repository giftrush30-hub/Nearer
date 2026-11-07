package com.nearer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ShareMidnight = Color(0xFF101A2B)
private val SharePanel = Color(0xFF1A2940)
private val ShareGold = Color(0xFFFFD12D)
private val ShareMint = Color(0xFF69E0C0)
private val ShareWhite = Color(0xFFF6F8FC)
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
    onInstallPendingApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val filteredApps = remember(apps, search) {
        apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
    }
    val selectedApps = apps.filter { it.packageName in selected }
    val canSendToAnyAndroidPhone = selectedApps.isNotEmpty() && selectedApps.all { it.packageFiles.size == 1 }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = SharePanel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NEARER SHARE HUB", color = ShareGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text("Share with any phone.", color = ShareWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Nearby NeaRer transfers and browser downloads for everyone else.", color = ShareMuted, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF263F61), modifier = Modifier.size(48.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text("N", color = ShareGold, fontSize = 25.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF203858), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("01  NEARBY", color = ShareMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Send to NeaRer", color = ShareWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF203858), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("02  OPEN SEND", color = ShareGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Send without the app", color = ShareWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = SharePanel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OPENSEND • NO APP REQUIRED", color = ShareGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Let any phone receive your files.", color = ShareWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("NeaRer creates a temporary local Wi-Fi network and a private download page. The receiver joins the network and opens the link in their browser. No mobile data is needed. Android and iPhone can receive ordinary files; only Android can install APK apps.", color = ShareMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(
                    onClick = onPickOpenSendFiles,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShareGold, contentColor = ShareMidnight)
                ) { Text(if (openSendSession == null) "Choose files to share" else "Replace shared files", fontWeight = FontWeight.Bold) }
                if (openSendSession != null) {
                    Text("YOUR TEMPORARY WI-FI", color = ShareMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(openSendSession.ssid, color = ShareWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    openSendSession.password?.let { Text("Password: $it", color = ShareMuted, fontSize = 12.sp) }
                    Text("Scan to join Wi-Fi", color = ShareWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OpenSendQrImage(OpenSendQrCode.wifiPayload(openSendSession.ssid, openSendSession.password), Modifier.align(Alignment.CenterHorizontally))
                    Text("After joining, scan this second code or type the address below.", color = ShareMuted, fontSize = 11.sp)
                    OpenSendQrImage(openSendSession.downloadUrl, Modifier.align(Alignment.CenterHorizontally))
                    Text(openSendSession.downloadUrl, color = ShareMint, fontSize = 10.sp)
                    Text("${openSendSession.fileCount} file(s) • ${formatShareBytes(openSendSession.totalBytes)}", color = ShareMuted, fontSize = 11.sp)
                    Button(
                        onClick = onStopOpenSend,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF633D4B), contentColor = ShareWhite)
                    ) { Text("Stop sharing and close access") }
                }
                if (openSendNotice.isNotBlank()) Text(openSendNotice, color = ShareMuted, fontSize = 11.sp)
                Text("The receiver's camera may offer to join the Wi-Fi network, but QR behavior differs by phone. The network name, password and browser address are always shown as a manual fallback.", color = ShareMuted, fontSize = 10.sp, lineHeight = 15.sp)
                Text("OpenSend is a local HTTP session, not end-to-end encrypted. Share only with people you trust, and keep NeaRer running until the receiver finishes. Android may stop the session if the app or process is closed.", color = ShareMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        Surface(shape = RoundedCornerShape(22.dp), color = SharePanel, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("APPSHARE • SHARE INSTALLED APPS", color = ShareGold, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Send an app, not just a file.", color = ShareWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Choose an installed Android app and send its complete install package to a connected NeaRer phone. Some apps contain multiple split APK files; NeaRer keeps them together.", color = ShareMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Button(
                    onClick = onLoadApps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !appsLoading,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263F61), contentColor = ShareWhite)
                ) { Text(if (appsLoading) "Loading installed apps…" else if (apps.isEmpty()) "Show my installed apps" else "Refresh installed apps") }
                if (apps.isNotEmpty()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search apps") },
                        placeholder = { Text("App name") }
                    )
                    Text("${filteredApps.size} app(s) • ${selected.size} selected", color = ShareMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    filteredApps.take(60).forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, color = ShareWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${app.versionName ?: "Version unknown"} • ${formatShareBytes(app.totalBytes)}", color = ShareMuted, fontSize = 10.sp)
                            }
                            Text(if (app.isSystemApp) "SYSTEM" else "APP", color = if (app.isSystemApp) ShareGold else ShareMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (filteredApps.size > 60) Text("Showing the first 60 matches. Search to narrow the list.", color = ShareMuted, fontSize = 10.sp)
                    Button(
                        onClick = { onShareApps(selected.toList()) },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShareMint, contentColor = ShareMidnight)
                    ) { Text("Send selected apps", fontWeight = FontWeight.Bold) }
                    if (selectedApps.any { it.packageFiles.size > 1 }) {
                        Text("One or more selected apps contain split APKs. They can be sent as complete packages to a NeaRer phone, but a phone without NeaRer can install only single-APK apps through this browser flow.", color = ShareGold, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    Button(
                        onClick = { onShareAppsToAnyPhone(selected.toList()) },
                        enabled = canSendToAnyAndroidPhone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263F61), contentColor = ShareWhite)
                    ) { Text("Share installable APKs with any Android phone", fontWeight = FontWeight.Bold) }
                }
                Text("Privacy: when you tap the app-list button, NeaRer reads installed app names and package details on this phone to build the list. The inventory stays on this device unless you choose an app to send. App accounts, chats, passwords and private app data are never copied.", color = ShareMuted, fontSize = 10.sp, lineHeight = 15.sp)
                if (appNotice.isNotBlank()) Text(appNotice, color = ShareMuted, fontSize = 11.sp)
                Text("Single-APK apps can be downloaded by phones without NeaRer. Split APK apps need NeaRer on the receiving phone for package-aware installation. Android always asks the receiver to approve installation.", color = ShareMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }

        if (pendingApp != null) {
            Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF173C3A), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("APP RECEIVED • READY FOR REVIEW", color = ShareMint, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(pendingApp.label, color = ShareWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("${pendingApp.packageName} • ${pendingApp.versionName ?: "Unknown version"}", color = ShareMuted, fontSize = 11.sp)
                    Text("${pendingApp.apkFiles.size} verified APK component(s) • ${formatShareBytes(pendingApp.totalBytes)}", color = ShareMuted, fontSize = 11.sp)
                    Button(onClick = onInstallPendingApp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShareGold, contentColor = ShareMidnight)) {
                        Text("Review and install with Android", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatShareBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
