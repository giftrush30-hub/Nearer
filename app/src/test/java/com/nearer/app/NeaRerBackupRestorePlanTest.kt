package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerBackupRestorePlanTest {
    @Test fun createsReadOnlyPlanFromValidatedStagingDirectory() {
        val root = Files.createTempDirectory("nearer-restore-plan").toFile()
        try {
            File(root, "profile").mkdirs()
            File(root, "chats").mkdirs()
            File(root, "received").mkdirs()
            File(root, "received/notes.txt").writeText("hello")
            File(root, "chats/messages.jsonl").writeText("")
            File(root, "profile.json").writeText(
                JSONObject()
                    .put("format", "NeaRerLocalSnapshot")
                    .put("version", 1)
                    .put("onboardingComplete", true)
                    .put("display_name", "Ada")
                    .put("profile_avatar_id", "orbit")
                    .put("community_groups", "[]")
                    .put("hasProfilePhoto", false)
                    .toString()
            )

            val plan = NeaRerBackupRestorePlan.fromPreparedRestore(
                NeaRerBackupManager.PreparedRestore(root, NeaRerBackupSnapshot.inspect(root))
            )

            assertEquals("Ada", plan.portablePreferences["display_name"])
            assertEquals("orbit", plan.portablePreferences["profile_avatar_id"])
            assertTrue(plan.onboardingComplete)
            assertEquals(1, plan.receivedFiles.size)
            assertEquals("hello", plan.receivedFiles.single().readText())
            assertNull(plan.profilePhoto)
            assertTrue(plan.excludedDeviceState.any { it.contains("permissions") })
            // Planning must not consume, move or alter staged content.
            assertTrue(File(root, "received/notes.txt").isFile)
            assertTrue(File(root, "chats/messages.jsonl").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun doesNotIncludeUnlistedOrDeviceSpecificProfileKeys() {
        val root = Files.createTempDirectory("nearer-restore-plan-allowlist").toFile()
        try {
            File(root, "profile").mkdirs()
            File(root, "chats").mkdirs()
            File(root, "received").mkdirs()
            File(root, "chats/messages.jsonl").writeText("")
            File(root, "profile.json").writeText(
                JSONObject()
                    .put("format", "NeaRerLocalSnapshot")
                    .put("version", 1)
                    .put("display_name", "Ada")
                    .put("mesh_relay_enabled", true)
                    .put("profile_photo_path", "/old/phone/private/photo.jpg")
                    .put("hasProfilePhoto", false)
                    .toString()
            )

            val plan = NeaRerBackupRestorePlan.fromPreparedRestore(
                NeaRerBackupManager.PreparedRestore(root, NeaRerBackupSnapshot.inspect(root))
            )

            assertEquals(setOf("display_name"), plan.portablePreferences.keys)
            assertFalse(plan.portablePreferences.containsKey("mesh_relay_enabled"))
            assertFalse(plan.portablePreferences.containsKey("profile_photo_path"))
        } finally {
            root.deleteRecursively()
        }
    }
}
