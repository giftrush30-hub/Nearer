package com.nearer.app

import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * A validated, non-mutating description of what a staged backup would restore.
 *
 * The plan deliberately contains only portable allow-listed values. Device permissions,
 * active relay consent, Android content URIs, transport sockets and the local NeaRer
 * identity are never imported from another installation.
 *
 * File references point into PreparedRestore's private staging directory. The caller
 * must keep that PreparedRestore open for the entire lifetime of this plan.
 */
internal data class NeaRerBackupRestorePlan(
    val preview: NeaRerBackupSnapshot.Preview,
    val onboardingComplete: Boolean,
    val portablePreferences: Map<String, String>,
    val profilePhoto: File?,
    val receivedFiles: List<File>,
    val excludedDeviceState: List<String>
) {
    companion object {
        private val preferenceKeys = setOf(
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

        private val exclusions = listOf(
            "Public NeaRer identity and device-specific identity credentials",
            "Android runtime permissions and nearby-device consent",
            "Active mesh-relay setting; relay requires fresh consent on this phone",
            "Pending outgoing file selections and Android content-URI grants",
            "Live Bluetooth, Wi-Fi Direct and Nearby Connections sessions"
        )

        /**
         * Re-validates the staged snapshot and builds a read-only restore plan.
         * No SharedPreferences, SQLite rows, profile files or received files are changed.
         */
        @Throws(IOException::class)
        fun fromStagedDirectory(directory: File): NeaRerBackupRestorePlan {
            val preview = NeaRerBackupSnapshot.inspect(directory)
            val root = directory.canonicalFile
            val profileFile = File(root, "profile.json")
            val profile = try {
                JSONObject(profileFile.readText(Charsets.UTF_8))
            } catch (failure: Exception) {
                throw IOException("Could not read validated restore profile", failure)
            }

            val values = linkedMapOf<String, String>()
            preferenceKeys.forEach { key ->
                if (profile.has(key)) {
                    val value = profile.opt(key)
                    if (value !is String) throw IOException("Restore preference is not text: $key")
                    values[key] = value
                }
            }

            val photo = if (preview.includesProfilePhoto) {
                File(root, "profile/profile-photo.jpg").also {
                    if (!it.isFile || !NeaRerBackupPathSafety.isContained(root, it)) {
                        throw IOException("Staged profile photo is missing or unsafe")
                    }
                }
            } else null

            val receivedRoot = File(root, "received")
            if (!receivedRoot.isDirectory || !NeaRerBackupPathSafety.isContained(root, receivedRoot)) {
                throw IOException("Staged received-files folder is missing or unsafe")
            }
            val received = (receivedRoot.listFiles()
                ?: throw IOException("Could not list staged received files"))
                .sortedBy { it.name }
                .map { file ->
                    if (!file.isFile || !NeaRerBackupPathSafety.isContained(receivedRoot, file)) {
                        throw IOException("Staged received-file entry is unsafe")
                    }
                    file
                }

            return NeaRerBackupRestorePlan(
                preview = preview,
                onboardingComplete = profile.optBoolean("onboardingComplete", false),
                portablePreferences = values.toMap(),
                profilePhoto = photo,
                receivedFiles = received,
                excludedDeviceState = exclusions.toList()
            )
        }
    }
}
