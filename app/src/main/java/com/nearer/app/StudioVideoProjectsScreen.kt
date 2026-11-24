package com.nearer.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.util.UUID

private val studioEditorPreferenceGroups = listOf(
    "nearer_studio_video_timeline" to "project_v1",
    "nearer_studio_video_grade" to "grade_v1",
    "nearer_studio_video_text" to "overlay_v1",
    "nearer_studio_video_captions" to "captions_v1",
    "nearer_studio_video_transitions" to ""
)

private fun copyStudioEditorPreferences(context: Context, fromId: String?, toId: String) {
    studioEditorPreferenceGroups.forEach { (baseName, _) ->
        val sourceName = if (fromId == null) baseName else "${baseName}_${fromId}"
        val targetName = "${baseName}_${toId}"
        val source = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE)
        val target = context.getSharedPreferences(targetName, Context.MODE_PRIVATE)
        val values = source.all
        if (values.isNotEmpty()) {
            val editor = target.edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
        }
    }
}

/** Local project manager. Source media stays in its original location; only editor settings are copied. */
@Composable
internal fun StudioVideoProjectsScreen(
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { StudioProjectLibraryStore(context) }
    val projects = remember { mutableStateListOf<StudioProjectRecord>() }
    var nameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<StudioProjectRecord?>(null) }
    var name by remember { mutableStateOf("") }

    fun refresh() {
        projects.clear()
        projects.addAll(store.read().projects.filter { it.kind == "VIDEO" && !it.archived })
    }
    fun migrateLegacyProjectIfNeeded() {
        val current = store.read().projects
        if (current.isNotEmpty()) return
        val legacyTimeline = context.getSharedPreferences("nearer_studio_video_timeline", Context.MODE_PRIVATE)
        val hasLegacyWork = legacyTimeline.contains("project_v1")
        if (!hasLegacyWork) return
        val id = "legacy-video-project"
        copyStudioEditorPreferences(context, null, id)
        store.upsert(
            StudioProjectRecord(
                id = id,
                title = "My first movie",
                kind = "VIDEO",
                updatedAtMs = System.currentTimeMillis(),
                payloadJson = legacyTimeline.getString("project_v1", "{}") ?: "{}"
            )
        )
    }
    LaunchedEffect(context) {
        migrateLegacyProjectIfNeeded()
        refresh()
    }

    if (nameDialog) {
        AlertDialog(
            onDismissRequest = { nameDialog = false; renameTarget = null },
            title = { Text(if (renameTarget == null) "Name your movie" else "Rename movie") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    singleLine = true,
                    label = { Text("Movie name") },
                    placeholder = { Text("My movie") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanName = name.trim().ifBlank { "My movie" }
                    val targetId = renameTarget
                    if (targetId == null) {
                        val id = UUID.randomUUID().toString()
                        store.upsert(StudioProjectRecord(id, cleanName, "VIDEO", System.currentTimeMillis(), "{}"))
                        refresh()
                        onOpenProject(id)
                    } else {
                        store.read().projects.firstOrNull { it.id == targetId }?.let {
                            store.upsert(it.copy(title = cleanName, updatedAtMs = System.currentTimeMillis()))
                        }
                        refresh()
                    }
                    nameDialog = false
                    renameTarget = null
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { nameDialog = false; renameTarget = null }) { Text("CANCEL") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.title}?") },
            text = { Text("This removes the movie's edit settings from NeaRer. Your original videos, photos and music are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(target.id)
                    studioEditorPreferenceGroups.forEach { (baseName, _) ->
                        context.getSharedPreferences("${baseName}_${target.id}", Context.MODE_PRIVATE).edit().clear().apply()
                    }
                    refresh()
                    deleteTarget = null
                }) { Text("DELETE") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("KEEP") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("MY MOVIES", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Your movie projects", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("Each movie keeps its own clips, trims, captions, colour and transitions. Everything is saved on this phone.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Button(
            onClick = { name = ""; renameTarget = null; nameDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
        ) { Text("＋  CREATE NEW MOVIE", fontWeight = FontWeight.Bold) }

        if (projects.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Your first movie starts here", color = White, fontWeight = FontWeight.Bold)
                    Text("Create a project, then add your video clips. You can return to this screen to manage every movie.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else {
            projects.forEach { project ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(project.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("VIDEO PROJECT  ·  SAVED ON THIS PHONE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { onOpenProject(project.id) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                            Text("OPEN MOVIE", fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RENAME", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                name = project.title; renameTarget = project.id; nameDialog = true
                            }.padding(8.dp))
                            Text("DUPLICATE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                val duplicateId = UUID.randomUUID().toString()
                                copyStudioEditorPreferences(context, project.id, duplicateId)
                                store.upsert(project.copy(id = duplicateId, title = "${project.title} copy".take(80), updatedAtMs = System.currentTimeMillis()))
                                refresh()
                            }.padding(8.dp))
                            Text("DELETE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { deleteTarget = project }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
