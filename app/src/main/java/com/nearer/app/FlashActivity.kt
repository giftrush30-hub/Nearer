package com.nearer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD45A), background = Color(0xFF081426), surface = Color(0xFF142640))) {
                LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("NEARER FLASH", color = Color(0xFFFFD45A), fontWeight = FontWeight.Bold)
                        Text("Messages beyond distance", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text("Preset missed-call signals. Both phones need cellular coverage and NeaRer.", color = Color.LightGray)
                        OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Receiver phone number") }, modifier = Modifier.fillMaxWidth())
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
