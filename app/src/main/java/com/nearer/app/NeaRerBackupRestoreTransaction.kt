package com.nearer.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Applies a previously reviewed backup as a coordinated local restore.
 *
 * A rollback copy of the current chat archive and SharedPreferences is captured before
 * live data changes. Received files are swapped by same-filesystem directory renames.
 * The caller must obtain explicit user confirmation before calling apply().
 *
 * The Activity must be recreated after success so in-memory profile/community/transport
 * state is reloaded. Device identity, permissions and relay consent are never imported.
 */
internal object NeaRerBackupRestoreTransaction {
    data class Result(
        val chatMessages: Long,
        val receivedFiles: Int,
        val displayName: String,
        val restartRequired: Boolean = true
    )

    private val portableKeys = setOf(
        "display_name",
        "profile_avatar_id",
        "onboarding_answers",
        "community_groups",
        "community_messages",
        "nearby_transfer_history",
        "recently_seen",
        "nearby_outbox",
        "nearby_seen_message_ids_ordered"
    )

    @Throws(IOException::class)
    fun apply(
        context: Context,
        chatDatabase: NeaRerChatHistoryDatabase,
        prepared: NeaRerBackupManager.PreparedRestore,
        explicitlyConfirmed: Boolean
    ): Result {
        if (!explicitlyConfirmed) throw IOException("Restore requires explicit user confirmation")
        val plan = NeaRerBackupRestorePlan.fromPreparedRestore(prepared)
        val filesRoot = context.filesDir.canonicalFile
        val receivedTarget = File(filesRoot, "received")
        // NOFOLLOW_LINKS also detects a dangling symlink, for which File.exists() is false.
        if (Files.exists(receivedTarget.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            (!receivedTarget.isDirectory || !NeaRerBackupPathSafety.isContained(filesRoot, receivedTarget))
        ) {
            throw IOException("Current received-files folder resolves outside NeaRer storage")
        }

        val prefs = context.getSharedPreferences("nearer_local", Context.MODE_PRIVATE)
        // SharedPreferences may expose mutable StringSet instances. Copy them before
        // any mutation so the rollback snapshot remains an immutable point-in-time copy.
        val preferenceValues = prefs.all
        validatePreferenceSnapshot(preferenceValues)
        val oldPreferences = preferenceValues.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }

        // Validate all rollback inputs before allocating the private workspace.
        val rollbackRoot = createPrivateDirectory(context, "nearer-restore-rollback-")
        val rollbackChats = File(rollbackRoot, "messages.jsonl")
        val stagedReceived = File(filesRoot, ".nearer-received-stage-${UUID.randomUUID()}")
        val oldReceived = File(filesRoot, ".nearer-received-rollback-${UUID.randomUUID()}")
        val newPhoto = plan.profilePhoto?.let {
            File(filesRoot, "nearer-profile-photo-${UUID.randomUUID()}.jpg")
        }
        var oldReceivedMoved = false
        var newReceivedMoved = false
        var databaseReplaced = false
        var preferencesCommitted = false

        try {
            chatDatabase.exportPortableArchive(rollbackChats)
            if (!stagedReceived.mkdir()) throw IOException("Could not stage restored received files")
            plan.receivedFiles.forEach { source ->
                val name = source.name
                if (name.isBlank() || name == "." || name == ".." ||
                    name.contains('/') || name.contains('\\')) {
                    throw IOException("A restored file has an unsafe name")
                }
                val target = File(stagedReceived, name)
                if (target.exists()) throw IOException("Restored files contain duplicate names")
                copyFile(source, target)
            }
            if (!NeaRerBackupPathSafety.isContained(filesRoot, stagedReceived)) {
                throw IOException("Restore staging folder escaped NeaRer storage")
            }
            newPhoto?.let { target ->
                copyFile(plan.profilePhoto!!, target)
                if (!NeaRerBackupPathSafety.isContained(filesRoot, target)) {
                    throw IOException("Restored profile photo escaped NeaRer storage")
                }
            }

            if (receivedTarget.exists()) {
                if (!receivedTarget.renameTo(oldReceived)) {
                    throw IOException("Could not preserve existing received files")
                }
                oldReceivedMoved = true
            }
            if (!stagedReceived.renameTo(receivedTarget)) {
                throw IOException("Could not install restored received files")
            }
            newReceivedMoved = true

            chatDatabase.replacePortableArchive(plan.chatArchive)
            databaseReplaced = true

            val editor = prefs.edit()
            portableKeys.forEach { editor.remove(it) }
            plan.portablePreferences.forEach { (key, value) ->
                if (key !in portableKeys) throw IOException("Restore plan contains an unsupported preference")
                editor.putString(key, value)
            }
            editor.putBoolean("onboarding_complete", plan.onboardingComplete)
            if (newPhoto != null) editor.putString("profile_photo_path", newPhoto.absolutePath)
            else editor.remove("profile_photo_path")
            if (!editor.commit()) throw IOException("Could not save restored NeaRer preferences")
            preferencesCommitted = true

            // The live restore is committed at this point. Cleanup is deliberately
            // best-effort and must never enter the rollback path: the rollback chat
            // archive is no longer needed once preferences and SQLite have committed.
            runCatching { oldReceived.deleteRecursively() }
            val oldPhotoPath = oldPreferences["profile_photo_path"] as? String
            runCatching {
                oldPhotoPath?.let { path ->
                    val oldPhoto = File(path)
                    if (oldPhoto != newPhoto && oldPhoto.isFile &&
                        NeaRerBackupPathSafety.isContained(filesRoot, oldPhoto)) {
                        oldPhoto.delete()
                    }
                }
            }
            runCatching { rollbackRoot.deleteRecursively() }
            return Result(
                chatMessages = plan.preview.chatMessages,
                receivedFiles = plan.receivedFiles.size,
                displayName = plan.preview.displayName
            )
        } catch (failure: Exception) {
            val rollbackFailures = mutableListOf<Throwable>()
            if (preferencesCommitted || prefs.all != oldPreferences) {
                try {
                    restorePreferences(prefs, oldPreferences)
                } catch (rollback: Exception) {
                    rollbackFailures.add(rollback)
                }
            }
            if (databaseReplaced) {
                try {
                    chatDatabase.replacePortableArchive(rollbackChats)
                } catch (rollback: Exception) {
                    rollbackFailures.add(rollback)
                }
            }
            if (newReceivedMoved) receivedTarget.deleteRecursively()
            if (oldReceivedMoved && oldReceived.exists() && !oldReceived.renameTo(receivedTarget)) {
                rollbackFailures.add(IOException("Could not put the original received files back"))
            }
            stagedReceived.deleteRecursively()
            if (rollbackFailures.isEmpty()) {
                newPhoto?.delete()
                rollbackRoot.deleteRecursively()
            } else {
                // A failed preference rollback may still leave profile_photo_path pointing
                // at the staged image. Preserve it, along with the old received folder and
                // the exported chat archive, rather than turning a partial rollback into
                // additional data loss.
                failure.addSuppressed(IOException(
                    "Rollback recovery data retained. Private cache: ${rollbackRoot.absolutePath}; " +
                        "preserved received-folder backup: ${if (oldReceivedMoved) oldReceived.absolutePath else "none"}; " +
                        "preserved staged profile photo: ${newPhoto?.absolutePath ?: "none"}"
                ))
            }
            rollbackFailures.forEach(failure::addSuppressed)
            if (failure is IOException) throw failure
            throw IOException("Could not apply NeaRer restore", failure)
        }
    }

    private fun validatePreferenceSnapshot(values: Map<String, *>) {
        values.forEach { (key, value) ->
            if (value !is String && value !is Boolean && value !is Int &&
                value !is Long && value !is Float && value !is Set<*>) {
                throw IOException("Unsupported saved preference type for $key")
            }
            if (value is Set<*> && value.any { it !is String }) {
                throw IOException("Unsupported saved preference set for $key")
            }
        }
    }

    private fun restorePreferences(
        preferences: SharedPreferences,
        values: Map<String, *>
    ) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> editor.remove(key)
                else -> throw IOException("Unsupported saved preference type for $key")
            }
        }
        if (!editor.commit()) throw IOException("Could not roll back NeaRer preferences")
    }

    private fun createPrivateDirectory(context: Context, prefix: String): File {
        val marker = File.createTempFile(prefix, ".tmp", context.cacheDir)
        if (!marker.delete() || !marker.mkdir()) {
            marker.delete()
            throw IOException("Could not create restore rollback workspace")
        }
        if (!NeaRerBackupPathSafety.isDirectChild(context.cacheDir, marker)) {
            marker.deleteRecursively()
            throw IOException("Restore rollback workspace is outside private cache")
        }
        return marker
    }

    private fun copyFile(source: File, destination: File) {
        if (!source.isFile || !source.canRead() || destination.exists()) {
            throw IOException("A restore file could not be copied safely")
        }
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, 64 * 1024)
                output.fd.sync()
            }
        }
    }
}
