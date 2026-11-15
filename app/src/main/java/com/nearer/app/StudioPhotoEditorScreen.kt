package com.nearer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
internal fun StudioPhotoEditorScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var brightness by remember(uri) { mutableStateOf(0f) }
    var contrast by remember(uri) { mutableStateOf(1f) }
    var saturation by remember(uri) { mutableStateOf(1f) }
    var warmth by remember(uri) { mutableStateOf(0f) }
    var rotation by remember(uri) { mutableStateOf(0) }
    var message by remember { mutableStateOf("Your original photo is kept unchanged.") }
    var exported by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        source = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
        if (source == null) message = "This image could not be opened. Try another photo format."
    }

    val previewMatrix = photoColourMatrix(brightness, contrast, saturation, warmth)

    Column(Modifier.fillMaxSize().background(Midnight).padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("PHOTO COLOUR LAB", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (source != null) {
                androidx.compose.foundation.Image(
                    bitmap = source!!.asImageBitmap(),
                    contentDescription = "Photo preview",
                    modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer { rotationZ = rotation.toFloat() },
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(previewMatrix))
                )
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Preparing photo…", color = Mint, fontWeight = FontWeight.Bold)
                    Text(message, color = Muted, fontSize = 11.sp)
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.weight(1.05f, fill = false)) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("LIGHT & COLOUR", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("BRIGHTNESS  ${brightness.toInt()}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = brightness, onValueChange = { brightness = it; exported = null }, valueRange = -70f..70f, enabled = source != null)
                Text("CONTRAST  ${String.format("%.2f", contrast)}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = contrast, onValueChange = { contrast = it; exported = null }, valueRange = 0.5f..1.8f, enabled = source != null)
                Text("SATURATION  ${String.format("%.2f", saturation)}", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = saturation, onValueChange = { saturation = it; exported = null }, valueRange = 0f..2f, enabled = source != null)
                Text("WARMTH  ${warmth.toInt()}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Slider(value = warmth, onValueChange = { warmth = it; exported = null }, valueRange = -45f..45f, enabled = source != null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { rotation = (rotation + 90) % 360; exported = null }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("ROTATE 90°") }
                    Button(onClick = {
                        brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; rotation = 0; exported = null
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Gold)) { Text("RESET") }
                }
            }
        }
        Button(onClick = {
            val input = source
            if (input == null) message = "Wait for the photo to finish loading."
            else {
                exporting = true
                message = "Applying your edits and saving a new copy…"
                val brightnessValue = brightness
                val contrastValue = contrast
                val saturationValue = saturation
                val warmthValue = warmth
                val rotationValue = rotation
                scope.launch {
                    val output = withContext(Dispatchers.IO) {
                        runCatching {
                            val transform = Matrix().apply { postRotate(rotationValue.toFloat()) }
                            val rotated = Bitmap.createBitmap(input, 0, 0, input.width, input.height, transform, true)
                            val edited = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
                            val values = photoColourMatrix(brightnessValue, contrastValue, saturationValue, warmthValue)
                            val colorMatrix = AndroidColorMatrix(values)
                            Canvas(edited).drawBitmap(rotated, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(colorMatrix)
                            })
                            val folder = File(context.filesDir, "studio-exports").apply { mkdirs() }
                            val file = File(folder, "NeaRer_Photo_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { edited.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                            if (rotated !== input) rotated.recycle()
                            edited.recycle()
                            file
                        }.getOrNull()
                    }
                    exported = output
                    exporting = false
                    message = if (output != null) "Edited copy saved · ${output.length() / 1024L} KB." else "Could not export this image. Try a smaller photo."
                }
            }
        }, enabled = source != null && !exporting, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight), shape = RoundedCornerShape(14.dp)) {
            Text(if (exporting) "SAVING…" else "EXPORT EDITED PHOTO", fontWeight = FontWeight.Bold)
        }
        Text(message, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        exported?.let { file ->
            Button(onClick = {
                runCatching {
                    val outUri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, outUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share edited photo"))
                }.onFailure { message = "Could not share this photo: ${it.message}" }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Mint)) {
                Text("SHARE EDITED PHOTO")
            }
        }
    }
}

/** One shared matrix keeps the on-screen preview and exported JPEG colour treatment identical. */
private fun photoColourMatrix(brightness: Float, contrast: Float, saturation: Float, warmth: Float): FloatArray {
    val inverse = 1f - saturation
    val red = 0.213f * inverse
    val green = 0.715f * inverse
    val blue = 0.072f * inverse
    val contrastOffset = 128f * (1f - contrast) + brightness
    return floatArrayOf(
        (red + saturation) * contrast, green * contrast, blue * contrast, 0f, contrastOffset + warmth,
        red * contrast, (green + saturation) * contrast, blue * contrast, 0f, contrastOffset,
        red * contrast, green * contrast, (blue + saturation) * contrast, 0f, contrastOffset - warmth,
        0f, 0f, 0f, 1f, 0f
    )
}
