package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerBackupArchiveTest {
    @Test fun encryptedArchiveRestoresNestedFilesAndReportsContents() {
        val root = Files.createTempDirectory("nearer-archive-roundtrip").toFile()
        try {
            val snapshot = File(root, "snapshot").apply { mkdirs() }
            File(snapshot, "profile").mkdirs()
            File(snapshot, "profile/name.json").writeText("""{"name":"NeaRer User"}""")
            File(snapshot, "chats.db").writeBytes(ByteArray(2048) { (it % 251).toByte() })
            val encrypted = File(root, "phone.nearer")
            val restored = File(root, "restored")
            NeaRerBackupArchive.createEncryptedBackup(snapshot, encrypted, "strong backup password".toCharArray())
            val summary = NeaRerBackupArchive.restoreEncryptedBackup(encrypted, restored, "strong backup password".toCharArray())
            assertEquals(2, summary.files)
            assertEquals(File(snapshot, "profile/name.json").readText(), File(restored, "profile/name.json").readText())
            assertTrue(File(restored, "chats.db").readBytes().contentEquals(File(snapshot, "chats.db").readBytes()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesToRestoreOverExistingDirectory() {
        val root = Files.createTempDirectory("nearer-archive-no-overwrite").toFile()
        try {
            val snapshot = File(root, "snapshot").apply { mkdirs() }
            File(snapshot, "data").writeText("safe")
            val encrypted = File(root, "backup.nearer")
            NeaRerBackupArchive.createEncryptedBackup(snapshot, encrypted, "strong backup password".toCharArray())
            val destination = File(root, "existing").apply { mkdirs() }
            File(destination, "keep.txt").writeText("keep")
            val result = runCatching {
                NeaRerBackupArchive.restoreEncryptedBackup(encrypted, destination, "strong backup password".toCharArray())
            }
            assertTrue(result.isFailure)
            assertEquals("keep", File(destination, "keep.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun tamperedPayloadIsRejectedAndNoRestoreFolderIsPublished() {
        val root = Files.createTempDirectory("nearer-archive-tamper").toFile()
        try {
            val snapshot = File(root, "snapshot").apply { mkdirs() }
            File(snapshot, "data.txt").writeText("original")
            val encrypted = File(root, "backup.nearer")
            NeaRerBackupArchive.createEncryptedBackup(snapshot, encrypted, "strong backup password".toCharArray())

            // Modify the authenticated encrypted container: GCM must reject before ZIP extraction.
            val bytes = encrypted.readBytes()
            bytes[bytes.lastIndex - 3] = (bytes[bytes.lastIndex - 3].toInt() xor 1).toByte()
            encrypted.writeBytes(bytes)
            val destination = File(root, "restore")
            val result = runCatching {
                NeaRerBackupArchive.restoreEncryptedBackup(encrypted, destination, "strong backup password".toCharArray())
            }
            assertTrue(result.isFailure)
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
