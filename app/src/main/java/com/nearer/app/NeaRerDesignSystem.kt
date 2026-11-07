package com.nearer.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared brand tokens. Accent blue is reserved for actions, not used as a page fill. */
internal object NeaRerPalette {
    val Midnight = Color(0xFF101A2B)
    val Panel = Color(0xFF1A2940)
    val RaisedPanel = Color(0xFF203858)
    val AccentBlue = Color(0xFF2563EB)
    val Gold = Color(0xFFFFD12D)
    val Mint = Color(0xFF69E0C0)
    val TextPrimary = Color(0xFFF6F8FC)
    val TextMuted = Color(0xFFA8B6C9)
    val Border = Color(0xFF26364D)
}

/** Five persistent destinations keep the phone navigation clear and thumb-friendly. */
internal data class NeaRerDestination(
    val id: String,
    val label: String,
    val glyph: String
)

internal object NeaRerNavigation {
    val destinations = listOf(
        NeaRerDestination("home", "Home", "⌂"),
        NeaRerDestination("nearby", "Nearby", "◎"),
        NeaRerDestination("chats", "Chats", "✉"),
        NeaRerDestination("community", "Groups", "♧"),
        NeaRerDestination("more", "More", "•••")
    )
}

@Composable
internal fun NeaRerMoreHub(
    onOpenGames: () -> Unit,
    onOpenFlash: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "YOUR NEARER TOOLS",
                color = NeaRerPalette.Gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Text(
                "More ways to stay close",
                color = NeaRerPalette.TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Open the extra features without crowding your main navigation.",
                color = NeaRerPalette.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        MoreHubCard(
            glyph = "♜",
            title = "Game Zone",
            description = "Play Ludo and other games with people you connect with.",
            accent = NeaRerPalette.Gold,
            onClick = onOpenGames
        )
        MoreHubCard(
            glyph = "⌁",
            title = "NeaRer Flash",
            description = "Send and receive short call-based signals. Network support may vary.",
            accent = NeaRerPalette.Mint,
            onClick = onOpenFlash
        )
    }
}

@Composable
private fun MoreHubCard(
    glyph: String,
    title: String,
    description: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = NeaRerPalette.Panel,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, NeaRerPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = NeaRerPalette.RaisedPanel,
                shape = RoundedCornerShape(15.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(glyph, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = NeaRerPalette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(description, color = NeaRerPalette.TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Text("›", color = accent, fontSize = 26.sp, fontWeight = FontWeight.Light)
        }
    }
}
