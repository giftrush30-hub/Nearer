package com.nearer.app

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Coordinates the complete encrypted export and the safe, read-only restore preview.
 *
 * Applying restored data is intentionally separate: it needs a transactional plan
 * across SQLite, preferences, profile media and received files before it is exposed.
 */
internal object NeaRerBackupManager {
    data class CreatedBackup(
        val chatMessages: Long,
        val receivedFiles: Int,
        val receivedBytes: Long,
        val includesProfilePhoto: Boolean
    )

    @Throws(IOException::class)
    fun createEncryptedBackup(
        context: Context,
        chatDatabase: NeaRerChatHistoryDatabase,
        destination: File,
        password: CharArray
    ): CreatedBackup {
        var snapshot: File? = null
        try {
            val workDirectory = newPrivateWorkDirectory(context, "nearer-backup-")
            snapshot = workDirectory
            val summary = NeaRerBackupSnapshot.create(context, chatDatabase, workDirectory)
            NeaRerBackupArchive.createEncryptedBackup(workDirectory, destination, password)
            return CreatedBackup(
                summary.chatMessages,
                summary.receivedFiles,
                summary.receivedBytes,
                summary.includesProfilePhoto
            )
        } catch (failure: Exception) {
            password.fill('\u0000')
            if (failure is IOException) throw failure
            throw IOException("Could not create encrypted NeaRer backup", failure)
        } finally {
            snapshot?.deleteRecursively()
        }
    }

    /**
     * Decrypts and validates into a private temporary folder, returns only preview
     * metadata, then deletes all plaintext restore material. No app data is changed.
     */
    @Throws(IOException::class)
    fun previewEncryptedBackup(
        context: Context,
        encryptedBackup: File,
        password: CharArray
    ): NeaRerBackupSnapshot.Preview {
        val restored = newNonexistentWorkPath(context, "nearer-restore-preview-")
        try {
            NeaRerBackupArchive.restoreEncryptedBackup(encryptedBackup, restored, password)
            return NeaRerBackupSnapshot.inspect(restored)
        } catch (failure: Exception) {
            password.fill('\u0000')
            if (failure is IOException) throw failure
            throw IOException("Could not validate NeaRer backup", failure)
        } finally {
            restored.deleteRecursively()
        }
    }

    private fun newPrivateWorkDirectory(context: Context, prefix: String): File {
        val marker = File.createTempFile(prefix, ".tmp", context.cacheDir)
        if (!marker.delete() || !marker.mkdir()) {
            marker.delete()
            throw IOException("Could not create private backup workspace")
        }
        return marker
    }

    private fun newNonexistentWorkPath(context: Context, prefix: String): File {
        // A UUID avoids reserving a directory before restoreEncryptedBackup creates it.
        return File(context.cacheDir, prefix + UUID.randomUUID().toString())
    }
}
