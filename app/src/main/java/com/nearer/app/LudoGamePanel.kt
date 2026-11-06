package com.nearer.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LudoGold = Color(0xFFFFC857)
private val LudoNavy = Color(0xFF10243B)
private fun LudoColor.paint(): Color = when (this) {
    LudoColor.RED -> Color(0xFFE65C65)
    LudoColor.GREEN -> Color(0xFF37B987)
    LudoColor.YELLOW -> LudoGold
    LudoColor.BLUE -> Color(0xFF4C9DFA)
}

@Composable
fun LudoGamePanel() {
    var game by remember { mutableStateOf(LudoEngine.newGame(
        mapOf(LudoColor.RED to "You", LudoColor.GREEN to "Nova", LudoColor.YELLOW to "Atlas", LudoColor.BLUE to "Mira"),
        setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
    )) }
    var message by remember { mutableStateOf("Roll the dice to begin") }
    var lastRoll by remember { mutableIntStateOf(0) }
    val current = game.currentPlayer
    val moves = LudoEngine.legalMoves(game)

    LaunchedEffect(game.currentPlayerIndex, game.diceValue, game.turnNumber, game.winner) {
        if (!game.isFinished && game.currentPlayer.isComputer) {
            delay(850)
            if (game.diceValue == null) {
                game = LudoEngine.rollDice(game)
                lastRoll = game.diceValue ?: 0
                message = "${current.name} rolled $lastRoll"
            } else {
                val choice = LudoEngine.chooseComputerMove(game)
                if (choice == null) {
                    game = LudoEngine.passTurn(game)
                    message = "${current.name} has no legal move"
                } else {
                    val result = LudoEngine.moveToken(game, choice)
                    game = result.state
                    message = if (result.captured.isNotEmpty()) "${current.name} captured a token!" else "${current.name} moved"
                }
            }
        }
    }

    Surface(color = LudoNavy, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("NEARER GAME ZONE", color = LudoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("LUDO • CLASSIC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Text("LOCAL • VS AI", color = Color(0xFFB8C8D8), fontSize = 10.sp)
            }
            LudoBoard(game, Modifier.fillMaxWidth().aspectRatio(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(if (game.isFinished) "${game.winner} WINS!" else "${current.name}'S TURN", color = current.color.paint(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(message, color = Color(0xFFCAD6E2), fontSize = 11.sp)
                }
                Surface(color = Color(0xFF213B56), shape = RoundedCornerShape(14.dp)) {
                    Text(if (game.diceValue == null) "🎲" else "🎲  ${game.diceValue}", color = LudoGold, fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp))
                }
            }
            if (!game.isFinished && !current.isComputer && game.diceValue == null) {
                Button(onClick = {
                    game = LudoEngine.rollDice(game)
                    lastRoll = game.diceValue ?: 0
                    message = "You rolled $lastRoll"
                    if (moves.isEmpty()) Unit
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = LudoGold, contentColor = LudoNavy)) {
                    Text("ROLL THE DICE", fontWeight = FontWeight.Black)
                }
            } else if (!game.isFinished && !current.isComputer && game.diceValue != null) {
                if (moves.isEmpty()) {
                    Text("No token can move. Pass your turn.", color = LudoGold, fontSize = 12.sp)
                    Button(onClick = { game = LudoEngine.passTurn(game); message = "Turn passed" }, modifier = Modifier.fillMaxWidth()) { Text("PASS TURN") }
                } else {
                    Text("Choose a token to move", color = LudoGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        moves.forEach { index ->
                            Button(onClick = {
                                val result = LudoEngine.moveToken(game, index)
                                game = result.state
                                message = when {
                                    result.state.isFinished -> "Champion! You won the match."
                                    result.captured.isNotEmpty() -> "Great capture! Extra pressure on the board."
                                    result.tokenFinished -> "Token safely reached home."
                                    else -> "Token ${index + 1} moved"
                                }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = current.color.paint(), contentColor = LudoNavy)) {
                                Text("MOVE ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                game.players.forEach { player ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(player.name, color = player.color.paint(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${player.finishedTokens}/4", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Button(onClick = { game = LudoEngine.newGame(
                mapOf(LudoColor.RED to "You", LudoColor.GREEN to "Nova", LudoColor.YELLOW to "Atlas", LudoColor.BLUE to "Mira"),
                setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
            ); message = "New match — roll the dice"; lastRoll = 0 }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF294866))) {
                Text("NEW MATCH", color = Color.White)
            }
            Text("Traditional Ludo rules • Roll six to leave the yard • Exact roll to finish • Captures and safe squares", color = Color(0xFF9FB2C6), fontSize = 9.sp)
        }
    }
}

@Composable
private fun LudoBoard(game: LudoGameState, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFFE65C65), Color(0xFF37B987), LudoGold, Color(0xFF4C9DFA))
    Canvas(modifier.background(Color(0xFFF2F4F7), RoundedCornerShape(12.dp))) {
        val cell = size.width / 15f
        fun cellRect(x: Int, y: Int, color: Color) {
            drawRect(color, Offset(x * cell, y * cell), Size(cell, cell))
            drawRect(Color(0xFFCCD3DC), Offset(x * cell, y * cell), Size(cell, cell), style = Stroke(0.7f))
        }
        for (i in 0..14) for (j in 0..14) cellRect(i, j, Color(0xFFF8FAFC))
        drawRect(colors[0], Offset(0f, 0f), Size(cell * 6, cell * 6))
        drawRect(colors[1], Offset(cell * 9, 0f), Size(cell * 6, cell * 6))
        drawRect(colors[2], Offset(0f, cell * 9), Size(cell * 6, cell * 6))
        drawRect(colors[3], Offset(cell * 9, cell * 9), Size(cell * 6, cell * 6))
        // Four home yards with four clearly visible token sockets.
        val yards = listOf(Offset(1f,1f), Offset(10f,1f), Offset(1f,10f), Offset(10f,10f))
        yards.forEachIndexed { index, origin ->
            drawRoundRect(Color.White, Offset(origin.x * cell, origin.y * cell), Size(cell * 4, cell * 4), androidx.compose.ui.geometry.CornerRadius(cell * .35f))
            repeat(4) { n ->
                val x = origin.x + if (n % 2 == 0) 1.25f else 2.75f
                val y = origin.y + if (n < 2) 1.25f else 2.75f
                drawCircle(colors[index], cell * .42f, Offset((x + .5f) * cell, (y + .5f) * cell))
                drawCircle(Color.White, cell * .32f, Offset((x + .5f) * cell, (y + .5f) * cell), style = Stroke(cell * .07f))
            }
        }
        // Cross-shaped shared track with coloured start gates and home lanes.
        for (i in 0..5) {
            cellRect(6, i + 1, if (i == 0) colors[0] else Color.White)
            cellRect(8, i + 1, if (i == 0) colors[1] else Color.White)
            cellRect(6, 9 + i, if (i == 5) colors[2] else Color.White)
            cellRect(8, 9 + i, if (i == 5) colors[3] else Color.White)
            cellRect(i + 1, 6, Color.White)
            cellRect(i + 1, 8, Color.White)
            cellRect(9 + i, 6, Color.White)
            cellRect(9 + i, 8, Color.White)
        }
        for (i in 0..4) {
            cellRect(7, i + 1, colors[0])
            cellRect(7, i + 10, colors[2])
            cellRect(i + 1, 7, colors[1])
            cellRect(i + 10, 7, colors[3])
        }
        // Central four-colour home.
        val c = Offset(7.5f * cell, 7.5f * cell)
        drawCircle(LudoGold, cell * 1.2f, c)
        drawCircle(LudoNavy, cell * .85f, c, style = Stroke(cell * .18f))
        // Tokens are drawn from the live rules-engine state, not a static illustration.
        game.players.forEachIndexed { p, player ->
            player.tokens.forEachIndexed { t, token ->
                if (!token.isFinished) {
                    val pos = when {
                        token.isInYard -> {
                            val base = yards[p]
                            Offset((base.x + if (t % 2 == 0) 1.75f else 3.25f) * cell, (base.y + if (t < 2) 1.75f else 3.25f) * cell)
                        }
                        token.steps in 52..57 -> when (player.color) {
                            LudoColor.RED -> Offset(7.5f * cell, (6.5f - (token.steps - 52)) * cell)
                            LudoColor.GREEN -> Offset((8.5f + (token.steps - 52)) * cell, 7.5f * cell)
                            LudoColor.YELLOW -> Offset(7.5f * cell, (8.5f + (token.steps - 52)) * cell)
                            LudoColor.BLUE -> Offset((6.5f - (token.steps - 52)) * cell, 7.5f * cell)
                        }
                        else -> {
                            val a = (token.steps * 2 * Math.PI / 52.0) - Math.PI / 2
                            Offset((7.5 + 5.5 * kotlin.math.cos(a)).toFloat() * cell, (7.5 + 5.5 * kotlin.math.sin(a)).toFloat() * cell)
                        }
                    }
                    drawCircle(Color(0x55000000), cell * .43f, pos + Offset(cell * .04f, cell * .07f))
                    drawCircle(player.color.paint(), cell * .4f, pos)
                    drawCircle(Color.White, cell * .29f, pos, style = Stroke(cell * .09f))
                    drawCircle(Color.White, cell * .09f, pos)
                }
            }
        }
    }
}
