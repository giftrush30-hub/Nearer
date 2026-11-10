package com.nearer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Honest mesh status panel. Counts are supplied by the live app state; this
 * component never animates fictional peers or claims that disabled relaying works.
 */
@Composable
internal fun MeshStatusPanel(
    activePeerCount: Int,
    routeCount: Int,
    queuedPacketCount: Int,
    relayEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF172B49)
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NEARER MESH", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("A network that can grow with you", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                Surface(shape = RoundedCornerShape(30.dp), color = if (relayEnabled) Color(0xFF174B43) else Color(0xFF3C3540)) {
                    Text(
                        if (relayEnabled) "RELAY ACTIVE" else "BUILDING SECURE MESH",
                        color = if (relayEnabled) Color(0xFF9DE8C9) else Gold,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MeshMetric("NEARBY PEERS", activePeerCount.toString(), Modifier.weight(1f))
                MeshMetric("KNOWN ROUTES", routeCount.toString(), Modifier.weight(1f))
                MeshMetric("DEAD DROPS", queuedPacketCount.toString(), Modifier.weight(1f))
            }
            Text(
                if (relayEnabled) "Encrypted relay is enabled for this device."
                else "Direct nearby chat remains available. Multi-hop forwarding stays off until trusted identity keys, packet verification and transport forwarding are connected.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MeshStage("01", "Encrypt", true)
                MeshStage("02", "Route", true)
                MeshStage("03", "Forward", relayEnabled)
                MeshStage("04", "Deliver", relayEnabled)
            }
        }
    }
}

@Composable
private fun MeshMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = Color(0xFF101A2B)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = BlueAccent, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun MeshStage(number: String, label: String, ready: Boolean) {
    Surface(
        modifier = Modifier.weightless(),
        shape = RoundedCornerShape(9.dp),
        color = if (ready) Color(0xFF174B43) else Color(0xFF26344A)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(number, color = if (ready) Color(0xFF9DE8C9) else Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(label, color = White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun Modifier.weightless(): Modifier = this
