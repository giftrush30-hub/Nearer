package com.nearer.app

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

private data class StudioAsset(val uri: Uri, val name: String, val kind: String)

@Composable
internal fun StudioWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var editingAsset by remember { mutableStateOf<StudioAsset?>(null) }
    var recordingAudio by remember { mutableStateOf(false) }
    var mixingAudio by remember { mutableStateOf(false) }
    var videoTimeline by remember { mutableStateOf(false) }
    var videoProjects by remember { mutableStateOf(false) }
    var selectedVideoProjectId by remember { mutableStateOf<String?>(null) }
    var filmSchool by remember { mutableStateOf(false) }
    var threeDScene by remember { mutableStateOf(false) }
    val preferences = remember(context) {
        context.getSharedPreferences("nearer_studio_workspace", android.content.Context.MODE_PRIVATE)
    }
    val assets = remember(context) {
        mutableStateListOf<StudioAsset>().apply {
            val saved = runCatching { JSONArray(preferences.getString("assets", "[]")) }.getOrNull()
            if (saved != null) {
                for (index in 0 until saved.length()) {
                    val item = saved.optJSONObject(index) ?: continue
                    val uri = runCatching { Uri.parse(item.optString("uri")) }.getOrNull() ?: continue
                    val hasGrant = context.contentResolver.persistedUriPermissions.any {
                        it.uri == uri && it.isReadPermission
                    }
                    val isLocalRecording = uri.scheme == "file" && runCatching { File(uri.path.orEmpty()).isFile }.getOrDefault(false)
                    if (hasGrant || isLocalRecording) add(StudioAsset(uri, item.optString("name", "Media asset"), item.optString("kind", "Media")))
                }
            }
        }
    }
    fun persistAssets() {
        val json = JSONArray()
        assets.forEach { asset ->
            json.put(JSONObject().put("uri", asset.uri.toString()).put("name", asset.name).put("kind", asset.kind))
        }
        preferences.edit().putString("assets", json.toString()).apply()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        selected.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (assets.none { it.uri == uri }) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Media asset"
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    val kind = when {
                        mime.startsWith("image/") -> "PHOTO"
                        mime.startsWith("video/") -> "VIDEO"
                        mime.startsWith("audio/") -> "AUDIO"
                        else -> "MEDIA"
                    }
                    assets.add(0, StudioAsset(uri, name, kind))
                }
            }
        }
        persistAssets()
    }

    if (threeDScene) {
        StudioFilament3DPreview(onBack = { threeDScene = false })
    } else if (filmSchool) {
        StudioFilmSchoolScreen(onBack = { filmSchool = false })
    } else if (videoTimeline && selectedVideoProjectId != null) {
        StudioVideoTimelineScreen(projectId = selectedVideoProjectId!!, onBack = { videoTimeline = false; videoProjects = true })
    } else if (videoProjects) {
        StudioVideoProjectsScreen(
            onOpenProject = { id -> selectedVideoProjectId = id; videoProjects = false; videoTimeline = true },
            onBack = { videoProjects = false }
        )
    } else if (mixingAudio) {
        StudioAudioMixerScreen(onBack = { mixingAudio = false })
    } else if (recordingAudio) {
        StudioAudioRecorderScreen(onBack = { recordingAudio = false }, onRecorded = { uri, name ->
            if (assets.none { it.uri == uri }) assets.add(0, StudioAsset(uri, name, "AUDIO"))
            persistAssets()
            recordingAudio = false
            editingAsset = StudioAsset(uri, name, "AUDIO")
        })
    } else if (editingAsset != null) {
        val asset = editingAsset!!
        when (asset.kind) {
            "VIDEO" -> StudioVideoEditorScreen(uri = asset.uri, title = asset.name, onBack = { editingAsset = null })
            "PHOTO" -> StudioPhotoEditorScreen(uri = asset.uri, title = asset.name, onBack = { editingAsset = null })
            "AUDIO" -> StudioAudioEditorScreen(uri = asset.uri, title = asset.name, onBack = { editingAsset = null })
            else -> StudioAudioEditorScreen(uri = asset.uri, title = asset.name, onBack = { editingAsset = null })
        }
    } else Column(
        modifier = Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  MORE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("OFFLINE FILM STUDIO", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NEARER STUDIO", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Make a movie. Learn as you go. No data needed.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Start with a video, join a few clips, add sound and make your own movie. Your chosen files stay on this phone.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Button(onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("＋  Add videos, photos or sound", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { recordingAudio = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) {
                    Text("●  Record a voice or sound", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { mixingAudio = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                    Text("＋  Mix music and sound", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { videoProjects = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                    Text("▤  My movies · create or continue", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { threeDScene = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) {
                    Text("◇  Create a 3D scene or animate a character", fontWeight = FontWeight.Bold)
                }
                Button(onClick = { filmSchool = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) {
                    Text("▶  Teach me how to make movies", fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR FILES", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${assets.size} ON THIS PHONE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        if (assets.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your studio is ready", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Add a photo, video or audio recording to start your first local project space.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else {
            assets.forEach { asset ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(asset.name, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(asset.kind, color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (asset.kind == "VIDEO" || asset.kind == "PHOTO" || asset.kind == "AUDIO") Text("CHANGE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { editingAsset = asset }.padding(7.dp))
                        Text("VIEW", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                            runCatching {
                                val localFile = if (asset.uri.scheme == "file") File(asset.uri.path.orEmpty()) else null
                                val openUri = if (localFile != null) androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", localFile) else asset.uri
                                val mime = context.contentResolver.getType(asset.uri) ?: if (asset.kind == "AUDIO") "audio/mp4" else "*/*"
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(openUri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        }.padding(8.dp))
                        Text("×", color = Muted, fontSize = 18.sp, modifier = Modifier.clickable {
                            runCatching { context.contentResolver.releasePersistableUriPermission(asset.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            assets.remove(asset)
                            persistAssets()
                        }.padding(6.dp))
                    }
                }
            }
        }
        Text("MORE TO COME", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("You can make a movie from clips, cut a video, change a photo, record your voice and mix sounds. More movie-making tools are being built.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
