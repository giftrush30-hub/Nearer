package com.nearer.app.games.ludo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

private val LudoNavy = Color(0xFF101A2B)
private val LudoPanel = Color(0xFF1A2940)
private val LudoMint = Color(0xFF69E0C0)
private val LudoWhite = Color(0xFFF6F8FC)
private val LudoMuted = Color(0xFFA8B6C9)

@Composable
fun LudoGameScreen(
    playerName: String,
    onExit: () -> Unit
) {
    var playerCount by remember { mutableStateOf(4) }
    var game by remember(playerName) { mutableStateOf(LudoEngine.newGame(playerName, playerCount)) }

    LaunchedEffect(game.activePlayerIndex, game.dice, game.turnNumber, game.winnerId, game.message) {
        if (!game.isFinished && game.activePlayer.isComputer) {
            delay(650)
            if (game.dice == null) game = LudoEngine.roll(game)
            if (game.dice != null && game.activePlayer.isComputer) {
                delay(650)
                LudoEngine.chooseComputerMove(game)?.let { game = LudoEngine.move(game, it) }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = LudoNavy) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = LudoPanel, contentColor = LudoWhite)) {
                    Text("← Back")
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEARER GAME ZONE", color = LudoMint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Ludo", color = LudoWhite, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF203858)) {
                    Text("OFFLINE", color = LudoMint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }

            Surface(color = LudoPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (game.isFinished) "MATCH COMPLETE" else "PLAYER TURN", color = LudoMint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(13.dp).clip(CircleShape).background(Color(game.activePlayer.color.hex)))
                        Text("  ${game.activePlayer.name}", color = LudoWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(game.dice?.let { "Dice: $it" } ?: "Dice ready", color = LudoMuted, fontSize = 12.sp)
                    }
                    Text(game.message, color = if (game.isFinished) Color(0xFFFFD166) else LudoMuted, fontSize = 12.sp)
                }
            }

            LudoBoard(game = game, modifier = Modifier.fillMaxWidth().weight(1f, fill = false))

            Surface(color = LudoPanel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("YOUR TOKENS", color = LudoMint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (game.activePlayer.isComputer) {
                        Text("NeaRer is choosing a move…", color = LudoMuted, fontSize = 12.sp)
                    } else if (game.dice == null) {
                        Text("Roll the dice to begin your turn.", color = LudoMuted, fontSize = 12.sp)
                    } else if (game.legalTokenIndexes.isEmpty()) {
                        Text("No legal token move.", color = LudoMuted, fontSize = 12.sp)
                    } else {
                        Text("Choose which token to move:", color = LudoMuted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            game.legalTokenIndexes.forEach { tokenIndex ->
                                Button(
                                    onClick = { game = LudoEngine.move(game, tokenIndex) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(game.activePlayer.color.hex), contentColor = LudoNavy)
                                ) { Text("Token ${tokenIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { game = LudoEngine.roll(game) },
                            enabled = game.canRoll && !game.activePlayer.isComputer,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LudoMint, contentColor = LudoNavy),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("ROLL DICE", fontWeight = FontWeight.Black) }
                        Button(
                            onClick = { game = LudoEngine.newGame(playerName, playerCount) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34465F), contentColor = LudoWhite),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("New game") }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Players: $playerCount", color = LudoMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        Button(
                            onClick = { playerCount = count; game = LudoEngine.newGame(playerName, count) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (playerCount == count) LudoMint else LudoPanel, contentColor = if (playerCount == count) LudoNavy else LudoWhite),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("$count", fontSize = 11.sp) }
                    }
                }
            }
            Text("Traditional rules • 1 human + computer opponents • no mobile data", color = LudoMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LudoBoard(game: LudoState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(330.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEEF2F7)).padding(3.dp)) {
        val cell = size.width / 15f
        val track = ludoTrackCells
        val baseColors = listOf(
            Color(game.players[0].color.hex),
            Color(0xFF58C99B),
            Color(0xFFFFD166),
            Color(0xFF5B91F2)
        )
        fun cellRect(x: Int, y: Int, color: Color) {
            drawRoundRect(color, Offset(x * cell + 1f, y * cell + 1f), Size(cell - 2f, cell - 2f), CornerRadius(cell * 0.12f))
        }
        // Four home yards.
        for (y in 0..5) for (x in 0..5) cellRect(x, y, Color(0xFFFFE0E2))
        for (y in 9..14) for (x in 0..5) cellRect(x, y, Color(0xFFDDF6EA))
        for (y in 9..14) for (x in 9..14) cellRect(x, y, Color(0xFFFFF0C9))
        for (y in 0..5) for (x in 9..14) cellRect(x, y, Color(0xFFDCE8FF))
        // Yard inset panels.
        listOf(Offset(0f, 0f), Offset(9f, 0f), Offset(0f, 9f), Offset(9f, 9f)).forEachIndexed { index, offset ->
            drawRoundRect(baseColors[index].copy(alpha = 0.18f), Offset((offset.x + 1f) * cell, (offset.y + 1f) * cell), Size(4f * cell, 4f * cell), CornerRadius(cell * 0.35f))
        }
        // Main track.
        track.forEachIndexed { index, point ->
            val safe = LudoEngine.isSafeSquare(index)
            val color = when (index) {
                0 -> Color(0xFFFFB7BE)
                13 -> Color(0xFFB7EAD4)
                26 -> Color(0xFFFFE3A0)
                39 -> Color(0xFFB9D0FF)
                else -> if (safe) Color(0xFFDCE5EF) else Color.White
            }
            cellRect(point.first, point.second, color)
        }
        // Home lanes leading into the centre.
        val lanes = listOf(
            listOf(5 to 7, 4 to 7, 3 to 7, 2 to 7, 1 to 7, 7 to 7),
            listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5, 7 to 7),
            listOf(9 to 7, 10 to 7, 11 to 7, 12 to 7, 13 to 7, 7 to 7),
            listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9, 7 to 7)
        )
        lanes.forEachIndexed { index, lane ->
            lane.dropLast(1).forEach { (x, y) -> cellRect(x, y, baseColors[index].copy(alpha = 0.75f)) }
        }
        // Centre finish.
        val center = Path().apply {
            moveTo(7.5f * cell, 6f * cell)
            lineTo(9f * cell, 7.5f * cell)
            lineTo(7.5f * cell, 9f * cell)
            lineTo(6f * cell, 7.5f * cell)
            close()
        }
        drawPath(center, Color(0xFF23354D))
        // Tokens: yards, track and home lane.
        game.players.forEachIndexed { playerIndex, player ->
            player.tokens.forEachIndexed { tokenIndex, progress ->
                val point = when {
                    progress == -1 -> ludoBaseSpots[playerIndex][tokenIndex]
                    progress in 0..51 -> track[LudoEngine.globalSquare(player.color, progress)]
                    progress in 52..55 -> ludoHomeLanes[playerIndex][progress - 52]
                    else -> 7 to 7
                }
                val x = (point.first + 0.5f) * cell
                val y = (point.second + 0.5f) * cell
                drawCircle(Color(0xFF253247).copy(alpha = 0.25f), cell * 0.34f, Offset(x + 1f, y + 2f))
                drawCircle(Color(player.color.hex), cell * 0.31f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.85f), cell * 0.11f, Offset(x - cell * 0.07f, y - cell * 0.08f))
            }
        }
    }
}

private val ludoTrackCells = listOf(
    6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
    5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6, 0 to 6,
    0 to 7, 0 to 8, 1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8,
    6 to 9, 6 to 10, 6 to 11, 6 to 12, 6 to 13, 6 to 14,
    7 to 14, 8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10, 8 to 9,
    9 to 8, 10 to 8, 11 to 8, 12 to 8, 13 to 8, 14 to 8,
    14 to 7, 14 to 6, 13 to 6, 12 to 6, 11 to 6, 10 to 6, 9 to 6,
    8 to 5, 8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
    7 to 0, 6 to 0
)

private val ludoBaseSpots = listOf(
    listOf(2 to 2, 4 to 2, 2 to 4, 4 to 4),
    listOf(2 to 10, 4 to 10, 2 to 12, 4 to 12),
    listOf(10 to 10, 12 to 10, 10 to 12, 12 to 12),
    listOf(10 to 2, 12 to 2, 10 to 4, 12 to 4)
)

private val ludoHomeLanes = listOf(
    listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4),
    listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7),
    listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10),
    listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7)
)
