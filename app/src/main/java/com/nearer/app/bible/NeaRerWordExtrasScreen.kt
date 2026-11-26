package com.nearer.app.bible

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_OFFLINE_AUDIO_IMPORT_BYTES = 256L * 1024L * 1024L

private data class WordPlanDay(val title: String, val reading: String, val reflection: String)
private data class WordReadingPlan(val id: String, val title: String, val subtitle: String, val days: List<WordPlanDay>)

/** Original short plans. References are prompts; completion is private and stored on this device. */
private val wordReadingPlans = listOf(
    WordReadingPlan("beginnings", "Beginning with God", "A seven-day foundation for knowing God", listOf(
        WordPlanDay("God creates", "Genesis 1:1–31", "Notice what the passage says about God's character and creation."),
        WordPlanDay("Made in His image", "Genesis 2:4–25", "What does this passage say about human dignity and responsibility?"),
        WordPlanDay("The promise", "Genesis 12:1–9", "Where do you see trust and obedience in Abram's response?"),
        WordPlanDay("God hears", "Exodus 3:1–15", "What does God's name and response reveal about Him?"),
        WordPlanDay("The way of wisdom", "Proverbs 3:1–12", "Which instruction can you practise today?"),
        WordPlanDay("A new heart", "Psalm 51:1–17", "Turn the words of this prayer into your own honest prayer."),
        WordPlanDay("The Word among us", "John 1:1–18", "What does this passage teach about Jesus?")
    )),
    WordReadingPlan("prayer", "Learning to Pray", "Seven days of honest, Scripture-shaped prayer", listOf(
        WordPlanDay("The Lord's prayer", "Matthew 6:5–15", "Which part of this prayer do you most need today?"),
        WordPlanDay("Ask with trust", "Matthew 7:7–11", "How does Jesus describe the Father's care?"),
        WordPlanDay("Pray in trouble", "Psalm 13", "Name your concern honestly, as the psalmist does."),
        WordPlanDay("Give thanks", "Philippians 4:4–9", "Turn one anxiety into a specific prayer."),
        WordPlanDay("Pray continually", "1 Thessalonians 5:12–24", "Choose one practical way to keep prayer present."),
        WordPlanDay("Pray for others", "Ephesians 3:14–21", "Who can you pray for using Paul's example?"),
        WordPlanDay("Jesus withdraws to pray", "Luke 5:12–16", "What space can you make for quiet prayer?")
    )),
    WordReadingPlan("wisdom", "Wisdom for Daily Life", "Seven days of practical biblical wisdom", listOf(
        WordPlanDay("The fear of the Lord", "Proverbs 1:1–7", "What is the starting point of wisdom?"),
        WordPlanDay("Guard your heart", "Proverbs 4:20–27", "What influences do you need to guard against?"),
        WordPlanDay("Words that build", "James 3:1–12", "How can your words bring life today?"),
        WordPlanDay("Listen first", "James 1:19–27", "Where do you need to listen before reacting?"),
        WordPlanDay("Choose humility", "Philippians 2:1–11", "How does Jesus redefine greatness?"),
        WordPlanDay("Work faithfully", "Colossians 3:12–24", "What ordinary task can you do wholeheartedly?"),
        WordPlanDay("Walk wisely", "Ephesians 5:1–17", "What would making the most of today look like?")
    )),
    WordReadingPlan("hope", "Hope in Difficult Seasons", "Seven days of comfort and perseverance", listOf(
        WordPlanDay("The Shepherd", "Psalm 23", "Which image in this psalm gives you comfort?"),
        WordPlanDay("Help in trouble", "Psalm 46", "What does it mean to be still before God?"),
        WordPlanDay("Come to Jesus", "Matthew 11:25–30", "What burden can you bring to Jesus?"),
        WordPlanDay("Nothing separates us", "Romans 8:18–39", "Which promise in this passage stands out?"),
        WordPlanDay("Strength renewed", "Isaiah 40:27–31", "Where do you need renewed strength?"),
        WordPlanDay("Peace in Christ", "John 14:1–7, 25–27", "What does Jesus promise His disciples?"),
        WordPlanDay("A future hope", "Revelation 21:1–7", "What future does God promise?")
    ))
)

@Composable
fun NeaRerWordExtrasScreen(onBack: () -> Unit, onOpenReading: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("nearer_word_plans", android.content.Context.MODE_PRIVATE) }
    var selectedPlan by remember { mutableStateOf<WordReadingPlan?>(null) }
    var completed by remember { mutableStateOf(prefs.getStringSet("completed_days", emptySet())?.toSet().orEmpty()) }
    var audioFile by remember { mutableStateOf(prefs.getString("audio_file", null)?.let { File(context.filesDir, "nearer-word-audio/" + it) }?.takeIf { it.exists() }) }
    var audioLabel by remember { mutableStateOf(audioFile?.name ?: "No audio imported") }
    var isPlaying by remember { mutableStateOf(false) }
    var audioNotice by remember { mutableStateOf("Import an audio Bible file you have permission to use. It will be copied into private app storage for offline playback.") }
    val player = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && audioFile?.exists() == true) {
                    prefs.edit().putLong("audio_position_ms", player.currentPosition).apply()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    prefs.edit().putLong("audio_position_ms", 0L).apply()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (audioFile?.exists() == true) {
                prefs.edit().putLong("audio_position_ms", player.currentPosition).apply()
            }
            player.release()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            audioNotice = "Importing audio for offline use…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val folder = File(context.filesDir, "nearer-word-audio").apply { mkdirs() }
                        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "audio"
                        val stamp = System.currentTimeMillis()
                        val temporary = File(folder, "import-" + stamp + ".part")
                        val destination = File(folder, "audio-" + stamp + "." + extension)
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                temporary.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= MAX_OFFLINE_AUDIO_IMPORT_BYTES) {
                                            "Audio import exceeds the 256 MB per-file limit."
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                    require(total > 0L) { "The selected audio file is empty." }
                                }
                            } ?: error("The selected audio file could not be opened.")
                            check(temporary.renameTo(destination)) { "Could not finish saving the audio file." }
                            destination
                        } catch (error: Throwable) {
                            temporary.delete()
                            destination.delete()
                            throw error
                        }
                    }
                }
                result.onSuccess { file ->
                    player.stop()
                    audioFile?.takeIf { it != file }?.delete()
                    audioFile = file
                    prefs.edit().putString("audio_file", file.name).putLong("audio_position_ms", 0L).apply()
                    audioLabel = file.name
                    audioNotice = "Audio imported into private app storage. It is ready for offline playback."
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    player.prepare()
                }.onFailure { audioNotice = "Audio import failed: " + (it.message ?: "unknown error") }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NEARER WORD · STUDY", color = Color(0xFFFFD12D), fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("BACK", color = Color.White) }
        }
        if (selectedPlan == null) {
            Text("Grow through the Word", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Short, practical reading journeys. Your progress stays on this phone.", color = Color(0xFFA8B6C9), fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(wordReadingPlans, key = { it.id }) { plan ->
                    val done = plan.days.indices.count { completed.contains(plan.id + ":" + it) }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F0E3))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(plan.title, color = Color(0xFF15233A), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(plan.subtitle, color = Color(0xFF46566D), fontSize = 12.sp)
                            Text("$done of 7 days completed", color = Color(0xFF46566D), fontSize = 11.sp)
                            Button(onClick = { selectedPlan = plan }) { Text("Open plan") }
                        }
                    }
                }
            }
        } else {
            val plan = selectedPlan!!
            Text(plan.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Mark a day complete after reading. You can pause and return whenever you like.", color = Color(0xFFA8B6C9), fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plan.days.size) { index ->
                    val day = plan.days[index]
                    val key = plan.id + ":" + index
                    val checked = completed.contains(key)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("DAY ${index + 1} · ${day.title}", color = Color(0xFF15233A), fontWeight = FontWeight.Bold)
                                Checkbox(checked = checked, onCheckedChange = { value ->
                                    completed = if (value) completed + key else completed - key
                                    prefs.edit().putStringSet("completed_days", completed).apply()
                                })
                            }
                            Text(day.reading, color = Color(0xFF8A5B00), fontWeight = FontWeight.SemiBold)
                            Text(day.reflection, color = Color(0xFF46566D), fontSize = 12.sp)
                            TextButton(onClick = { onOpenReading(day.reading) }) { Text("Open passage in Bible", color = Color(0xFF8A5B00)) }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { selectedPlan = null }) { Text("All reading plans") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("OFFLINE AUDIO BIBLE", color = Color(0xFF15233A), fontWeight = FontWeight.Bold)
                Text(audioLabel, color = Color(0xFF46566D), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { audioPicker.launch(arrayOf("audio/*", "application/octet-stream")) }) {
                        Text("Import audio")
                    }
                    OutlinedButton(enabled = audioFile?.exists() == true, onClick = {
                        val file = audioFile
                        if (file != null && file.exists()) {
                            if (player.isPlaying) player.pause()
                            else {
                                if (player.currentMediaItem == null) {
                                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                                    player.prepare()
                                    player.seekTo(prefs.getLong("audio_position_ms", 0L))
                                } else if (player.playbackState == Player.STATE_IDLE) {
                                    player.prepare()
                                    player.seekTo(prefs.getLong("audio_position_ms", 0L))
                                } else if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0L)
                                }
                                player.play()
                            }
                        }
                    }) { Text(if (isPlaying) "Pause" else "Play") }
                    OutlinedButton(enabled = audioFile?.exists() == true, onClick = {
                        player.pause()
                        prefs.edit().putLong("audio_position_ms", player.currentPosition).apply()
                        player.seekTo(0L)
                        prefs.edit().putLong("audio_position_ms", 0L).apply()
                        player.stop()
                        isPlaying = false
                    }) { Text("Stop") }
                }
                Text(audioNotice, color = Color(0xFF46566D), fontSize = 10.sp)
                Text("Audio is imported from a file you choose; NeaRer does not download or redistribute recordings automatically.", color = Color(0xFF6A788C), fontSize = 10.sp)
            }
        }
    }
}
