package com.nearer.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AcademyLesson(
    val id: String,
    val title: String,
    val duration: String,
    val concept: String,
    val practice: String,
    val question: String,
    val answers: List<String>,
    val correctAnswer: Int
)

private data class AcademyCourse(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val symbol: String,
    val lessons: List<AcademyLesson>
)

private val academyCourses = listOf(
    AcademyCourse("film", "FILMMAKING", "Make films with your phone", "From first frame to finished story", "▶", listOf(
        AcademyLesson("film_1", "See the shot before you take it", "6 MIN", "A strong shot has a clear subject, a deliberate background and a reason for the viewer to look. Before recording, decide what the audience must notice first. Move your phone, not just your fingers, until the frame feels intentional.", "Record the same subject in a wide shot, a medium shot and a close-up. Keep the subject in focus and avoid distracting objects behind it.", "Which shot is best for showing a person's facial emotion?", listOf("Wide shot", "Close-up", "Extreme wide shot"), 1),
        AcademyLesson("film_2", "Choose the right frame rate", "7 MIN", "Frame rate is the number of images recorded each second. 24 FPS gives familiar cinematic motion; 30 FPS feels natural for everyday video; 60 FPS captures smoother motion and gives room for slow motion when edited on a suitable timeline.", "Record a short walking clip at 30 FPS, then record a moving subject at 60 FPS. Compare motion and file size.", "Which frame rate is a common cinematic standard?", listOf("12 FPS", "24 FPS", "120 FPS"), 1),
        AcademyLesson("film_3", "Build a scene with three shots", "8 MIN", "A scene becomes easier to understand when shots work together. Establish where the action happens, show the important action, then capture the detail or reaction that gives it meaning. Keep screen direction consistent.", "Film a simple action in three shots: establish the place, show the action, then capture a reaction or detail.", "What is the main job of an establishing shot?", listOf("Show where the scene happens", "Add a transition", "Make every shot close-up"), 0)
    )),
    AcademyCourse("photo", "PHOTOGRAPHY", "Tell stories with light", "Composition, focus and natural light", "◉", listOf(
        AcademyLesson("photo_1", "Find soft, useful light", "5 MIN", "Light gives a photograph its mood. Window light and open shade are often softer than direct midday sun. Turn your subject toward the light and watch how shadows shape the face or object.", "Photograph one object beside a window from three angles. Compare the shadows and highlights.", "Which light is usually softer on a portrait?", listOf("Harsh midday sun", "Open shade", "Direct phone flash"), 1),
        AcademyLesson("photo_2", "Compose with intention", "6 MIN", "Composition guides attention. Use leading lines, clean backgrounds and deliberate spacing. The rule of thirds is a useful starting point, not a law: place the subject where the image feels balanced and meaningful.", "Take one photo with the subject centred and another with it placed off-centre. Keep the background simple.", "What should composition help the viewer do?", listOf("Notice the subject", "Ignore the subject", "See every object equally"), 0)
    )),
    AcademyCourse("design", "DESIGN", "Design that communicates", "Hierarchy, spacing and colour", "✳", listOf(
        AcademyLesson("design_1", "Create a clear visual hierarchy", "6 MIN", "A good design tells people where to look first, second and third. Give the most important message the strongest size or contrast. Use spacing to group related information and avoid giving every element equal weight.", "Create a simple event poster with one headline, one supporting line and one clear call to action.", "What creates a strong visual hierarchy?", listOf("Making everything the same size", "Clear differences in importance", "Using as many fonts as possible"), 1),
        AcademyLesson("design_2", "Use colour with purpose", "5 MIN", "A limited palette feels more coherent. Choose a dominant colour, a supporting colour and one accent. Check text contrast so the design stays readable on small phone screens.", "Create a three-colour palette for a poster: dominant, supporting and accent. Check that the text remains readable.", "Why should an accent colour be used sparingly?", listOf("To guide attention", "To hide the headline", "To make every element compete"), 0)
    )),
    AcademyCourse("audio", "AUDIO", "Make sound feel professional", "Clean voice, levels and atmosphere", "♫", listOf(
        AcademyLesson("audio_1", "Capture a clean voice recording", "5 MIN", "Good audio starts before editing. Move away from fans and traffic, keep the microphone a consistent distance from the speaker and record a short test. Avoid handling noise and clipping.", "Record the same sentence in a quiet room and in a noisy location. Listen with headphones and note the difference.", "What is the best first step for cleaner dialogue?", listOf("Record beside loud traffic", "Find a quiet space and test levels", "Hold the microphone far away"), 1),
        AcademyLesson("audio_2", "Balance dialogue and background sound", "6 MIN", "Dialogue should remain easy to understand. Background music and effects should support the scene without competing with speech. Listen at a comfortable volume and check the mix on phone speakers as well as headphones.", "Place a short music clip under a voice recording in an editor that supports multiple audio tracks. Lower the music until every word is clear.", "What should usually remain easiest to hear in a dialogue scene?", listOf("The dialogue", "The loudest sound effect", "The background music"), 0)
    )),
    AcademyCourse("motion", "ANIMATION", "Bring ideas into motion", "Timing, spacing and key poses", "✧", listOf(
        AcademyLesson("motion_1", "Understand timing and spacing", "6 MIN", "Timing is how long an action takes. Spacing is how far an object moves between frames. Together they create weight, speed and personality. A bouncing ball accelerates as it falls and slows near the top of its arc.", "Sketch five positions of a bouncing ball. Leave larger gaps while it falls quickly and smaller gaps near the top.", "What makes an animated object appear to accelerate?", listOf("Increasing distance between positions", "Using identical spacing everywhere", "Removing the starting pose"), 0),
        AcademyLesson("motion_2", "Plan key poses first", "7 MIN", "Key poses define the important moments of an action. Blocking those poses first makes movement easier to understand before adding the in-between frames. Clear silhouettes help the audience read the action.", "Draw a simple character reaching for an object using a starting pose, contact pose and ending pose.", "What do key poses establish?", listOf("The important moments of an action", "The final export size", "The background colour only"), 0)
    ))
)

@Composable
internal fun AcademyWorkspaceScreen(onBack: () -> Unit, onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("nearer_academy_progress", Context.MODE_PRIVATE) }
    val completed = remember(context) {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("completed_lessons", emptySet()).orEmpty())
        }
    }
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var selectedLessonId by remember { mutableStateOf<String?>(null) }
    val selectedCourse = academyCourses.firstOrNull { it.id == selectedCourseId }
    val selectedLesson = selectedCourse?.lessons?.firstOrNull { it.id == selectedLessonId }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selectedLesson != null) "‹  LESSONS" else if (selectedCourse != null) "‹  ACADEMY" else "‹  MORE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                when {
                    selectedLessonId != null -> selectedLessonId = null
                    selectedCourseId != null -> selectedCourseId = null
                    else -> onBack()
                }
            }.padding(vertical = 8.dp))
            Text("LEARN • PRACTISE • CREATE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        if (selectedLesson != null && selectedCourse != null) {
            val lesson = selectedLesson
            var answer by remember(lesson.id) { mutableStateOf<Int?>(null) }
            val alreadyDone = lesson.id in completed
            Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selectedCourse.category + "  /  " + lesson.duration, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(lesson.title, color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 29.sp)
                    Text(lesson.concept, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR FIELD PRACTICE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(lesson.practice, color = White, fontSize = 13.sp, lineHeight = 19.sp)
                    Button(onClick = onOpenStudio, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                        Text("Open Studio to practise  ↗", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("QUICK KNOWLEDGE CHECK", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(lesson.question, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    lesson.answers.forEachIndexed { index, option ->
                        val chosen = answer == index
                        val correct = index == lesson.correctAnswer
                        Surface(
                            color = if (chosen && correct) Mint.copy(alpha = 0.18f) else if (chosen) Gold.copy(alpha = 0.14f) else Midnight,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { if (!alreadyDone) answer = index }
                        ) {
                            Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (chosen && correct) "✓  " else if (chosen) "•  " else "○  ", color = if (chosen && correct) Mint else Gold, fontWeight = FontWeight.Bold)
                                Text(option, color = White, fontSize = 13.sp)
                            }
                        }
                    }
                    if (answer != null) {
                        Text(if (answer == lesson.correctAnswer) "Correct. You’ve got it." else "Not quite. Revisit the lesson and try again.", color = if (answer == lesson.correctAnswer) Mint else Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            if (answer == lesson.correctAnswer && !alreadyDone) {
                                completed.add(lesson.id)
                                preferences.edit().putStringSet("completed_lessons", completed.toSet()).apply()
                            }
                        },
                        enabled = alreadyDone || answer == lesson.correctAnswer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight, disabledContainerColor = Midnight, disabledContentColor = Muted)
                    ) {
                        Text(if (alreadyDone) "Lesson completed ✓" else "Complete lesson  •  +20 XP", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (selectedCourse != null) {
            val course = selectedCourse
            val doneCount = course.lessons.count { it.id in completed }
            Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(course.category, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(course.title, color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text(course.subtitle, color = Muted, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { doneCount.toFloat() / course.lessons.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Mint, trackColor = Midnight)
                    Text("$doneCount of ${course.lessons.size} lessons completed", color = Muted, fontSize = 11.sp)
                }
            }
            course.lessons.forEachIndexed { index, lesson ->
                val done = lesson.id in completed
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { selectedLessonId = lesson.id }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = if (done) Mint.copy(alpha = 0.18f) else Midnight, shape = RoundedCornerShape(12.dp)) {
                            Text(if (done) "✓" else "${index + 1}", color = if (done) Mint else Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lesson.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(lesson.duration + if (done) "  •  COMPLETED" else "  •  LESSON", color = Muted, fontSize = 10.sp)
                        }
                        Text("›", color = Mint, fontSize = 24.sp)
                    }
                }
            }
        } else {
            val totalLessons = academyCourses.sumOf { it.lessons.size }
            val percent = completed.size.coerceAtMost(totalLessons) * 100 / totalLessons
            Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("NEARER ACADEMY", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Skills that leave the screen and enter real life.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Short offline lessons, practical challenges and quick knowledge checks. Learn a skill, practise it in Studio, then build something of your own.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$percent%", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("YOUR LEARNING PROGRESS", color = White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp), color = Mint, trackColor = Midnight)
                        }
                    }
                }
            }
            Text("CHOOSE YOUR CREATIVE PATH", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            academyCourses.forEach { course ->
                val done = course.lessons.count { it.id in completed }
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { selectedCourseId = course.id }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(color = Midnight, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) {
                            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                Text(course.symbol, color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(course.category, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(course.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(course.subtitle, color = Muted, fontSize = 11.sp)
                            LinearProgressIndicator(progress = { done.toFloat() / course.lessons.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp), color = Mint, trackColor = Midnight)
                        }
                        Text("$done/${course.lessons.size}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text("Built for offline learning. Your lesson progress stays on this phone.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
