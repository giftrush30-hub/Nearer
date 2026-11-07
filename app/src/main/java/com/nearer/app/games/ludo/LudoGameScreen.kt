package com.nearer.app.games.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
fun LudoGameScreen(
    playerName: String,
    onExit: () -> Unit,
    connectedPeerName: String? = null,
    roomStatus: String = "Connect to a nearby player to invite them.",
    incomingInviteName: String? = null,
    roomPeerName: String? = null,
    roomReady: Boolean = false,
    roomMessages: List<String> = emptyList(),
    roomGameState: LudoDoubleDiceState? = null,
    roomIsHost: Boolean = false,
    roomCanAct: Boolean = false,
    onRoomAction: (LudoRoomAction) -> Unit = {},
    onInvitePeer: () -> Unit = {},
    onAcceptInvite: () -> Unit = {},
    onDeclineInvite: () -> Unit = {},
    onSendRoomChat: (String) -> Unit = {},
    onLeaveRoom: () -> Unit = {}
) {
    var roomChatDraft by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(4) }
    var computerOpponents by remember { mutableStateOf(true) }
    var doubleGame by remember(playerName) { mutableStateOf(LudoDoubleDiceState(LudoEngine.newGame(playerName, playerCount, computerOpponents = computerOpponents))) }
    var rolling by remember { mutableStateOf(false) }
    var rollingFirst by remember { mutableIntStateOf(1) }
    var rollingSecond by remember { mutableIntStateOf(1) }
    LaunchedEffect(roomReady, roomGameState) {
        if (roomReady && roomGameState != null) {
            doubleGame = roomGameState
            roomGameState.dice?.let { rollingFirst = it.first; rollingSecond = it.second }
        }
    }
    val game = doubleGame.game
    var soundEnabled by remember { mutableStateOf(true) }
    var aiDifficulty by remember { mutableStateOf(LudoAiDifficulty.MEDIUM) }
    var showRules by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val soundFx = remember(context) { LudoSoundEffects(context) }
    DisposableEffect(soundFx) { onDispose { soundFx.release() } }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Highlight legal moves only while a player can actually choose a token.
    // This avoids running an endless animation throughout setup and idle turns.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(game.legalTokenIndexes, game.isFinished) {
        if (game.legalTokenIndexes.isNotEmpty() && !game.isFinished) {
            while (true) {
                pulse.animateTo(0.72f, tween(850, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
            }
        } else {
            pulse.snapTo(1f)
        }
    }

    suspend fun animateRoll(computer: Boolean = false) {
        if (rolling || !LudoDoubleDiceEngine.canRoll(doubleGame)) return
        rolling = true
        if (soundEnabled && !computer) soundFx.play(LudoSound.ROLL)
        repeat(if (computer) 14 else 14) {
            rollingFirst = Random.nextInt(1, 7)
            rollingSecond = Random.nextInt(1, 7)
            delay(if (computer) 145 else 88)
        }
        if (roomReady) {
            if (roomCanAct) onRoomAction(LudoRoomAction(LudoRoomActionType.ROLL))
        } else doubleGame = LudoDoubleDiceEngine.roll(doubleGame)
        doubleGame.dice?.let { pair ->
            rollingFirst = pair.first
            rollingSecond = pair.second
        }
        rolling = false
        if (soundEnabled && doubleGame.dice != null) soundFx.play(LudoSound.LAND)
    }

    LaunchedEffect(game.winnerId) {
        if (game.winnerId != null && soundEnabled) {
            soundFx.play(LudoSound.WIN)
        }
    }

    fun moveToken(tokenIndex: Int) {
        if (roomReady) {
            if (roomCanAct) onRoomAction(LudoRoomAction(LudoRoomActionType.MOVE, tokenIndex = tokenIndex))
            return
        }
        val next = LudoDoubleDiceEngine.move(doubleGame, tokenIndex)
        if (soundEnabled && next != doubleGame) {
            val action = next.game.lastAction.lowercase()
            when {
                "captured" in action -> soundFx.play(LudoSound.CAPTURE)
                "home" in action -> soundFx.play(LudoSound.HOME)
                else -> soundFx.play(LudoSound.MOVE)
            }
        }
        doubleGame = next
    }

    LaunchedEffect(game.activePlayerIndex, doubleGame.turn, game.turnNumber, game.winnerId, game.message, showRules, roomReady) {
        if (!roomReady && !showRules && !game.isFinished && game.activePlayer.isComputer) {
            val turn = doubleGame.turn
            when {
                turn == null -> animateRoll(computer = true)
                turn.selected == null -> {
                    val slot = LudoTwoDiceRules.playableDice(game.activePlayer, turn).firstOrNull()
                    if (slot != null) {
                        delay(1800)
                        doubleGame = LudoDoubleDiceEngine.selectDie(doubleGame, slot)
                    }
                }
                else -> {
                    delay(2100)
                    LudoAiEngine.chooseComputerMove(game, aiDifficulty)?.let { moveToken(it) }
                }
            }
        }
    }

    if (showRules) {
        Dialog(onDismissRequest = { showRules = false }) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.7f)),
                shadowElevation = 18.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Text("HOW TO PLAY", color = Gold, fontSize = 11.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("TRADITIONAL LUDO", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text("01  •  Roll both dice together. Each die is a separate move.", color = White, fontSize = 12.sp)
                    Text("02  •  Use the dice separately on one or two tokens, or combine both numbers for one longer move.", color = White, fontSize = 12.sp)
                    Text("03  •  A token leaves its yard only when an individual die shows 6.", color = White, fontSize = 12.sp)
                    Text("04  •  Choose a die or combine both, then tap a highlighted token. You can also spend the dice separately.", color = White, fontSize = 12.sp)
                    Text("05  •  Safe squares protect tokens. You need an exact move to reach home. Combined moves are checked for legal distance.", color = White, fontSize = 12.sp)
                    Text("Double six earns another turn after both dice are resolved. First to bring all four tokens home wins.", color = Color(0xFFFFD98A),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { showRules = false },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A))
                    ) {
                        Text("LET'S PLAY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
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
                Surface(
                    color = Color(0xFF17365A),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.75f)),
                    modifier = Modifier.clickable { showRules = true }
                ) {
                    Text("RULES", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(6.dp))
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
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0B1D33))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("LIVE", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text(game.lastAction, color = Color(0xFFB8C9E1), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        game.players.forEach { player ->
                            val active = player.id == game.activePlayer.id
                            Surface(color = if (active) Color(0xFF244B80) else Color(0xFF0D1E35),
                                shape = RoundedCornerShape(11.dp),
                                border = BorderStroke(1.dp, if (active) Gold.copy(alpha = 0.8f) else Color(0xFF294362)),
                                modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(vertical = 7.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(player.name.take(8), color = if (active) White else Muted, fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                    Text(if (player.isComputer) "AI PLAYER" else "HUMAN", color = Color(player.color.hex), fontSize = 7.sp, fontWeight = FontWeight.Black)
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
                    LudoBoard(game, pulse.value, Modifier.fillMaxWidth()) { token ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        moveToken(token)
                    }
                }
            }

            if (game.isFinished) {
                val champion = game.players.firstOrNull { it.id == game.winnerId }?.name ?: "Player"
                Surface(
                    color = Color(0xFF18375D),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = pulse.value)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 19.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("✦  VICTORY  ✦", color = Gold, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                        VictoryConfetti(Modifier.fillMaxWidth().height(62.dp))
                        Text("🏆", fontSize = 38.sp)
                        Text(champion.uppercase(), color = White, fontSize = 23.sp,
                            fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("ROYAL CHAMPION", color = Color(0xFFFFE59A), fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                        Text("All four tokens have reached home.", color = Muted, fontSize = 11.sp)
                        Button(
                            onClick = { doubleGame = LudoDoubleDiceState(LudoEngine.newGame(playerName, playerCount, computerOpponents = computerOpponents)); rolling = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A))
                        ) {
                            Text("PLAY AGAIN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
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
                                doubleGame.turn == null -> "Tap either die to roll both together"
                                doubleGame.turn?.selected == null -> "Choose one of your remaining dice"
                                game.legalTokenIndexes.isEmpty() -> "No legal move with this die"
                                else -> "Choose a token to move"
                            }, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val pair = doubleGame.dice
                            DiceFace(if (rolling) rollingFirst else pair?.first ?: rollingFirst, rolling,
                                Modifier.size(56.dp).clickable(
                                    enabled = !rolling && !game.activePlayer.isComputer && (!roomReady || roomCanAct) &&
                                        (if (doubleGame.turn == null) LudoDoubleDiceEngine.canRoll(doubleGame)
                                        else doubleGame.turn?.selected == null && LudoDieSlot.FIRST in (doubleGame.turn?.remaining ?: emptySet()))
                                ) {
                                    if (doubleGame.turn == null) scope.launch { animateRoll() }
                                    else if (roomReady) { if (roomCanAct) onRoomAction(LudoRoomAction(LudoRoomActionType.SELECT_DIE, dieSlot = LudoDieSlot.FIRST)) } else doubleGame = LudoDoubleDiceEngine.selectDie(doubleGame, LudoDieSlot.FIRST)
                                })
                            DiceFace(if (rolling) rollingSecond else pair?.second ?: rollingSecond, rolling,
                                Modifier.size(56.dp).clickable(
                                    enabled = !rolling && !game.activePlayer.isComputer && (!roomReady || roomCanAct) &&
                                        (if (doubleGame.turn == null) LudoDoubleDiceEngine.canRoll(doubleGame)
                                        else doubleGame.turn?.selected == null && LudoDieSlot.SECOND in (doubleGame.turn?.remaining ?: emptySet()))
                                ) {
                                    if (doubleGame.turn == null) scope.launch { animateRoll() }
                                    else if (roomReady) { if (roomCanAct) onRoomAction(LudoRoomAction(LudoRoomActionType.SELECT_DIE, dieSlot = LudoDieSlot.SECOND)) } else doubleGame = LudoDoubleDiceEngine.selectDie(doubleGame, LudoDieSlot.SECOND)
                                })
                        }
                    }
                    if ((!roomReady || roomCanAct) && !game.activePlayer.isComputer && doubleGame.turn?.selected == null &&
                        LudoDieSlot.FIRST in (doubleGame.turn?.remaining ?: emptySet()) &&
                        LudoDieSlot.SECOND in (doubleGame.turn?.remaining ?: emptySet()) &&
                        LudoTwoDiceRules.legalTokenIndexes(game.activePlayer, doubleGame.turn!!.copy(selected = LudoDieSlot.BOTH)).isNotEmpty()) {
                        val pairTotal = (doubleGame.dice?.first ?: 0) + (doubleGame.dice?.second ?: 0)
                        Surface(
                            color = Color(0xFF263E5B),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.9f)),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !roomReady || roomCanAct) {
                                if (roomReady) onRoomAction(LudoRoomAction(LudoRoomActionType.SELECT_DIE, dieSlot = LudoDieSlot.BOTH))
                                else doubleGame = LudoDoubleDiceEngine.selectDie(doubleGame, LudoDieSlot.BOTH)
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("COMBINE BOTH DICE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    Text("Move one token $pairTotal spaces in one direction", color = White, fontSize = 11.sp)
                                }
                                Text("$pairTotal  ›", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { animateRoll() }
                        },
                            enabled = LudoDoubleDiceEngine.canRoll(doubleGame) && !game.activePlayer.isComputer && !rolling && (!roomReady || roomCanAct),
                            modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A),
                                disabledContainerColor = Color(0xFF59677B), disabledContentColor = Color(0xFFB8C3D1)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp)) {
                            Text(if (rolling) "ROLLING…" else if (LudoDoubleDiceEngine.canRoll(doubleGame)) "ROLL BOTH DICE" else if (doubleGame.turn?.selected != null) "MOVE WITH " + game.dice else "CHOOSE A DIE",
                                fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                        }
                        Button(onClick = { if (!roomReady) { doubleGame = LudoDoubleDiceState(LudoEngine.newGame(playerName, playerCount, computerOpponents = computerOpponents)); rolling = false } }, enabled = !roomReady,
                            modifier = Modifier.height(54.dp), shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25466F), contentColor = White),
                            contentPadding = PaddingValues(horizontal = 13.dp)) {
                            Text("NEW GAME", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                    if (game.activePlayer.isComputer && !game.isFinished) {
                        Text("NEARER BOT • THINKING", color = Color(0xFF9FC1FF), fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                    } else if (doubleGame.turn?.selected != null && game.legalTokenIndexes.isNotEmpty()) {
                        Text("SELECT YOUR TOKEN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            game.legalTokenIndexes.forEach { tokenIndex ->
                                Surface(color = Color(0xFF1B3B65), shape = RoundedCornerShape(13.dp),
                                    border = BorderStroke(1.dp, Gold.copy(alpha = pulse.value)),
                                    modifier = Modifier.weight(1f).clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        moveToken(tokenIndex)
                                    }) {
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
                    if (!game.isFinished) {
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
                        modifier = Modifier.clickable(enabled = !roomReady) {
                            computerOpponents = true
                            doubleGame = LudoDoubleDiceState(LudoEngine.newGame(playerName, playerCount, computerOpponents = true))
                        }) {
                        Text("VS COMPUTER", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp))
                    }
                    Surface(color = if (!computerOpponents) Cobalt else Panel, shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, if (!computerOpponents) Gold else Color(0xFF294362)),
                        modifier = Modifier.clickable(enabled = !roomReady) {
                            computerOpponents = false
                            doubleGame = LudoDoubleDiceState(LudoEngine.newGame(playerName, playerCount, computerOpponents = false))
                        }) {
                        Text("PASS & PLAY", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp))
                    }
                }
            }

            if (computerOpponents) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("COMPUTER SKILL", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LudoAiDifficulty.values().forEach { level ->
                            Surface(color = if (aiDifficulty == level) Cobalt else Panel, shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (aiDifficulty == level) Gold else Color(0xFF294362)),
                                modifier = Modifier.clickable { aiDifficulty = level }) {
                                Text(level.label, color = White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                            }
                        }
                    }
                }
                Text(aiDifficulty.description, color = Color(0xFF9FB5D2), fontSize = 10.sp)
            }

            Surface(color = Panel, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.48f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NEARBY MULTIPLAYER", color = Gold, fontSize = 10.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (roomReady) "ROOM ACTIVE" else if (roomPeerName != null) "INVITE PENDING" else "OFFLINE READY",
                            color = if (roomPeerName != null) Color(0xFF79E2B2) else Muted,
                            fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(roomStatus, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    if (incomingInviteName != null) {
                        Text("$incomingInviteName wants to play Ludo with you.",
                            color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onAcceptInvite, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A))) {
                                Text("ACCEPT", fontWeight = FontWeight.Black)
                            }
                            Button(onClick = onDeclineInvite, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = White)) {
                                Text("DECLINE", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (roomPeerName == null) {
                        Button(onClick = onInvitePeer, enabled = connectedPeerName != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt, contentColor = White)) {
                            Text(if (connectedPeerName != null) "INVITE $connectedPeerName" else "CONNECT A NEARBY PLAYER FIRST",
                                fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    } else if (!roomReady) {
                        Text("Waiting for $roomPeerName to accept the invitation.", color = White, fontSize = 11.sp)
                        Button(onClick = onLeaveRoom, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = White)) {
                            Text("CANCEL INVITATION", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    } else {
                        Text("Playing with $roomPeerName", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (roomMessages.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 130.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF091A2D)).padding(9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                roomMessages.takeLast(12).forEach { message ->
                                    Text(message, color = White, fontSize = 10.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = roomChatDraft, onValueChange = { roomChatDraft = it.take(300) },
                                modifier = Modifier.weight(1f), singleLine = true,
                                placeholder = { Text("Message your opponent…", color = Muted, fontSize = 10.sp) })
                            Button(onClick = {
                                onSendRoomChat(roomChatDraft)
                                roomChatDraft = ""
                            }, enabled = roomChatDraft.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF14233A))) {
                                Text("SEND", fontWeight = FontWeight.Black, fontSize = 10.sp)
                            }
                        }
                        Button(onClick = onLeaveRoom, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = White)) {
                            Text("LEAVE ROOM", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
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
                                doubleGame = LudoDoubleDiceState(LudoEngine.newGame(playerName, count, computerOpponents = computerOpponents))
                                rolling = false
                            }) {
                            Text(count.toString(), color = White, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (soundEnabled) Color(0xFF18375D) else Panel,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (soundEnabled) Gold.copy(alpha = 0.7f) else Color(0xFF294362)),
                    modifier = Modifier.clickable { soundEnabled = !soundEnabled }
                ) {
                    Text(
                        if (soundEnabled) "♫  GAME SOUNDS ON" else "♫  GAME SOUNDS OFF",
                        color = if (soundEnabled) Gold else Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                    )
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
    // Run the dice rotation only while a roll is in progress; an idle game
    // should not keep an infinite animation ticking in the background.
    val rotation = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(rolling) {
        if (rolling) {
            rotation.snapTo(0f)
            lift.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch {
                    rotation.animateTo(
                        targetValue = 1080f,
                        animationSpec = tween(760, easing = LinearEasing)
                    )
                }
                launch {
                    repeat(3) { bounce ->
                        lift.animateTo(
                            targetValue = (if (bounce == 0) 1f else 0.48f / bounce),
                            animationSpec = tween(if (bounce == 0) 170 else 115, easing = FastOutSlowInEasing)
                        )
                        lift.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.42f,
                                stiffness = if (bounce == 0) Spring.StiffnessMedium else Spring.StiffnessHigh
                            )
                        )
                    }
                }
            }
        } else {
            rotation.snapTo(0f)
            lift.snapTo(0f)
        }
    }
    Canvas(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation.value
                translationY = -lift.value * 15.dp.toPx()
                val scale = 1f + lift.value * 0.10f
                scaleX = scale
                scaleY = scale
            }
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = if (rolling) "Dice rolling" else "Dice showing $value" }
    ) {
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
        player.tokens.mapIndexed { tokenIndex, target ->
            val progress = remember(player.id, tokenIndex) { Animatable(target.toFloat()) }
            LaunchedEffect(player.id, tokenIndex, target) {
                val distance = abs(target.toFloat() - progress.value)
                if (distance > 0.001f) {
                    progress.animateTo(
                        targetValue = target.toFloat(),
                        animationSpec = tween(
                            durationMillis = (distance * 265f).toInt().coerceIn(520, 6500),
                            easing = LinearEasing
                        )
                    )
                }
            }
            progress
        }
    }
    Canvas(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Night)
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
            .semantics {
                contentDescription = if (game.legalTokenIndexes.isEmpty()) {
                    "Ludo board. ${game.activePlayer.name} turn. Roll the dice to begin a move."
                } else {
                    "Ludo board. ${game.legalTokenIndexes.size} legal token moves available. Tap a highlighted token to move."
                }
            }
    ) {
        val cell = size.width / 15f
        val colors = listOf(Color(0xFFE64B68), Color(0xFF19B58A), Color(0xFFFFC247), Color(0xFF347CF5))
        fun tile(x: Int, y: Int, color: Color) {
            val topLeft = Offset(x * cell + 1.5f, y * cell + 1.5f)
            val tileSize = Size(cell - 3f, cell - 3f)
            val radius = CornerRadius(cell * 0.13f)
            val shade = Color(
                red = (color.red * 0.72f).coerceIn(0f, 1f),
                green = (color.green * 0.72f).coerceIn(0f, 1f),
                blue = (color.blue * 0.72f).coerceIn(0f, 1f),
                alpha = color.alpha
            )
            drawRoundRect(
                Brush.linearGradient(
                    listOf(color.copy(red = (color.red * 1.12f).coerceIn(0f, 1f),
                        green = (color.green * 1.12f).coerceIn(0f, 1f),
                        blue = (color.blue * 1.12f).coerceIn(0f, 1f)), color, shade),
                    start = topLeft,
                    end = Offset(topLeft.x + cell, topLeft.y + cell)
                ),
                topLeft, tileSize, radius
            )
            drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft, tileSize, radius,
                style = Stroke(cell * 0.025f))
        }
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF28466D), Color(0xFF0D1E35))), cornerRadius = CornerRadius(cell * 0.3f))
        drawRoundRect(Color(0xFF8FB8F2).copy(alpha = 0.16f), cornerRadius = CornerRadius(cell * 0.3f), style = Stroke(cell * 0.10f))
        for (y in 0..5) for (x in 0..5) tile(x, y, Color(0xFF3B2036))
        for (y in 9..14) for (x in 0..5) tile(x, y, Color(0xFF173D3B))
        for (y in 9..14) for (x in 9..14) tile(x, y, Color(0xFF49391F))
        for (y in 0..5) for (x in 9..14) tile(x, y, Color(0xFF1D3458))
        val yards = listOf(Offset(0f, 0f), Offset(0f, 9f), Offset(9f, 9f), Offset(9f, 0f))
        yards.forEachIndexed { i, p ->
            val yardTopLeft = Offset((p.x + 1f) * cell, (p.y + 1f) * cell)
            drawRoundRect(Color(0xFF172A45).copy(alpha = 0.10f),
                Offset(yardTopLeft.x, yardTopLeft.y + cell * 0.09f),
                Size(4f * cell, 4f * cell), CornerRadius(cell * 0.42f))
            drawRoundRect(
                Brush.linearGradient(listOf(colors[i].copy(alpha = 0.28f), colors[i].copy(alpha = 0.12f))),
                yardTopLeft, Size(4f * cell, 4f * cell), CornerRadius(cell * 0.42f)
            )
            drawRoundRect(Color(0xFFEAF1FC).copy(alpha = 0.98f),
                Offset(yardTopLeft.x + cell * 0.48f, yardTopLeft.y + cell * 0.48f),
                Size(3.04f * cell, 3.04f * cell), CornerRadius(cell * 0.32f))
            drawRoundRect(colors[i].copy(alpha = 0.92f),
                Offset(yardTopLeft.x + cell * 0.48f, yardTopLeft.y + cell * 0.48f),
                Size(3.04f * cell, 3.04f * cell), CornerRadius(cell * 0.32f),
                style = Stroke(cell * 0.045f))
            ludoBaseSpots[i].forEach { (x, y) ->
                val socket = Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                drawCircle(colors[i].copy(alpha = 0.14f), cell * 0.39f, socket)
                drawCircle(colors[i].copy(alpha = 0.8f), cell * 0.34f, socket, style = Stroke(cell * 0.055f))
                drawCircle(Color(0xFF172A45), cell * 0.25f, socket)
            }
        }
        ludoTrackCells.forEachIndexed { index, point ->
            val safe = LudoEngine.isSafeSquare(index)
            val start = index == 0 || index == 13 || index == 26 || index == 39
            tile(point.first, point.second, when {
                start -> colors[index / 13]
                safe -> Color(0xFFFFD96A)
                else -> Color(0xFFEAF0FA)
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
        val centerPoint = Offset(7.5f * cell, 7.5f * cell)
        val centerWedges = listOf(
            Triple(Offset(6f * cell, 6f * cell), Offset(9f * cell, 6f * cell), colors[0]),
            Triple(Offset(9f * cell, 6f * cell), Offset(9f * cell, 9f * cell), colors[3]),
            Triple(Offset(9f * cell, 9f * cell), Offset(6f * cell, 9f * cell), colors[2]),
            Triple(Offset(6f * cell, 9f * cell), Offset(6f * cell, 6f * cell), colors[1])
        )
        centerWedges.forEach { (first, second, color) ->
            val wedge = Path().apply {
                moveTo(first.x, first.y)
                lineTo(second.x, second.y)
                lineTo(centerPoint.x, centerPoint.y)
                close()
            }
            drawPath(wedge, color)
        }
        val center = Path().apply {
            moveTo(7.5f * cell, 6f * cell); lineTo(9f * cell, 7.5f * cell)
            lineTo(7.5f * cell, 9f * cell); lineTo(6f * cell, 7.5f * cell); close()
        }
        drawPath(center, Gold, style = Stroke(cell * 0.075f))
        drawCircle(Color(0xFFFFE99A), cell * 0.13f, centerPoint)
        drawCircle(Color(0xFF102746), cell * 0.055f, centerPoint)

        game.players.forEachIndexed { pIndex, player ->
            player.tokens.forEachIndexed { tokenIndex, target ->
                val point = positionForProgress(player.color, tokenIndex, animated[pIndex][tokenIndex].value)
                val x = (point.first + 0.5f) * cell
                val y = (point.second + 0.5f) * cell
                val tokenColor = colors[player.color.ordinal]
                if (player.id == game.activePlayer.id && tokenIndex in game.legalTokenIndexes) {
                    drawCircle(Gold.copy(alpha = 0.2f * pulse), cell * 0.49f, Offset(x, y))
                    drawCircle(Gold.copy(alpha = pulse), cell * 0.4f, Offset(x, y), style = Stroke(cell * 0.055f))
                }
                // Sculpted pawn silhouette: shadow, tapered body, raised head and enamel highlight.
                drawOval(
                    Color(0xFF061326).copy(alpha = 0.58f),
                    Offset(x - cell * 0.24f, y + cell * 0.19f),
                    Size(cell * 0.48f, cell * 0.18f)
                )
                val pawn = Path().apply {
                    moveTo(x - cell * 0.19f, y + cell * 0.19f)
                    cubicTo(x - cell * 0.18f, y + cell * 0.02f, x - cell * 0.09f, y - cell * 0.015f, x - cell * 0.105f, y - cell * 0.12f)
                    cubicTo(x - cell * 0.12f, y - cell * 0.28f, x + cell * 0.12f, y - cell * 0.28f, x + cell * 0.105f, y - cell * 0.12f)
                    cubicTo(x + cell * 0.09f, y - cell * 0.015f, x + cell * 0.18f, y + cell * 0.02f, x + cell * 0.19f, y + cell * 0.19f)
                    close()
                }
                drawPath(pawn, Color(0xFF07162B).copy(alpha = 0.72f))
                drawPath(
                    pawn,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.82f), tokenColor, tokenColor.copy(red = (tokenColor.red * 0.62f).coerceIn(0f, 1f))),
                        start = Offset(x - cell * 0.2f, y - cell * 0.2f),
                        end = Offset(x + cell * 0.2f, y + cell * 0.2f)
                    )
                )
                drawCircle(Color(0xFFFFF8E3), cell * 0.105f, Offset(x, y - cell * 0.19f))
                drawCircle(Color.White.copy(alpha = 0.76f), cell * 0.045f, Offset(x - cell * 0.045f, y - cell * 0.225f))
            }
        }
    }
}


@Composable
private fun VictoryConfetti(modifier: Modifier = Modifier) {
    val fall by rememberInfiniteTransition(label = "victory-confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "confetti-fall"
    )
    Canvas(modifier) {
        val palette = listOf(Gold, Color(0xFF77B7FF), Color(0xFFFF7894), Color(0xFF7CE0B7), White)
        repeat(34) { index ->
            val x = ((index * 37 + 11) % 97) / 100f * size.width
            val phase = (fall + index * 0.071f) % 1f
            val y = phase * size.height
            val radius = (2.2f + (index % 4) * 0.9f).dp.toPx()
            val color = palette[index % palette.size].copy(alpha = 0.95f - phase * 0.35f)
            if (index % 3 == 0) {
                drawRoundRect(
                    color,
                    topLeft = Offset(x, y),
                    size = Size(radius * 0.8f, radius * 2.2f),
                    cornerRadius = CornerRadius(radius * 0.3f)
                )
            } else {
                drawCircle(color, radius, Offset(x, y))
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
    if (progress >= 56f) {
        // Give each completed token its own visible royal-home slot instead of
        // drawing all four directly on top of one another.
        return when (tokenIndex) {
            0 -> 7.12f to 7.12f
            1 -> 7.88f to 7.12f
            2 -> 7.12f to 7.88f
            else -> 7.88f to 7.88f
        }
    }
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
