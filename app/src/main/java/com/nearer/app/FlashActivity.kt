package com.nearer.app

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class FlashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var number by remember { mutableStateOf("") }
            var code by remember { mutableIntStateOf(5) }
            var step by remember { mutableIntStateOf(0) }
            var status by remember { mutableStateOf("Enable Flash receiving on this phone before receiving signals.") }
            val roleManager = getSystemService(RoleManager::class.java)
            val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                status = if (Build.VERSION.SDK_INT >= 29 && roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true) "Flash receiver is enabled." else "Call screening was not enabled."
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD45A), background = Color(0xFF081426), surface = Color(0xFF142640))) {
                LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("NEARER FLASH", color = Color(0xFFFFD45A), fontWeight = FontWeight.Bold)
                        Text("Messages beyond distance", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text("Preset missed-call signals. Both phones need cellular coverage and NeaRer.", color = Color.LightGray)
                        OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Receiver phone number") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF142640))) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("RECEIVER SETUP", color = Color(0xFFFFD45A), fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = number, onValueChange = { number = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Trusted sender number") })
                                Button(onClick = {
                                    if (number.filter(Char::isDigit).length < 10) status = "Enter a valid phone number."
                                    else {
                                        getSharedPreferences(FlashCallScreeningService.PREFS, MODE_PRIVATE).edit()
                                            .putString(FlashCallScreeningService.KEY_TRUSTED_NUMBER, number)
                                            .putBoolean(FlashCallScreeningService.KEY_ENABLED, true).apply()
                                        status = "Trusted sender saved."
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("SAVE TRUSTED SENDER") }
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 29 && roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true)
                                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                    else status = "Android 10 or later is required for call screening."
                                }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE FLASH RECEIVER") }
                                Button(onClick = { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 740) }, modifier = Modifier.fillMaxWidth()) { Text("GRANT CONTACTS ACCESS") }
                        if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 741) }, modifier = Modifier.fillMaxWidth()) { Text("GRANT NOTIFICATION ACCESS") }
                        Text(status, color = Color.LightGray)
                            }
                        }
                    }
                    items(FlashCallScreeningService.FLASH_CODES.toList()) { item ->
                        val (n, message) = item
                        Button(onClick = { code = n; step = 0 }, modifier = Modifier.fillMaxWidth()) { Text("$n  $message") }
                    }
                    item {
                        Text("Selected: ${FlashCallScreeningService.FLASH_CODES[code]}", color = Color.White)
                        Text("Signal $step of $code", color = Color.LightGray)
                        Button(onClick = {
                            if (number.isNotBlank() && step < code) {
                                step += 1
                                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("OPEN DIALER FOR NEXT SIGNAL") }
                        Text("Place one call, hang up, return here and repeat. Calls may cost airtime.", color = Color.LightGray)
                    }
                }
            }
        }
    }
}
