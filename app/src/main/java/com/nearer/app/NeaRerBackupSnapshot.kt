package com.nearer.app

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONObject

/**
 * Builds a portable, allow-listed snapshot from NeaRer's current on-device stores.
 *
 * This deliberately excludes transient transport state, permissions, device-specific
 * paths and the public NeaRer identity as a recovery credential.
 */
internal object NeaRerBackupSnapshot {
    data class Summary(
        val chatMessages: Long,
        val receivedFiles: Int,
        val receivedBytes: Long,
        val includesProfilePhoto: Boolean
    )

    private val profileStringKeys = setOf(
        "display_name",
        "profile_avatar_id",
        "onboarding_answers",
        "community_groups",
        "community_messages",
        "nearby_transfer_history"
    )

    @Throws(IOException::class)
    fun create(context: Context, chatDatabase: NeaRerChatHistoryDatabase, destination: File): Summary {
        if (destination.exists()) throw IOException("Snapshot destination already exists")
        val parent = destination.absoluteFile.parentFile
            ?: throw IOException("Snapshot destination has no parent folder")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create snapshot parent")
        if (!destination.mkdirs()) throw IOException("Could not create snapshot folder")

        try {
            val prefs = context.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
            val profile = JSONObject()
                .put("format", "NeaRerLocalSnapshot")
                .put("version", 1)
                .put("onboardingComplete", prefs.getBoolean("onboarding_complete", false))
            val all = prefs.all
            profileStringKeys.forEach { key ->
                val value = all[key]
                if (value is String) profile.put(key, value)
            }
            // The app-private absolute path is device-specific; restore uses the
            // portable profile/profile-photo.jpg location instead.
            val photoPath = prefs.getString("profile_photo_path", null)
            val appRoot = context.filesDir.canonicalFile
            val photo = photoPath?.let(::File)?.takeIf { candidate ->
                candidate.isFile && candidate.canRead() &&
                    candidate.canonicalPath.startsWith(appRoot.path + File.separator)
            }
            val profileDirectory = File(destination, "profile")
            if (!profileDirectory.mkdirs()) throw IOException("Could not create profile snapshot folder")
            val hasPhoto = photo?.let {
                copyFile(it, File(profileDirectory, "profile-photo.jpg"))
                true
            } ?: false
            profile.put("hasProfilePhoto", hasPhoto)
            writeAtomically(File(destination, "profile.json"), profile.toString().toByteArray(Charsets.UTF_8))

            val chatDirectory = File(destination, "chats")
            if (!chatDirectory.mkdirs()) throw IOException("Could not create chat snapshot folder")
            val chatCount = chatDatabase.exportPortableArchive(File(chatDirectory, "messages.jsonl"))

            val receivedSource = File(context.filesDir, "received")
            val receivedDestination = File(destination, "received")
            if (!receivedDestination.mkdirs()) throw IOException("Could not create received-files snapshot folder")
            var fileCount = 0
            var totalBytes = 0L
            if (receivedSource.exists()) {
                val root = receivedSource.canonicalFile
                val items = receivedSource.listFiles() ?: throw IOException("Could not list received files")
                items.filter { it.isFile }.forEach { source ->
                    val canonical = source.canonicalFile
                    if (!canonical.path.startsWith(root.path + File.separator)) {
                        throw IOException("A received file resolves outside its storage folder")
                    }
                    if (source.length() > MAX_RECEIVED_FILE_BYTES) {
                        throw IOException("A received file is too large for this backup")
                    }
                    totalBytes += source.length()
                    if (totalBytes > MAX_RECEIVED_TOTAL_BYTES) {
                        throw IOException("Received files exceed the backup size limit")
                    }
                    val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(140)
                    val target = File(receivedDestination, safeName)
                    if (target.exists()) throw IOException("Received files contain duplicate safe names")
                    copyFile(source, target)
                    fileCount++
                }
            }
            return Summary(chatCount, fileCount, totalBytes, hasPhoto)
        } catch (failure: Exception) {
            destination.deleteRecursively()
            if (failure is IOException) throw failure
            throw IOException("Could not create NeaRer backup snapshot", failure)
        }
    }

    private fun copyFile(source: File, destination: File) {
        if (destination.exists()) throw IOException("Snapshot file already exists")
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, BUFFER_BYTES)
                output.fd.sync()
            }
        }
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        val temporary = File.createTempFile(".nearer-profile-", ".tmp", destination.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) throw IOException("Could not publish profile snapshot")
        } finally {
            temporary.delete()
        }
    }

    private const val BUFFER_BYTES = 64 * 1024
    private const val MAX_RECEIVED_FILE_BYTES = 512L * 1024L * 1024L
    private const val MAX_RECEIVED_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
}
