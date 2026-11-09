package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** Streams a browser upload to private storage without buffering the file in memory. */
internal object FastShareUploadReceiver {
    const val BUFFER_SIZE = 256 * 1024

    fun receive(
        input: InputStream,
        receivedDirectory: File,
        requestedName: String,
        contentLength: Long,
        shouldContinue: () -> Boolean = { true }
    ): File {
        require(contentLength >= 0L) { "Invalid upload length." }
        val safeName = TransferFileNamePolicy.safeName(requestedName)
        require(safeName.isNotBlank()) { "A file name is required." }
        if (!receivedDirectory.exists() && !receivedDirectory.mkdirs()) {
            throw IllegalStateException("Could not prepare NeaRer's received-files folder.")
        }
        val temporary = File.createTempFile(".nearer-upload-", ".part", receivedDirectory)
        try {
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = contentLength
                while (remaining > 0L) {
                    if (!shouldContinue()) throw IllegalStateException("The Fast Share session stopped.")
                    val requested = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, requested)
                    if (count < 0) throw IllegalStateException("The browser upload ended before the full file arrived.")
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    remaining -= count.toLong()
                }
                output.fd.sync()
            }
            if (temporary.length() != contentLength) {
                throw IllegalStateException("The uploaded file size did not match.")
            }
            val destination = File(
                receivedDirectory,
                System.currentTimeMillis().toString() + "-" + java.util.UUID.randomUUID().toString() + "-" + safeName
            )
            if (!temporary.renameTo(destination)) {
                throw IllegalStateException("Could not finish saving the uploaded file.")
            }
            return destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }
}
