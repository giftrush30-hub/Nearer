package com.nearer.app

import android.net.Uri
import android.view.Choreographer
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object StudioFilamentInitializer {
    @Volatile private var initialized = false
    @Synchronized fun ensureInitialized() {
        if (!initialized) {
            Utils.init()
            initialized = true
        }
    }
}

@Composable
internal fun StudioFilament3DPreview(onBack: () -> Unit) {
    val context = LocalContext.current
    val scenePreferences = remember(context) { context.getSharedPreferences("nearer_studio_3d_scene", android.content.Context.MODE_PRIVATE) }
    var selectedModelUri by remember(context) { mutableStateOf(scenePreferences.getString("model_uri", null)?.let(Uri::parse)) }
    var viewer by remember { mutableStateOf<ModelViewer?>(null) }
    var status by remember { mutableStateOf("Choose a local GLB model to begin.") }
    var animationCount by remember { mutableStateOf(0) }
    var animationIndex by remember { mutableStateOf(0) }
    var animationPlaying by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val canRemember = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            selectedModelUri = uri
            if (canRemember) {
                scenePreferences.edit().putString("model_uri", uri.toString()).apply()
                status = "Model selected. Preparing the offline 3D preview…"
            } else {
                scenePreferences.edit().remove("model_uri").apply()
                status = "Model selected for this session. This file provider does not allow the model to be remembered."
            }
        }
    }

    LaunchedEffect(viewer, selectedModelUri) {
        val activeViewer = viewer ?: return@LaunchedEffect
        val uri = selectedModelUri ?: return@LaunchedEffect
        status = "Checking your 3D model…"
        val bytes = runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not open the selected file.")
            }
        }.getOrElse {
            status = "Could not read this model: ${it.message ?: "unknown error"}"
            return@LaunchedEffect
        }
        val validation = StudioGlbValidator.validate(bytes)
        if (!validation.isValid) {
            status = validation.errors.joinToString(" ")
            return@LaunchedEffect
        }
        runCatching {
            activeViewer.loadModelGlb(ByteBuffer.wrap(bytes))
            check(activeViewer.asset != null) { "Filament could not load this GLB model." }
            activeViewer.transformToUnitCube()
            activeViewer.autoPlayAnimations = animationPlaying
            animationCount = activeViewer.animator?.animationCount ?: 0
            animationIndex = 0
            activeViewer.activeAnimationIndex = 0
            status = if (animationCount > 0) {
                "Model loaded · $animationCount animation(s) available. This model is remembered on this phone."
            } else {
                "Model loaded · no embedded animation found. This model is remembered on this phone."
            }
        }.onFailure {
            status = "Could not load this 3D model: ${it.message ?: "unsupported asset"}"
        }
    }

    val frameCallback = remember {
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                viewer?.render(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }
    DisposableEffect(frameCallback) {
        Choreographer.getInstance().postFrameCallback(frameCallback)
        onDispose { Choreographer.getInstance().removeFrameCallback(frameCallback) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  BACK", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("3D SCENE STUDIO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text("Bring a 3D character or scene to life", color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text("Import a glTF 2.0 binary model (.glb). Rotate and zoom with your fingers. Embedded skeletal and transform animations can play offline.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { viewContext ->
                    SurfaceView(viewContext).also { surface ->
                        StudioFilamentInitializer.ensureInitialized()
                        val modelViewer = ModelViewer(surface)
                        surface.setOnTouchListener(modelViewer)
                        viewer = modelViewer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(status, color = Mint, fontSize = 11.sp, lineHeight = 16.sp)
        Button(onClick = { picker.launch(arrayOf("model/gltf-binary", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)) {
            Text(if (selectedModelUri == null) "CHOOSE 3D MODEL (.GLB)" else "CHANGE 3D MODEL", fontWeight = FontWeight.ExtraBold)
        }
        if (selectedModelUri != null) {
            Button(onClick = {
                viewer?.destroyModel()
                selectedModelUri = null
                scenePreferences.edit().remove("model_uri").apply()
                animationCount = 0
                animationIndex = 0
                status = "3D model cleared from this scene."
            }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
                Text("CLEAR REMEMBERED MODEL")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                animationPlaying = !animationPlaying
                viewer?.autoPlayAnimations = animationPlaying
            }, enabled = animationCount > 0, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text(if (animationPlaying) "PAUSE ANIMATION" else "PLAY ANIMATION", fontSize = 10.sp)
            }
            Button(onClick = {
                if (animationCount > 0) {
                    animationIndex = (animationIndex + 1) % animationCount
                    viewer?.activeAnimationIndex = animationIndex
                }
            }, enabled = animationCount > 1, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Gold)) {
                Text("NEXT ANIMATION", fontSize = 10.sp)
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Muted)) {
            Text("RETURN TO STUDIO")
        }
    }
}
