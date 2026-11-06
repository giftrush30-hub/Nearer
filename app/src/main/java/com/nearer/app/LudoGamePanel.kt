package com.nearer.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val LudoGold = Color(0xFFFFC857)
private val LudoNavy = Color(0xFF10243B)
private val LudoSurface = Color(0xFF193653)
private fun LudoColor.paint(): Color = when (this) {
    LudoColor.RED -> Color(0xFFE64F61)
    LudoColor.GREEN -> Color(0xFF20A978)
    LudoColor.YELLOW -> Color(0xFFFFC857)
    LudoColor.BLUE -> Color(0xFF428CE8)
}

private val ludoTrack = buildList {
    for (y in 1..5) add(6 to y)
    for (x in 5 downTo 0) add(x to 6)
    add(0 to 7); add(0 to 8)
    for (x in 1..5) add(x to 8)
    for (y in 9..14) add(6 to y)
    add(7 to 14); add(8 to 14)
    for (y in 13 downTo 9) add(8 to y)
    for (x in 9..14) add(x to 8)
    add(14 to 7); add(14 to 6)
    for (x in 13 downTo 9) add(x to 6)
    for (y in 5 downTo 0) add(8 to y)
    add(7 to 0); add(6 to 0)
}

private fun ludoTokenPosition(color: LudoColor, token: LudoToken, index: Int): Offset {
    if (token.isFinished) return Offset(7.5f, 7.5f)
    if (token.isInYard) {
        val bases = mapOf(
            LudoColor.RED to listOf(2.5f to 2.5f, 4.5f to 2.5f, 2.5f to 4.5f, 4.5f to 4.5f),
            LudoColor.GREEN to listOf(10.5f to 2.5f, 12.5f to 2.5f, 10.5f to 4.5f, 12.5f to 4.5f),
            LudoColor.YELLOW to listOf(10.5f to 10.5f, 12.5f to 10.5f, 10.5f to 12.5f, 12.5f to 12.5f),
            LudoColor.BLUE to listOf(2.5f to 10.5f, 4.5f to 10.5f, 2.5f to 12.5f, 4.5f to 12.5f)
        )
        val p = bases.getValue(color)[index]
        return Offset(p.first, p.second)
    }
    if (token.steps in 52..56) {
        val lane = token.steps - 52
        return when (color) {
            LudoColor.RED -> Offset(7.5f, 1.5f + lane)
            LudoColor.GREEN -> Offset(13.5f - lane, 7.5f)
            LudoColor.YELLOW -> Offset(7.5f, 13.5f - lane)
            LudoColor.BLUE -> Offset(1.5f + lane, 7.5f)
        }
    }
    val square = (color.startOffset + token.steps) % 52
    val p = ludoTrack[square]
    return Offset(p.first + .5f, p.second + .5f)
}

@Composable
fun LudoGamePanel(
    networkMatch: LudoNetworkMatch?,
    incomingInvite: GameInvite?,
    nearbyConnected: Boolean,
    onInviteNearby: () -> Unit,
    onAcceptInvite: () -> Unit,
    onRejectInvite: () -> Unit,
    onNetworkAction: (String, Int) -> Unit,
    gameChat: List<String>,
    onGameChat: (String) -> Unit,
    onEndMatch: () -> Unit
) {
    var localGame by remember { mutableStateOf(LudoEngine.newGame(
        mapOf(LudoColor.RED to "You", LudoColor.GREEN to "Nova", LudoColor.YELLOW to "Atlas", LudoColor.BLUE to "Mira"),
        setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
    )) }
    var message by remember { mutableStateOf("Roll a six to bring your first token out.") }
    var rolling by remember { mutableStateOf(false) }
    var gameText by remember { mutableStateOf("") }
    var networkActionPending by remember(networkMatch?.matchId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val game = networkMatch?.state ?: localGame
    val current = game.currentPlayer
    val moves = LudoEngine.legalMoves(game)
    val networkMode = networkMatch != null
    val ready = networkMatch?.ready ?: true
    val myTurn = if (networkMode) ready && current.color == networkMatch!!.localColor else !current.isComputer
    val diceRotation by animateFloatAsState(if (rolling) 360f else 0f, spring(dampingRatio = .45f, stiffness = 500f), label = "diceRotation")

    LaunchedEffect(networkMatch?.state?.turnNumber, networkMatch?.state?.currentPlayerIndex, networkMatch?.state?.diceValue, networkMatch?.state?.winner) {
        networkActionPending = false
    }

    fun requestNetworkAction(action: String, token: Int) {
        if (networkActionPending) return
        networkActionPending = true
        onNetworkAction(action, token)
        scope.launch {
            delay(4000)
            networkActionPending = false
        }
    }

    LaunchedEffect(networkMatch, game.currentPlayerIndex, game.diceValue, game.turnNumber, game.winner, game.consecutiveSixes) {
        if (!networkMode && !game.isFinished && game.currentPlayer.isComputer) {
            delay(750)
            if (localGame.diceValue == null) {
                localGame = LudoEngine.rollDice(localGame)
                message = "${current.name} rolled ${localGame.diceValue}"
            } else {
                val choice = LudoEngine.chooseComputerMove(localGame)
                if (choice == null) {
                    localGame = LudoEngine.passTurn(localGame)
                    message = if (localGame.consecutiveSixes >= 3) "${current.name} rolled three sixes — turn forfeited" else "${current.name} has no legal move"
                } else {
                    val result = LudoEngine.moveToken(localGame, choice)
                    localGame = result.state
                    message = when {
                        result.state.isFinished -> "${current.name} wins the match!"
                        result.captured.isNotEmpty() -> "${current.name} captured a token — bonus roll!"
                        result.tokenFinished -> "${current.name} brought a token home!"
                        else -> "${current.name} moved token ${choice + 1}"
                    }
                }
            }
        }
    }

    Surface(color = LudoNavy, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("NEARER • GAME ZONE", color = LudoGold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    Text("LUDO CLASSIC", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(if (networkMode) "PRIVATE NEARBY MATCH" else "Four players • One champion", color = Color(0xFFAFC1D4), fontSize = 10.sp)
                }
                Surface(color = Color(0xFF234463), shape = RoundedCornerShape(30.dp)) {
                    Text(if (networkMode) "BLUETOOTH / WI-FI" else "OFFLINE VS BOTS", color = Color(0xFF9FE6CD), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
                }
            }

            if (incomingInvite != null && networkMatch == null) {
                Surface(color = Color(0xFF244B43), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${incomingInvite.sender} invited you to a Ludo match.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAcceptInvite, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = LudoGold, contentColor = LudoNavy)) { Text("ACCEPT") }
                            Button(onClick = onRejectInvite, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38536A))) { Text("DECLINE") }
                        }
                    }
                }
            }

            LudoBoard(game = game, legalMoves = if (myTurn) moves else emptyList(), onMove = { index ->
                if (networkMode) requestNetworkAction("move", index) else {
                    val result = LudoEngine.moveToken(localGame, index)
                    localGame = result.state
                    message = when {
                        result.state.isFinished -> "Champion! You brought all four tokens home."
                        result.captured.isNotEmpty() -> "Capture! ${result.captured.size} opponent token sent home — bonus roll."
                        result.tokenFinished -> "Token reached the centre. Roll again!"
                        else -> "Token ${index + 1} moved."
                    }
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(1f))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (game.isFinished) "${game.winner?.name} TAKES THE CROWN" else "${current.name.uppercase()}'S TURN", color = current.color.paint(), fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(when {
                        networkMode && !ready && networkMatch!!.isHost -> "Waiting for your opponent to accept…"
                        networkMode && !ready -> "Waiting for the host to synchronise the board…"
                        networkActionPending -> "Sending your move securely to the other phone…"
                        networkMode && !myTurn -> "Waiting for ${current.name} to play…"
                        else -> message
                    }, color = Color(0xFFD2DFEA), fontSize = 10.sp, lineHeight = 14.sp)
                }
                DiceFace(value = game.diceValue ?: 0, rotation = diceRotation)
            }
            if (!game.isFinished && myTurn && ready && game.diceValue == null) {
                Button(onClick = {
                    if (networkMode) requestNetworkAction("roll", -1) else {
                        rolling = true
                        scope.launch {
                            delay(380)
                            localGame = LudoEngine.rollDice(localGame)
                            rolling = false
                            message = if (localGame.consecutiveSixes >= 3) "Third six! Your turn is forfeited." else "You rolled ${localGame.diceValue}. Choose a glowing token."
                        }
                    }
                }, enabled = !rolling && !networkActionPending, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LudoGold, contentColor = LudoNavy)) {
                    Text(if (rolling) "ROLLING…" else "ROLL THE DICE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            } else if (!game.isFinished && myTurn && ready && game.diceValue != null) {
                if (moves.isEmpty()) {
                    Text(if (game.consecutiveSixes >= 3) "Three sixes in a row — your turn ends." else "No legal move for this roll.", color = LudoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        if (networkMode) requestNetworkAction("pass", -1) else localGame = LudoEngine.passTurn(localGame)
                    }, enabled = !networkActionPending, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF31516F))) { Text("CONTINUE") }
                } else {
                    Text("Tap one of the glowing tokens on the board to move it.", color = LudoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                game.players.forEach { player ->
                    val active = player.color == current.color && !game.isFinished
                    Surface(color = if (active) Color(0xFF294967) else Color(0xFF1A3048), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Surface(color = player.color.paint(), shape = CircleShape, modifier = Modifier.size(7.dp)) {}
                                Text(player.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Text("${player.finishedTokens}/4 HOME", color = if (player.finishedTokens == 4) LudoGold else Color(0xFFAFC1D4), fontSize = 8.sp)
                        }
                    }
                }
            }

            if (networkMode) {
                Text("MATCH CHAT", color = LudoGold, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Column(Modifier.fillMaxWidth().height(70.dp).background(Color(0xFF142A40), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    gameChat.takeLast(8).forEach { Text(it, color = Color.White, fontSize = 10.sp) }
                    if (gameChat.isEmpty()) Text("Say hello while you play.", color = Color(0xFF91A9BE), fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    androidx.compose.material3.OutlinedTextField(gameText, { gameText = it.take(300) }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Message your opponent") })
                    Button(onClick = { onGameChat(gameText); gameText = "" }, enabled = gameText.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = LudoGold, contentColor = LudoNavy)) { Text("SEND") }
                }
                Button(onClick = onEndMatch, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B3541))) { Text("LEAVE MATCH") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        localGame = LudoEngine.newGame(
                            mapOf(LudoColor.RED to "You", LudoColor.GREEN to "Nova", LudoColor.YELLOW to "Atlas", LudoColor.BLUE to "Mira"),
                            setOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)
                        )
                        message = "New game. Roll a six to bring your first token out."
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF31516F))) {
                        Text("NEW GAME", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onInviteNearby, enabled = nearbyConnected && incomingInvite == null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244B43))) {
                        Text(if (nearbyConnected) "INVITE NEARBY" else "CONNECT FIRST", color = Color(0xFF9FE6CD), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                if (!nearbyConnected) Text("Connect to another NeaRer phone above to invite them to a private no-data match.", color = Color(0xFF91A9BE), fontSize = 9.sp)
            }
            Text("CLASSIC RULES  •  SIX TO EXIT  •  SAFE STARS  •  CAPTURES  •  EXACT HOME FINISH", color = Color(0xFF8FA7BD), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .35.sp)
        }
    }
}

@Composable
private fun DiceFace(value: Int, rotation: Float) {
    Canvas(Modifier.size(54.dp)) {
        rotate(rotation) {
            drawRoundRect(Color(0xFFFFFEFA), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .2f))
            drawRoundRect(Color(0xFFD4DCE5), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .2f), style = Stroke(size.width * .035f))
            val r = size.width * .075f
            val points = when (value) {
                1 -> listOf(.5f to .5f)
                2 -> listOf(.28f to .28f, .72f to .72f)
                3 -> listOf(.28f to .28f, .5f to .5f, .72f to .72f)
                4 -> listOf(.28f to .28f, .72f to .28f, .28f to .72f, .72f to .72f)
                5 -> listOf(.28f to .28f, .72f to .28f, .5f to .5f, .28f to .72f, .72f to .72f)
                6 -> listOf(.28f to .25f, .72f to .25f, .28f to .5f, .72f to .5f, .28f to .75f, .72f to .75f)
                else -> emptyList()
            }
            points.forEach { (x, y) -> drawCircle(LudoNavy, r, Offset(size.width * x, size.height * y)) }
            if (value == 0) drawCircle(LudoGold, size.width * .12f, Offset(size.width / 2, size.height / 2), style = Stroke(size.width * .035f))
        }
    }
}

@Composable
private fun LudoBoard(game: LudoGameState, legalMoves: List<Int>, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    val animatedPositions = game.players.map { player ->
        player.tokens.mapIndexed { index, token ->
            key(player.color, index) {
                animateOffsetAsState(ludoTokenPosition(player.color, token, index), spring(dampingRatio = .72f, stiffness = 420f), label = "token-${player.color}-$index").value
            }
        }
    }
    Canvas(modifier
        .background(Color(0xFFF4F6FA), RoundedCornerShape(16.dp))
        .pointerInput(game, legalMoves) {
            detectTapGestures { tap ->
                if (game.currentPlayer.isComputer || game.diceValue == null) return@detectTapGestures
                val unit = size.width / 15f
                val playerIndex = game.currentPlayerIndex
                val candidates = legalMoves.map { index -> index to ludoTokenPosition(game.currentPlayer.color, game.currentPlayer.tokens[index], index) }
                val selected = candidates.minByOrNull { (_, p) -> hypot((tap.x / unit - p.x).toDouble(), (tap.y / unit - p.y).toDouble()) }
                if (selected != null && hypot((tap.x / unit - selected.second.x).toDouble(), (tap.y / unit - selected.second.y).toDouble()) < .85) onMove(selected.first)
            }
        }) {
        val unit = size.width / 15f
        val palette = LudoColor.entries.associateWith { it.paint() }
        drawRoundRect(Color(0xFFDFE5ED), cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * .25f))
        drawRect(Color(0xFFF8FAFC), Offset(unit * .25f, unit * .25f), Size(unit * 14.5f, unit * 14.5f))

        // Four raised home quarters.
        val quarters = listOf(
            Triple(0f, 0f, LudoColor.RED), Triple(9f, 0f, LudoColor.GREEN),
            Triple(9f, 9f, LudoColor.YELLOW), Triple(0f, 9f, LudoColor.BLUE)
        )
        quarters.forEach { (x, y, color) ->
            drawRoundRect(palette.getValue(color), Offset((x + .12f) * unit, (y + .12f) * unit), Size(5.76f * unit, 5.76f * unit), androidx.compose.ui.geometry.CornerRadius(unit * .45f))
            drawRoundRect(Color.White, Offset((x + .9f) * unit, (y + .9f) * unit), Size(4.2f * unit, 4.2f * unit), androidx.compose.ui.geometry.CornerRadius(unit * .35f))
            drawRoundRect(Color(0xFFE7ECF2), Offset((x + 1.05f) * unit, (y + 1.05f) * unit), Size(3.9f * unit, 3.9f * unit), androidx.compose.ui.geometry.CornerRadius(unit * .3f), style = Stroke(unit * .035f))
        }

        // The real 52-square clockwise race track.
        ludoTrack.forEachIndexed { index, (x, y) ->
            val color = when (index) {
                0 -> LudoColor.RED
                13 -> LudoColor.BLUE
                26 -> LudoColor.YELLOW
                39 -> LudoColor.GREEN
                else -> null
            }
            val fill = color?.let { palette.getValue(it) } ?: Color.White
            drawRoundRect(fill, Offset(x * unit + unit * .035f, y * unit + unit * .035f), Size(unit * .93f, unit * .93f), androidx.compose.ui.geometry.CornerRadius(unit * .12f))
            drawRoundRect(Color(0xFFD5DCE5), Offset(x * unit + unit * .035f, y * unit + unit * .035f), Size(unit * .93f, unit * .93f), androidx.compose.ui.geometry.CornerRadius(unit * .12f), style = Stroke(unit * .025f))
            if (index in setOf(8, 21, 34, 47)) {
                val center = Offset((x + .5f) * unit, (y + .5f) * unit)
                val star = Path()
                repeat(10) { point ->
                    val angle = -PI / 2.0 + point * PI / 5.0
                    val radius = if (point % 2 == 0) unit * .22f else unit * .10f
                    val px = center.x + cos(angle).toFloat() * radius
                    val py = center.y + sin(angle).toFloat() * radius
                    if (point == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }
                star.close()
                drawPath(star, Color(0xFFD5A33D))
            }
        }

        // Five-square private home columns for each colour.
        for (i in 0..4) {
            val lanes = listOf(
                Triple(7, 1 + i, LudoColor.RED),
                Triple(13 - i, 7, LudoColor.GREEN),
                Triple(7, 13 - i, LudoColor.YELLOW),
                Triple(1 + i, 7, LudoColor.BLUE)
            )
            lanes.forEach { (x, y, color) ->
                drawRoundRect(palette.getValue(color), Offset(x * unit + unit * .035f, y * unit + unit * .035f), Size(unit * .93f, unit * .93f), androidx.compose.ui.geometry.CornerRadius(unit * .1f))
                drawRoundRect(Color.White.copy(alpha = .55f), Offset(x * unit + unit * .035f, y * unit + unit * .035f), Size(unit * .93f, unit * .93f), androidx.compose.ui.geometry.CornerRadius(unit * .1f), style = Stroke(unit * .025f))
            }
        }

        // Four coloured wedges meet in the central home.
        val center = Offset(7.5f * unit, 7.5f * unit)
        val wedges = listOf(
            Triple(Offset(6f * unit, 6f * unit), Offset(9f * unit, 6f * unit), LudoColor.RED),
            Triple(Offset(9f * unit, 6f * unit), Offset(9f * unit, 9f * unit), LudoColor.GREEN),
            Triple(Offset(9f * unit, 9f * unit), Offset(6f * unit, 9f * unit), LudoColor.YELLOW),
            Triple(Offset(6f * unit, 9f * unit), Offset(6f * unit, 6f * unit), LudoColor.BLUE)
        )
        wedges.forEach { (a, b, color) ->
            val path = Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
            drawPath(path, palette.getValue(color))
        }
        drawCircle(LudoGold, unit * .42f, center)
        drawCircle(LudoNavy, unit * .29f, center, style = Stroke(unit * .07f))

        // Player tokens animate between exact board squares; finished tokens gather at home.
        game.players.forEachIndexed { p, player ->
            player.tokens.forEachIndexed { index, token ->
                val pos = animatedPositions[p][index]
                val isSelectable = player.color == game.currentPlayer.color && index in legalMoves
                val point = Offset(pos.x * unit, pos.y * unit)
                val sameSquare = player.tokens.withIndex().filter { it.value.steps == token.steps && token.steps >= 0 }.map { it.index }
                val stackSlot = sameSquare.indexOf(index).coerceAtLeast(0)
                val stackOffsets = listOf(Offset(-.13f, -.13f), Offset(.13f, -.13f), Offset(-.13f, .13f), Offset(.13f, .13f))
                val stackOffset = if (sameSquare.size > 1) stackOffsets[stackSlot % stackOffsets.size] else Offset.Zero
                val stackedPoint = point + Offset(stackOffset.x * unit, stackOffset.y * unit)
                if (isSelectable) {
                    drawCircle(LudoGold.copy(alpha = .35f), unit * .48f, stackedPoint)
                    drawCircle(LudoGold, unit * .48f, stackedPoint, style = Stroke(unit * .07f))
                }
                drawCircle(Color(0x550C1B2A), unit * .36f, stackedPoint + Offset(unit * .035f, unit * .07f))
                drawCircle(player.color.paint(), unit * .32f, stackedPoint)
                drawCircle(Color.White, unit * .24f, stackedPoint, style = Stroke(unit * .07f))
                drawCircle(Color.White.copy(alpha = .9f), unit * .07f, stackedPoint + Offset(-unit * .09f, -unit * .1f))
            }
        }
    }
}
