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
    /**
     * A fully decrypted, integrity-checked restore staged in app-private cache.
     * The caller must close it after confirmation/cancellation; it is never applied
     * automatically and is not a durable copy of the user's backup.
     */
    class PreparedRestore internal constructor(
        internal val directory: File,
        val preview: NeaRerBackupSnapshot.Preview
    ) : java.io.Closeable {
        override fun close() {
            directory.deleteRecursively()
        }
    }

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
     * Decrypts, authenticates and validates a backup into a private staged folder.
     * The caller owns the returned PreparedRestore and MUST close it on every path.
     * This method does not modify live app data.
     */
    @Throws(IOException::class)
    fun prepareEncryptedBackup(
        context: Context,
        encryptedBackup: File,
        password: CharArray
    ): PreparedRestore {
        val restored = newNonexistentWorkPath(context, "nearer-restore-stage-")
        try {
            NeaRerBackupArchive.restoreEncryptedBackup(encryptedBackup, restored, password)
            val preview = NeaRerBackupSnapshot.inspect(restored)
            return PreparedRestore(restored, preview)
        } catch (failure: Exception) {
            password.fill('\\u0000')
            restored.deleteRecursively()
            if (failure is IOException) throw failure
            throw IOException("Could not prepare NeaRer backup restore", failure)
        }
    }

    /**
     * Read-only convenience preview. The staged plaintext is deleted immediately
     * after metadata has been read; use prepareEncryptedBackup when a later,
     * separately confirmed restore step needs the same verified staging directory.
     */
    @Throws(IOException::class)
    fun previewEncryptedBackup(
        context: Context,
        encryptedBackup: File,
        password: CharArray
    ): NeaRerBackupSnapshot.Preview {
        val prepared = prepareEncryptedBackup(context, encryptedBackup, password)
        return try {
            prepared.preview
        } finally {
            prepared.close()
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
