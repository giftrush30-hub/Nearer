package com.nearer.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class StudioLesson(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
    val practice: String
)

/** Short, fully offline lessons. Keep the words suitable for a first-time editor. */
internal object StudioFilmSchoolLessons {
    val all = listOf(
        StudioLesson(
            id = "first-movie",
            title = "Make your first movie",
            summary = "Bring a few phone videos together into one movie.",
            steps = listOf(
                "Open NeaRer Studio and choose “Make a movie with clips”.",
                "Pick the videos you want to use. Choose the clips in the order you want them to play.",
                "Watch the preview. Your clips play one after another.",
                "If a clip is in the wrong place, use the move controls to change its place.",
                "Play the whole movie once before you save it."
            ),
            practice = "Try making a short movie with two or three videos from your phone."
        ),
        StudioLesson(
            id = "trim-video",
            title = "Cut off the parts you do not need",
            summary = "Keep the good part of a video and leave out the rest.",
            steps = listOf(
                "Open your video in the editor.",
                "Move the start point to where the part you want begins.",
                "Move the end point to where the part you want finishes.",
                "Play the selected part to check it.",
                "Export a new copy. Your original video is not changed."
            ),
            practice = "Choose one video and make a shorter copy of your favourite moment."
        ),
        StudioLesson(
            id = "join-clips",
            title = "Join videos together",
            summary = "Put separate moments into one smooth movie.",
            steps = listOf(
                "Open the multi-clip movie editor.",
                "Add the videos you want to join.",
                "Arrange them in the order the story should happen.",
                "Trim any parts you do not want.",
                "Play the movie from the beginning to check the order."
            ),
            practice = "Join an opening clip and an ending clip to tell a tiny story."
        ),
        StudioLesson(
            id = "add-sound",
            title = "Add music or a voice recording",
            summary = "Give your movie a voice, music or extra sound.",
            steps = listOf(
                "Open your movie project.",
                "Choose the option to add background sound.",
                "Pick a music or sound file already on your phone.",
                "Play the movie and listen. Make sure the music does not cover the speaking.",
                "Play it again with your phone volume at a comfortable level."
            ),
            practice = "Add a quiet music track under a short video. Keep speech easy to hear."
        ),
        StudioLesson(
            id = "add-words",
            title = "Put words on the screen",
            summary = "Add a title, a name or a short message to your movie.",
            steps = listOf(
                "Open your movie project.",
                "Find the text or caption controls.",
                "Type a short title that is easy to read.",
                "Choose a place on the screen where it does not cover the important action.",
                "Preview the movie and check that the words stay readable."
            ),
            practice = "Add a simple title at the start of a short movie."
        ),
        StudioLesson(
            id = "save-movie",
            title = "Save and share your movie",
            summary = "Create a finished video while keeping your original clips safe.",
            steps = listOf(
                "Play your movie once and check the picture and sound.",
                "Choose Export to create a new video file.",
                "Choose a quality your phone can handle. Higher quality can take longer and use more space.",
                "Keep NeaRer open while the video is being created.",
                "When it finishes, open or share the new video. Your original clips stay as they were."
            ),
            practice = "Export a short test movie first. Check that it plays before making a longer one."
        )
    )
}

@Composable
internal fun StudioFilmSchoolScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("nearer_studio_film_school", Context.MODE_PRIVATE)
    }
    val completed = remember(context) {
        mutableStateOf(preferences.getStringSet("completed_lessons", emptySet()).orEmpty().toSet())
    }
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    var stepIndex by remember { mutableStateOf(0) }
    val selectedLesson = StudioFilmSchoolLessons.all.firstOrNull { it.id == selectedLessonId }

    BackHandler(enabled = selectedLesson != null) {
        selectedLessonId = null
        stepIndex = 0
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (selectedLesson == null) "‹  STUDIO" else "‹  ALL LESSONS",
                color = Mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    if (selectedLesson != null) {
                        selectedLessonId = null
                        stepIndex = 0
                    } else onBack()
                }.padding(vertical = 8.dp)
            )
            Text("WORKS WITHOUT DATA", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        if (selectedLesson == null) {
            Text("NeaRer Film School", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Learn by doing. Each lesson uses short, clear steps. You do not need internet or editing experience.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${completed.value.size} of ${StudioFilmSchoolLessons.all.size} lessons completed", color = Mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Start anywhere. You can repeat a lesson whenever you need it.", color = Muted, fontSize = 12.sp)
                }
            }
            StudioFilmSchoolLessons.all.forEachIndexed { index, lesson ->
                val isDone = lesson.id in completed.value
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedLessonId = lesson.id
                        stepIndex = 0
                    }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (isDone) "✓" else "${index + 1}", color = if (isDone) Mint else Gold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lesson.title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(lesson.summary, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Text(if (isDone) "DONE" else "OPEN", color = if (isDone) Mint else Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Text(selectedLesson.title, color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(selectedLesson.summary, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("STEP ${stepIndex + 1} OF ${selectedLesson.steps.size}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(selectedLesson.steps[stepIndex], color = White, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("TRY IT YOURSELF", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(selectedLesson.practice, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (stepIndex > 0) {
                    Button(
                        onClick = { stepIndex-- },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = White)
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (stepIndex < selectedLesson.steps.lastIndex) {
                            stepIndex++
                        } else {
                            val updated = completed.value + selectedLesson.id
                            completed.value = updated
                            preferences.edit().putStringSet("completed_lessons", updated).apply()
                            selectedLessonId = null
                            stepIndex = 0
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                ) {
                    Text(if (stepIndex == selectedLesson.steps.lastIndex) "Finish lesson" else "Next")
                }
            }
        }
    }
}
