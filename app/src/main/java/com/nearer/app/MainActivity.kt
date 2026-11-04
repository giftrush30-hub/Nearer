package com.nearer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Midnight = Color(0xFF101A2B)
private val Panel = Color(0xFF1A2940)
private val Mint = Color(0xFF69E0C0)
private val Muted = Color(0xFFA8B6C9)
private val White = Color(0xFFF6F8FC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Midnight,
                surface = Panel,
                primary = Mint,
                onPrimary = Midnight,
                onBackground = White,
                onSurface = White
            )) {
                NeaRerHome()
            }
        }
    }
}

@Composable
private fun NeaRerHome() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Midnight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Panel
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(0xFF244B4A), modifier = Modifier.size(10.dp)) {}
                        Text("  OFFLINE-FIRST CONNECTION", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Text(
                        "Bring nearby people closer.",
                        color = White,
                        fontSize = 25.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Chat and share directly between nearby Android phones. No mobile data required for a direct local connection.",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text("Find people nearby", fontWeight = FontWeight.Bold)
                    }
                    Text("Nearby discovery is the next feature being connected.", color = Muted, fontSize = 11.sp)
                }
            }

            Text("YOUR SPACE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

            FeatureTile("Chats", "Private conversations with nearby connections", "01")
            FeatureTile("File sharing", "Send photos, videos and documents directly", "02")
            FeatureTile("Connections", "Manage people and devices you trust", "03")

            Spacer(modifier = Modifier.weight(1f))
            Text(
                "NEARER  •  BUILT FOR DIRECT CONNECTION",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun FeatureTile(title: String, subtitle: String, number: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Panel
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(number, color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.padding(start = 15.dp)) {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
