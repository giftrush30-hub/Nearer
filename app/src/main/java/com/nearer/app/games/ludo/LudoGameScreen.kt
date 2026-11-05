package com.nearer.app.games.ludo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

private val Night = Color(0xFF081426)
private val Panel = Color(0xFF112746)
private val Raised = Color(0xFF19365E)
private val Cobalt = Color(0xFF2868E8)
private val Gold = Color(0xFFFFD34E)
private val White = Color(0xFFF6F8FF)
private val Muted = Color(0xFFAABBD4)

@Composable
fun LudoGameScreen(playerName: String, onExit: () -> Unit) {
    var playerCount by remember { mutableIntStateOf(4) }
    var computerOpponents by remember { mutableStateOf(true) }
    var game by remember(playerName) { mutableStateOf(LudoEngine.newGame(playerName, playerCount, computerOpponents = computerOpponents)) }
    var rolling by remember { mutableStateOf(false) }
    var rollingFace by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val pulse by rememberInfiniteTransition(label = "legal-pulse").animateFloat(
        0.72f, 1f, infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "legal-pulse"
    )

    suspend fun animateRoll(computer: Boolean = false) {
        if (rolling || !game.canRoll) return
        rolling = true
        repeat(if (computer) 7 else 10) {
            rollingFace = Random.nextInt(1, 7)
            delay(if (computer) 75 else 58)
        }
        game = LudoEngine.roll(game)
        rollingFace = game.dice ?: rollingFace
        rolling = false
    }

    LaunchedEffect(game.activePlayerIndex, game.dice, game.turnNumber, game.winnerId, game.message) {
        if (!game.isFinished && game.activePlayer.isComputer) {
            if (game.dice == null) animateRoll(computer = true)
            else {
                delay(720)
                LudoEngine.chooseComputerMove(game)?.let { game = LudoEngine.move(game, it) }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Night) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(color = Raised, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF315987)),
                    modifier = Modifier.clickable(onClick = onExit)) {
                    Text("‹  BACK", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEARER GAME ZONE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("LUDO", color = White, fontSize = 29.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                Surface(color = Color(0xFF153D79), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Cobalt)) {
                    Text("OFFLINE", color = Color(0xFFBFD5FF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                }
            }

            Surface(color = Panel, shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, Color(0xFF24466F)),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (game.isFinished) "MATCH COMPLETE" else "CURRENT TURN", color = Gold,
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp)
                        Spacer(Modifier.weight(1f))
                        Text("ROUND " + game.turnNumber, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(13.dp).clip(CircleShape).background(Color(game.activePlayer.color.hex)))
                        Spacer(Modifier.width(9.dp))
                        Text(game.activePlayer.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f))
                        Text(if (game.activePlayer.isComputer) "BOT PLAYING" else "YOUR MOVE",
                            color = if (game.activePlayer.isComputer) Muted else Gold, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(game.message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        game.players.forEach { player ->
                            val active = player.id == game.activePlayer.id
                            Surface(color = if (active) Color(0xFF244B80) else Color(0xFF0D1E35),
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, if (active) Gold.copy(alpha = 0.8f) else Color(0xFF294362)),
                                modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(vertical = 7.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(player.name.take(8), color = if (active) White else Muted, fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(player.finishedTokens.toString() + "/4 HOME", color = if (active) Gold else Muted,
                                        fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }

            Surface(color = Color(0xFF0D2039), shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF42618B)), shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 5.dp)) {
                        Text("THE ROYAL BOARD", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.weight(1f))
                        Text("4 TOKENS EACH", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    LudoBoard(game, pulse, Modifier.fillMaxWidth()) { token -> game = LudoEngine.move(game, token) }
                }
            }

            Surface(color = Panel, shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, Color(0xFF294D78)),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ROLL THE DICE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            Text(when {
                                game.isFinished -> "A champion has been crowned"
                                rolling -> "The dice are rolling…"
                                game.activePlayer.isComputer -> "NeaRer is taking its turn"
                                game.dice == null -> "Tap the golden dice to play"
                                game.legalTokenIndexes.isEmpty() -> "No legal move this turn"
                                else -> "Choose a token to move"
                            }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        DiceFace(if (rolling) rollingFace else game.dice ?: rollingFace, rolling, Modifier.size(66.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { scope.launch { animateRoll() } },
                            enabled = game.canRoll && !game.activePlayer.isComputer && !rolling,
                            modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A),
                                disabledContainerColor = Color(0xFF59677B), disabledContentColor = Color(0xFFB8C3D1)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp)) {
                            Text(if (rolling) "ROLLING…" else if (game.dice == null) "ROLL DICE  ⚄" else "DICE: " + game.dice,
                                fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                        }
                        Button(onClick = { game = LudoEngine.newGame(playerName, playerCount, computerOpponents = computerOpponents); rolling = false },
                            modifier = Modifier.height(54.dp), shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25466F), contentColor = White),
                            contentPadding = PaddingValues(horizontal = 13.dp)) {
                            Text("NEW GAME", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (game.activePlayer.isComputer && !game.isFinished) {
                        Text("NEARER BOT • THINKING", color = Color(0xFF9FC1FF), fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                    } else if (game.dice != null && game.legalTokenIndexes.isNotEmpty()) {
                        Text("SELECT YOUR TOKEN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            game.legalTokenIndexes.forEach { tokenIndex ->
                                Surface(color = Color(0xFF1B3B65), shape = RoundedCornerShape(13.dp),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = pulse)),
                                    modifier = Modifier.weight(1f).clickable { game = LudoEngine.move(game, tokenIndex) }) {
                                    Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(game.activePlayer.color.hex)).shadow(4.dp, CircleShape))
                                        Text("TOKEN " + (tokenIndex + 1), color = White, fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp))
                                        Text("MOVE", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                    if (game.isFinished) {
                        Text("🏆  " + (game.players.firstOrNull { it.id == game.winnerId }?.name ?: "Player") + " WINS!",
                            color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(game.lastAction, color = Color(0xFF9FC1FF), fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("MATCH MODE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = if (computerOpponents) Cobalt else Panel, shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, if (computerOpponents) Gold else Color(0xFF294362)),
                        modifier = Modifier.clickable {
                            computerOpponents = true
                            game = LudoEngine.newGame(playerName, playerCount, computerOpponents = true)
                        }) {
                        Text("VS COMPUTER", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp))
                    }
                    Surface(color = if (!computerOpponents) Cobalt else Panel, shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, if (!computerOpponents) Gold else Color(0xFF294362)),
                        modifier = Modifier.clickable {
                            computerOpponents = false
                            game = LudoEngine.newGame(playerName, playerCount, computerOpponents = false)
                        }) {
                        Text("PASS & PLAY", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("PLAYERS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4).forEach { count ->
                        Surface(color = if (playerCount == count) Cobalt else Panel, shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, if (playerCount == count) Gold else Color(0xFF294362)),
                            modifier = Modifier.clickable {
                                playerCount = count
                                game = LudoEngine.newGame(playerName, count, computerOpponents = computerOpponents)
                                rolling = false
                            }) {
                            Text(count.toString(), color = White, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            Text(if (computerOpponents) "TRADITIONAL LUDO  •  YOU VS COMPUTER OPPONENTS  •  OFFLINE" else "TRADITIONAL LUDO  •  LOCAL PASS & PLAY  •  OFFLINE",
                color = Color(0xFF8299B8), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun DiceFace(value: Int, rolling: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (rolling) 360f else 0f, tween(560, easing = FastOutSlowInEasing), label = "dice-angle")
    Canvas(modifier = modifier.rotate(rotation).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFE995), Gold, Color(0xFFE7A91F))),
            cornerRadius = CornerRadius(size.width * 0.22f))
        drawRoundRect(Color.White.copy(alpha = 0.45f), cornerRadius = CornerRadius(size.width * 0.22f),
            style = Stroke(size.width * 0.035f))
        val dot = size.width * 0.105f
        val l = size.width * 0.28f
        val m = size.width * 0.5f
        val r = size.width * 0.72f
        val t = size.height * 0.28f
        val c = size.height * 0.5f
        val b = size.height * 0.72f
        val points = when (value.coerceIn(1, 6)) {
            1 -> listOf(m to c)
            2 -> listOf(l to t, r to b)
            3 -> listOf(l to t, m to c, r to b)
            4 -> listOf(l to t, r to t, l to b, r to b)
            5 -> listOf(l to t, r to t, m to c, l to b, r to b)
            else -> listOf(l to t, r to t, l to c, r to c, l to b, r to b)
        }
        points.forEach { (x, y) -> drawCircle(Color(0xFF172A45), dot, Offset(x, y)) }
    }
}

@Composable
private fun LudoBoard(game: LudoState, pulse: Float, modifier: Modifier = Modifier, onTokenTap: (Int) -> Unit) {
    val animated = game.players.map { player ->
        player.tokens.map { target ->
            animateFloatAsState(target.toFloat(), tween((abs(target + 1) * 95).toInt().coerceIn(260, 1150),
                easing = FastOutSlowInEasing), label = "token-" + player.id + "-" + target).value
        }
    }
    Canvas(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9EFF8))
            .pointerInput(game.activePlayer.id, game.legalTokenIndexes, game.activePlayer.tokens) {
                detectTapGestures { tap ->
                    if (game.activePlayer.isComputer || game.legalTokenIndexes.isEmpty()) return@detectTapGestures
                    val cellSize = size.width / 15f
                    val nearest = game.legalTokenIndexes.minByOrNull { token ->
                        val p = positionForProgress(game.activePlayer.color, token, game.activePlayer.tokens[token].toFloat())
                        val dx = tap.x - (p.first + 0.5f) * cellSize
                        val dy = tap.y - (p.second + 0.5f) * cellSize
                        dx * dx + dy * dy
                    }
                    if (nearest != null) {
                        val p = positionForProgress(game.activePlayer.color, nearest, game.activePlayer.tokens[nearest].toFloat())
                        val dx = tap.x - (p.first + 0.5f) * cellSize
                        val dy = tap.y - (p.second + 0.5f) * cellSize
                        if (dx * dx + dy * dy <= cellSize * cellSize * 0.65f) onTokenTap(nearest)
                    }
                }
            }
    ) {
        val cell = size.width / 15f
        val colors = listOf(Color(0xFFD94C62), Color(0xFF23A879), Color(0xFFE5A923), Color(0xFF3978E8))
        fun tile(x: Int, y: Int, color: Color) {
            drawRoundRect(color, Offset(x * cell + 1.5f, y * cell + 1.5f),
                Size(cell - 3f, cell - 3f), CornerRadius(cell * 0.13f))
        }
        drawRoundRect(Color(0xFFF8FAFF), cornerRadius = CornerRadius(cell * 0.3f))
        for (y in 0..5) for (x in 0..5) tile(x, y, Color(0xFFF5DCE2))
        for (y in 9..14) for (x in 0..5) tile(x, y, Color(0xFFD9F1E7))
        for (y in 9..14) for (x in 9..14) tile(x, y, Color(0xFFFFEFCB))
        for (y in 0..5) for (x in 9..14) tile(x, y, Color(0xFFDCE8FF))
        val yards = listOf(Offset(0f, 0f), Offset(9f, 0f), Offset(0f, 9f), Offset(9f, 9f))
        yards.forEachIndexed { i, p ->
            drawRoundRect(colors[i].copy(alpha = 0.16f), Offset((p.x + 1f) * cell, (p.y + 1f) * cell),
                Size(4f * cell, 4f * cell), CornerRadius(cell * 0.42f))
            drawRoundRect(colors[i].copy(alpha = 0.55f), Offset((p.x + 1f) * cell, (p.y + 1f) * cell),
                Size(4f * cell, 4f * cell), CornerRadius(cell * 0.42f), style = Stroke(cell * 0.055f))
        }
        ludoTrackCells.forEachIndexed { index, point ->
            val safe = LudoEngine.isSafeSquare(index)
            val start = index == 0 || index == 13 || index == 26 || index == 39
            tile(point.first, point.second, when {
                start -> colors[index / 13]
                safe -> Color(0xFFFFD96A)
                else -> Color.White
            })
            if (safe && !start) {
                val center = Offset((point.first + 0.5f) * cell, (point.second + 0.5f) * cell)
                drawCircle(Color(0xFFB98518), cell * 0.17f, center, style = Stroke(cell * 0.045f))
                drawCircle(Color(0xFFFFD96A), cell * 0.105f, center)
            }
        }
        val lanes = listOf(
            listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
            listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
            listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
            listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)
        )
        lanes.forEachIndexed { i, lane -> lane.forEach { (x, y) -> tile(x, y, colors[i].copy(alpha = 0.9f)) } }
        val center = Path().apply {
            moveTo(7.5f * cell, 6f * cell); lineTo(9f * cell, 7.5f * cell)
            lineTo(7.5f * cell, 9f * cell); lineTo(6f * cell, 7.5f * cell); close()
        }
        drawPath(center, Color(0xFF102746))
        drawPath(center, Gold, style = Stroke(cell * 0.08f))
        drawCircle(Color(0xFFFFE99A), cell * 0.19f, Offset(7.5f * cell, 7.5f * cell))

        game.players.forEachIndexed { pIndex, player ->
            player.tokens.forEachIndexed { tokenIndex, target ->
                val point = positionForProgress(player.color, tokenIndex, animated[pIndex][tokenIndex])
                val x = (point.first + 0.5f) * cell
                val y = (point.second + 0.5f) * cell
                val tokenColor = colors[player.color.ordinal]
                if (player.id == game.activePlayer.id && tokenIndex in game.legalTokenIndexes) {
                    drawCircle(Gold.copy(alpha = 0.2f * pulse), cell * 0.49f, Offset(x, y))
                    drawCircle(Gold.copy(alpha = pulse), cell * 0.4f, Offset(x, y), style = Stroke(cell * 0.055f))
                }
                drawCircle(Color(0xFF172A45).copy(alpha = 0.3f), cell * 0.34f, Offset(x + cell * 0.035f, y + cell * 0.06f))
                drawCircle(Color(0xFFFAFCFF), cell * 0.33f, Offset(x, y))
                drawCircle(tokenColor, cell * 0.285f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.52f), cell * 0.095f, Offset(x - cell * 0.085f, y - cell * 0.095f))
            }
        }
    }
}

private fun positionForProgress(color: LudoColor, tokenIndex: Int, progress: Float): Pair<Float, Float> {
    val base = ludoBaseSpots[color.ordinal][tokenIndex]
    if (progress < 0f) {
        val start = ludoTrackCells[LudoEngine.globalSquare(color, 0)]
        return interpolateCell(base, start, progress + 1f)
    }
    if (progress >= 56f) return 7f to 7f
    if (progress < 52f) {
        val index = progress.toInt()
        val from = ludoTrackCells[LudoEngine.globalSquare(color, index)]
        val to = ludoTrackCells[LudoEngine.globalSquare(color, (index + 1) % 52)]
        return interpolateCell(from, to, progress - index)
    }
    val lane = ludoHomeLanes[color.ordinal]
    val laneProgress = progress - 52f
    val index = laneProgress.toInt().coerceIn(0, lane.lastIndex)
    val from = lane[index]
    val to = if (index == lane.lastIndex) 7 to 7 else lane[index + 1]
    return interpolateCell(from, to, laneProgress - index)
}

private fun interpolateCell(from: Pair<Int, Int>, to: Pair<Int, Int>, fraction: Float): Pair<Float, Float> {
    val t = fraction.coerceIn(0f, 1f)
    return (from.first + (to.first - from.first) * t) to (from.second + (to.second - from.second) * t)
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
    listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
    listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
    listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
    listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7)
)
