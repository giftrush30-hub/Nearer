package com.nearer.app

import android.view.Choreographer
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<ModelViewer?>(null) }
    var status by remember { mutableStateOf("Choose a local GLB model to begin.") }
    var animationCount by remember { mutableStateOf(0) }
    var animationIndex by remember { mutableStateOf(0) }
    var animationPlaying by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = "Checking your 3D model…"
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Could not open the selected file.")
                }
            }.getOrElse {
                status = "Could not read this model: ${it.message ?: "unknown error"}"
                return@launch
            }
            val validation = StudioGlbValidator.validate(bytes)
            if (!validation.isValid) {
                status = validation.errors.joinToString(" ")
                return@launch
            }
            val activeViewer = viewer
            if (activeViewer == null) {
                status = "3D preview is still starting. Please choose the model again."
                return@launch
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
                    "Model loaded · $animationCount animation(s) available."
                } else {
                    "Model loaded · no embedded animation found."
                }
            }.onFailure {
                status = "Could not load this 3D model: ${it.message ?: "unsupported asset"}"
            }
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
                modifier = Modifier.padding(vertical = 8.dp).then(Modifier))
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
            Text("CHOOSE 3D MODEL (.GLB)", fontWeight = FontWeight.ExtraBold)
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
