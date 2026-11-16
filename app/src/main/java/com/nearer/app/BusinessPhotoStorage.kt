package com.nearer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies selected business photos into NeaRer's private files directory.
 * This avoids keeping long-lived grants to a user's external photo provider.
 */
internal object BusinessPhotoStorage {
    private const val DIRECTORY = "business-photos"
    private const val MAX_SOURCE_BYTES = 30L * 1024L * 1024L
    private const val MAX_DIMENSION = 2_048
    private const val JPEG_QUALITY = 88

    suspend fun import(context: Context, source: Uri): Uri? = withContext(Dispatchers.IO) {
        val mime = runCatching { context.contentResolver.getType(source) }.getOrNull()
        if (mime?.startsWith("image/") != true) return@withContext null

        val directory = File(context.filesDir, DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) return@withContext null

        val token = UUID.randomUUID().toString().replace("-", "")
        val temporary = File(directory, "business-$token.tmp")
        val output = File(directory, "business-$token.jpg")
        try {
            val input = context.contentResolver.openInputStream(source) ?: return@withContext null
            input.use { stream ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_SOURCE_BYTES) { "Photo exceeds the 30 MB import safety limit." }
                        target.write(buffer, 0, read)
                    }
                    target.fd.sync()
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image." }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION || bounds.outHeight / sampleSize > MAX_DIMENSION) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(temporary.absolutePath, options)
                ?: throw IllegalArgumentException("NeaRer could not decode this image.")
            bitmap.use { decoded ->
                FileOutputStream(output).use { target ->
                    check(decoded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, target)) {
                        "NeaRer could not prepare the photo."
                    }
                    target.fd.sync()
                }
            }
            temporary.delete()
            FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        } catch (_: Throwable) {
            temporary.delete()
            output.delete()
            null
        }
    }

    fun deleteIfManaged(context: Context, uri: Uri) {
        if (uri.authority != "${context.packageName}.files") return
        val filename = uri.lastPathSegment ?: return
        if (!filename.startsWith("business-") || !filename.endsWith(".jpg")) return
        val directory = File(context.filesDir, DIRECTORY).canonicalFile
        val target = File(directory, filename).canonicalFile
        if (target.parentFile == directory) target.delete()
    }
}
