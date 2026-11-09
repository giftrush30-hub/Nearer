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

    data class Preview(
        val chatMessages: Long,
        val receivedFiles: Int,
        val receivedBytes: Long,
        val displayName: String,
        val includesProfilePhoto: Boolean
    )

    /** Validates the portable snapshot structure and returns safe restore-preview data. */
    @Throws(IOException::class)
    fun inspect(snapshotDirectory: File): Preview {
        if (!snapshotDirectory.isDirectory) throw IOException("Backup snapshot folder is missing")
        val root = snapshotDirectory.canonicalFile
        val profileFile = File(root, "profile.json")
        if (!profileFile.isFile || !isContained(root, profileFile) || profileFile.length() > MAX_PROFILE_BYTES) {
            throw IOException("Backup profile information is missing or too large")
        }
        val profile = try { JSONObject(profileFile.readText(Charsets.UTF_8)) }
            catch (failure: Exception) { throw IOException("Backup profile information is invalid", failure) }
        if (profile.has("hasProfilePhoto") && profile.opt("hasProfilePhoto") !is Boolean) {
            throw IOException("Backup profile photo flag is invalid")
        }
        profileStringKeys.forEach { key ->
            if (profile.has(key)) {
                val value = profile.opt(key)
                if (value !is String || value.length > MAX_PROFILE_VALUE_CHARS) {
                    throw IOException("Backup profile field is invalid: $key")
                }
            }
        }
        // Parse each portable JSON preference using its expected top-level type.
        // JSONTokener.nextValue() alone accepts trailing junk, so use the strict
        // JSONObject/JSONArray constructors and validate queued-message envelopes.
        val objectKeys = setOf("onboarding_answers")
        val arrayKeys = setOf("community_groups", "community_messages",
            "nearby_transfer_history", "recently_seen", "nearby_outbox",
            "nearby_seen_message_ids_ordered")
        (objectKeys + arrayKeys).forEach { key ->
            if (!profile.has(key)) return@forEach
            val raw = profile.optString(key, "")
            if (raw.isEmpty()) return@forEach
            try {
                if (key in objectKeys) {
                    JSONObject(raw)
                } else {
                    val array = org.json.JSONArray(raw)
                    if (key == "nearby_outbox") {
                        if (array.length() > MAX_PENDING_OUTBOX_ITEMS) {
                            throw IOException("Backup pending-message queue is too large")
                        }
                        val ids = HashSet<String>()
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index)
                                ?: throw IOException("Backup pending-message entry is invalid")
                            val id = item.optString("id")
                            val peer = item.optString("peer")
                            val payload = item.optString("payload")
                            if (id.isBlank() || id.length > 80 || !ids.add(id) ||
                                peer.isBlank() || peer.length > 64 ||
                                payload.isBlank() || payload.length > MAX_PENDING_PAYLOAD_CHARS) {
                                throw IOException("Backup pending-message entry has invalid fields")
                            }
                            val envelope = JSONObject(payload)
                            val body = envelope.optString("body")
                            if (envelope.optString("kind") != "message" ||
                                envelope.optString("id") != id || body.isBlank() ||
                                body.length > MAX_PENDING_MESSAGE_BODY_CHARS) {
                                throw IOException("Backup pending-message envelope is invalid")
                            }
                        }
                    } else if (key == "nearby_seen_message_ids_ordered") {
                        if (array.length() > MAX_SEEN_MESSAGE_IDS) {
                            throw IOException("Backup duplicate-protection history is too large")
                        }
                        val ids = HashSet<String>()
                        for (index in 0 until array.length()) {
                            val id = array.optString(index)
                            if (id.isBlank() || id.length > 160 || !ids.add(id)) {
                                throw IOException("Backup duplicate-protection entry is invalid")
                            }
                        }
                    }
                }
            } catch (failure: IOException) { throw failure }
            catch (failure: Exception) {
                throw IOException("Backup profile data is invalid: $key", failure)
            }
        }
        if (profile.optString("format") != "NeaRerLocalSnapshot" || profile.optInt("version") != 1) {
            throw IOException("Backup profile version is not supported")
        }
        val displayName = profile.optString("display_name", "NeaRer User")
        if (displayName.isBlank() || displayName.length > 24) {
            throw IOException("Backup profile name is invalid")
        }
        val avatarId = profile.optString("profile_avatar_id", "orbit")
        if (avatarId !in PROFILE_AVATARS) throw IOException("Backup avatar is not supported")
        if (profile.has("onboardingComplete") && profile.opt("onboardingComplete") !is Boolean) {
            throw IOException("Backup onboarding state is invalid")
        }
        val photo = File(root, "profile/profile-photo.jpg")
        val hasPhoto = profile.optBoolean("hasProfilePhoto", false)
        if (hasPhoto != photo.isFile ||
            photo.length() > MAX_PROFILE_PHOTO_BYTES ||
            (photo.exists() && !photo.canonicalPath.startsWith(root.path + File.separator))
        ) {
            throw IOException("Backup profile photo is missing or invalid")
        }
        val chatFile = File(root, "chats/messages.jsonl")
        if (!chatFile.isFile || !isContained(root, chatFile) || chatFile.length() > MAX_CHAT_ARCHIVE_BYTES) {
            throw IOException("Backup chat archive is missing or too large")
        }
        var chatCount = 0L
        val seenMessageIds = HashSet<String>()
        chatFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.length > MAX_CHAT_RECORD_CHARS) {
                    throw IOException("Backup chat record is invalid")
                }
                try {
                    val item = JSONObject(line)
                    val id = item.optString("id")
                    val body = item.optString("body")
                    val delivery = item.optString("delivery")
                    val conversationId = item.optString("conversationId")
                    val conversationName = item.optString("conversationName")
                    if (id.isBlank() || id.length > 160 || !seenMessageIds.add(id) ||
                        body.length > MAX_CHAT_BODY_CHARS || delivery.length > 40 ||
                        conversationId.length > 200 || conversationName.length > 160 ||
                        item.opt("timestamp") !is Number || item.opt("outgoing") !is Boolean
                    ) {
                        throw IOException("Backup chat record has invalid fields")
                    }
                } catch (failure: IOException) { throw failure }
                catch (failure: Exception) { throw IOException("Backup chat record is invalid", failure) }
                chatCount++
                if (chatCount > MAX_CHAT_MESSAGES) throw IOException("Backup contains too many chat messages")
            }
        }
        val received = File(root, "received")
        if (!received.isDirectory || !isContained(root, received)) throw IOException("Backup received-files folder is missing or unsafe")
        var receivedCount = 0
        var receivedBytes = 0L
        val receivedItems = received.listFiles() ?: throw IOException("Could not inspect received files")
        receivedItems.forEach { item ->
            if (!item.isFile || !isContained(received, item)) {
                throw IOException("Backup contains an unsafe received-file entry")
            }
            receivedBytes += item.length()
            if (item.length() > MAX_RECEIVED_FILE_BYTES || receivedBytes > MAX_RECEIVED_TOTAL_BYTES) {
                throw IOException("Backup received files exceed supported limits")
            }
            receivedCount++
        }
        return Preview(chatCount, receivedCount, receivedBytes, displayName, hasPhoto)
    }

    private val profileStringKeys = setOf(
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
            // Older installs may have only the legacy unordered Set. Convert it to
            // the ordered portable representation so duplicate protection survives.
            if (!profile.has("nearby_seen_message_ids_ordered")) {
                val legacySeenIds = all["nearby_seen_message_ids"] as? Set<*>
                if (!legacySeenIds.isNullOrEmpty()) {
                    profile.put("nearby_seen_message_ids_ordered", org.json.JSONArray().apply {
                        legacySeenIds.filterIsInstance<String>().forEach { put(it) }
                    }.toString())
                }
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

    private fun isContained(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath.startsWith(parentPath + File.separator)
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
    private const val MAX_PROFILE_BYTES = 1024 * 1024L
    private const val MAX_PROFILE_VALUE_CHARS = 512 * 1024
    private const val MAX_PENDING_OUTBOX_ITEMS = 10_000
    private const val MAX_PENDING_PAYLOAD_CHARS = 12_000
    private const val MAX_PENDING_MESSAGE_BODY_CHARS = 4_000
    private const val MAX_SEEN_MESSAGE_IDS = 20_000
    private const val MAX_PROFILE_PHOTO_BYTES = 5L * 1024L * 1024L
    private const val MAX_CHAT_ARCHIVE_BYTES = 64L * 1024L * 1024L
    private const val MAX_CHAT_RECORD_CHARS = 2 * 1024 * 1024
    private const val MAX_CHAT_MESSAGES = 200_000L
    private const val MAX_CHAT_BODY_CHARS = 1 * 1024 * 1024
    private val PROFILE_AVATARS = setOf("aurora", "spark", "wave", "sun", "orbit", "bloom", "leaf", "star")
    private const val MAX_RECEIVED_FILE_BYTES = 512L * 1024L * 1024L
    private const val MAX_RECEIVED_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
}
