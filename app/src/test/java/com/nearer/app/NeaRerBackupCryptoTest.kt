package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerBackupCryptoTest {
    private val password = "correct horse battery staple".toCharArray()

    @Test fun clearsPasswordEvenWhenInputValidationFails() {
        val folder = Files.createTempDirectory("nearer-backup-password-clear").toFile()
        try {
            val missingSource = File(folder, "missing.bin")
            val secret = "correct horse battery staple".toCharArray()
            val failure = runCatching {
                NeaRerBackupCrypto.encryptFile(missingSource, File(folder, "backup.nearer"), secret)
            }
            assertTrue(failure.isFailure)
            assertTrue(secret.all { it == '\\u0000' })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun encryptedBackupRoundTripsWithoutLeavingPlaintextInContainer() {
        val folder = Files.createTempDirectory("nearer-backup-test").toFile()
        try {
            val source = File(folder, "source.json")
            val encrypted = File(folder, "backup.nearer")
            val restored = File(folder, "restored.json")
            val content = """{"messages":[{"body":"private conversation"}]}""".toByteArray()
            source.writeBytes(content)

            NeaRerBackupCrypto.encryptFile(source, encrypted, password.copyOf())
            assertFalse(String(encrypted.readBytes()).contains("private conversation"))

            NeaRerBackupCrypto.decryptFile(encrypted, restored, password.copyOf())
            assertArrayEquals(content, restored.readBytes())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun wrongPasswordDoesNotPublishRestoredFile() {
        val folder = Files.createTempDirectory("nearer-backup-wrong-password").toFile()
        try {
            val source = File(folder, "source.bin").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
            val encrypted = File(folder, "backup.nearer")
            val restored = File(folder, "restored.bin")

            NeaRerBackupCrypto.encryptFile(source, encrypted, password.copyOf())
            val failure = runCatching {
                NeaRerBackupCrypto.decryptFile(encrypted, restored, "a completely wrong password".toCharArray())
            }
            assertTrue(failure.isFailure)
            assertFalse(restored.exists())
            assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".partial") })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun tamperedBackupDoesNotPublishRestoredFile() {
        val folder = Files.createTempDirectory("nearer-backup-tamper").toFile()
        try {
            val source = File(folder, "source.bin").apply { writeBytes(ByteArray(2048) { 7 }) }
            val encrypted = File(folder, "backup.nearer")
            val restored = File(folder, "restored.bin")
            NeaRerBackupCrypto.encryptFile(source, encrypted, password.copyOf())

            val bytes = encrypted.readBytes()
            bytes[bytes.lastIndex - 2] = (bytes[bytes.lastIndex - 2].toInt() xor 0x40).toByte()
            encrypted.writeBytes(bytes)

            val failure = runCatching {
                NeaRerBackupCrypto.decryptFile(encrypted, restored, password.copyOf())
            }
            assertTrue(failure.isFailure)
            assertFalse(restored.exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun refusesToOverwriteAnExistingBackup() {
        val folder = Files.createTempDirectory("nearer-backup-no-overwrite").toFile()
        try {
            val source = File(folder, "source.bin").apply { writeText("content") }
            val destination = File(folder, "backup.nearer").apply { writeText("keep me") }
            val failure = runCatching {
                NeaRerBackupCrypto.encryptFile(source, destination, password.copyOf())
            }
            assertTrue(failure.isFailure)
            assertTrue(destination.readText() == "keep me")
        } finally {
            folder.deleteRecursively()
        }
    }
}
