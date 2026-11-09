package com.nearer.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NeaRerAvatarChoice(val id: String, val glyph: String, val label: String, val tint: Color)

private val NeaRerAvatarChoices = listOf(
    NeaRerAvatarChoice("aurora", "✦", "Aurora", Color(0xFF285D91)),
    NeaRerAvatarChoice("spark", "✧", "Spark", Color(0xFF7655A5)),
    NeaRerAvatarChoice("wave", "≈", "Wave", Color(0xFF247C88)),
    NeaRerAvatarChoice("sun", "☼", "Sunrise", Color(0xFF9A6A28)),
    NeaRerAvatarChoice("orbit", "◉", "Orbit", Color(0xFF425C86)),
    NeaRerAvatarChoice("bloom", "❋", "Bloom", Color(0xFF9A5274)),
    NeaRerAvatarChoice("leaf", "❧", "Leaf", Color(0xFF47745A)),
    NeaRerAvatarChoice("star", "✴", "Starlight", Color(0xFF6B629D))
)

private data class NeaRerOnboardingQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>
)

private val NeaRerOnboardingQuestions = listOf(
    NeaRerOnboardingQuestion("personality", "Which words describe your personality?", listOf("Calm", "Adventurous", "Creative", "Funny", "Thoughtful", "A little of everything")),
    NeaRerOnboardingQuestion("reason", "What brings you to NeaRer?", listOf("Friendship", "Chatting", "Sharing files", "Communities", "Games", "A bit of everything")),
    NeaRerOnboardingQuestion("interests", "What are you interested in?", listOf("Music", "Technology", "Sports", "Art", "Learning", "Movies")),
    NeaRerOnboardingQuestion("values", "What matters most to you here?", listOf("Privacy", "Real connection", "Creativity", "Community", "Freedom", "Simplicity")),
    NeaRerOnboardingQuestion("free_time", "What do you enjoy in your free time?", listOf("Music", "Reading", "Gaming", "Creating", "Exploring", "Relaxing")),
    NeaRerOnboardingQuestion("welcome", "How should NeaRer welcome you?", listOf("Warm and friendly", "Simple and quiet", "Fun and lively", "Surprise me"))
)

@Composable
internal fun NeaRerOnboarding(
    publicId: String,
    onComplete: (String, String, Uri?, Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(0) }
    var displayName by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf("orbit") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val answers = remember { mutableStateMapOf<String, String>() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) photoUri = uri
    }
    val photoBitmap = remember(photoUri) {
        photoUri?.let { uri -> runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() }
    }
    val pages = listOf("Welcome", "Your name", "A little about you", "Choose your look", "Your NeaRer ID")
    val cleanName = displayName.trim().replace(Regex("\\s+"), " ").take(24)
    val canContinue = page != 1 || cleanName.isNotBlank()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C1728)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Nea", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("R", color = Color(0xFFFFD12D), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("er", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("GETTING STARTED", color = Color(0xFF9FB2CC), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                pages.forEachIndexed { index, _ ->
                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (index <= page) Color(0xFFFFD12D) else Color(0xFF26374D)))
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("STEP ${page + 1} OF 5", color = Color(0xFFFFD12D), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(9.dp))
            Text(
                when (page) {
                    0 -> "Closer begins here."
                    1 -> "What should we call you?"
                    2 -> "A little about you."
                    3 -> "Make it yours."
                    else -> "Your identity is ready."
                },
                color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (page) {
                    0 -> "A closer way to chat, share and belong — even when mobile data is unavailable."
                    1 -> "Choose the name people will see around NeaRer."
                    2 -> "These optional answers help shape a more welcoming experience."
                    3 -> "Choose an avatar, add a photo, or skip for now."
                    else -> "This is your permanent public NeaRer ID. Keep it safe."
                },
                color = Color(0xFFAAB9CC), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(28.dp))

            when (page) {
                0 -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF142A45)).padding(vertical = 30.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("✦", color = Color(0xFFFFD12D), fontSize = 56.sp)
                            Text("NeaRer", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Text("No data? We are NeaRer.", color = Color(0xFFFFD12D), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your people. Your space. Your way to stay connected.", color = Color(0xFFB9CBE1), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                        }
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Your display name") },
                        placeholder = { Text("e.g. Gift, Daniel, Zee…") },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("You can change this whenever you like. No phone number or email needed.", color = Color(0xFF8195AF), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                2 -> {
                    NeaRerOnboardingQuestions.forEach { question ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 17.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(question.prompt, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            question.options.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { option ->
                                        FilterChip(
                                            selected = answers[question.id] == option,
                                            onClick = { if (answers[question.id] == option) answers.remove(question.id) else answers[question.id] = option },
                                            label = { Text(option, fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Text("Every question is optional. You can skip this whole step.", color = Color(0xFF8195AF), fontSize = 12.sp)
                }
                3 -> {
                    if (photoBitmap != null) {
                        Image(photoBitmap.asImageBitmap(), contentDescription = "Selected profile photo", modifier = Modifier.size(112.dp).clip(CircleShape))
                        Spacer(Modifier.height(10.dp))
                        Text("Photo selected", color = Color(0xFFB9D7FF), fontSize = 13.sp)
                    } else {
                        val selected = NeaRerAvatarChoices.first { it.id == avatarId }
                        Box(
                            modifier = Modifier.size(112.dp).clip(CircleShape).background(selected.tint),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(selected.glyph, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("CHOOSE AN AVATAR", color = Color(0xFF9FB2CC), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(12.dp))
                    NeaRerAvatarChoices.chunked(4).forEach { rowChoices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            rowChoices.forEach { choice ->
                                Column(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                        .background(if (avatarId == choice.id && photoUri == null) Color(0xFF263D5B) else Color(0xFF17263A))
                                        .clickable { avatarId = choice.id; photoUri = null }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(choice.tint), contentAlignment = Alignment.Center) {
                                        Text(choice.glyph, color = Color.White, fontSize = 25.sp)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(choice.label, color = Color(0xFFDCE7F5), fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { photoPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244B78)),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (photoUri == null) "Upload a profile photo" else "Choose a different photo") }
                    Text("Or skip this step. Your avatar can be changed later.", color = Color(0xFF8195AF), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF142A45)).padding(vertical = 28.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✦", color = Color(0xFFFFD12D), fontSize = 35.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(cleanName.ifBlank { "NeaRer friend" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(publicId.ifBlank { "Preparing your ID…" }, color = Color(0xFFFFD12D), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("YOUR UNIQUE NEARER ID", color = Color(0xFF9FB2CC), fontSize = 10.sp, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Your ID helps people find and recognise you nearby. It is not a password or a recovery key.", color = Color(0xFF8195AF), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) {
                    Button(
                        onClick = { page -= 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26374D)),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (page < 4) page += 1
                        else onComplete(cleanName, avatarId, photoUri, answers.toMap())
                    },
                    enabled = canContinue,
                    modifier = Modifier.weight(if (page > 0) 1f else 1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD12D), contentColor = Color(0xFF152238)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (page == 3) "Enter NeaRer" else "Continue", fontWeight = FontWeight.Bold)
                }
            }
            if (page == 2) {
                Text("Skip questions", modifier = Modifier.padding(top = 14.dp).clickable { page = 3 }, color = Color(0xFFB8C8DD), fontSize = 13.sp)
            }
            if (page == 3) {
                Text("Skip for now", modifier = Modifier.padding(top = 14.dp).clickable { photoUri = null; page = 4 }, color = Color(0xFFB8C8DD), fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("No data required to create your local identity.", color = Color(0xFF71849E), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
