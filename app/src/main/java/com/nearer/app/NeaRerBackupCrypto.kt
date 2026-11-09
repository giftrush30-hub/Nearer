package com.nearer.app

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated, password-protected file container for future NeaRer backup exports.
 *
 * Decryption writes only to a private temporary file. The destination becomes visible
 * only after AES-GCM has authenticated the complete ciphertext and tag.
 */
internal object NeaRerBackupCrypto {
    private val magic = byteArrayOf(0x4E, 0x45, 0x41, 0x52, 0x45, 0x52, 0x42, 0x4B) // NEARERBK
    private const val VERSION = 1
    private const val ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 180_000
    private const val MAX_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val BUFFER_BYTES = 64 * 1024
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + NONCE_BYTES

    @Throws(IOException::class, GeneralSecurityException::class)
    fun encryptFile(source: File, destination: File, password: CharArray) {
        requirePassword(password)
        require(source.isFile && source.canRead()) { "Backup source is not a readable file" }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination must differ" }
        val temporary = createTemporary(destination)
        try {
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
            val header = makeHeader(ITERATIONS, salt, nonce)
            val cipher = cipher(Cipher.ENCRYPT_MODE, password, ITERATIONS, salt, nonce, header)
            FileOutputStream(temporary).use { output ->
                output.write(header)
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(output::write)
                    }
                }
                cipher.doFinal().let(output::write)
                output.fd.sync()
            }
            publish(temporary, destination)
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        } finally {
            password.fill('\u0000')
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun decryptFile(source: File, destination: File, password: CharArray) {
        requirePassword(password)
        require(source.isFile && source.canRead()) { "Backup file is not readable" }
        require(source.canonicalFile != destination.canonicalFile) { "Source and destination must differ" }
        require(source.length() > HEADER_BYTES + 16L) { "Backup file is incomplete" }
        val temporary = createTemporary(destination)
        try {
            FileInputStream(source).use { input ->
                val header = ByteArray(HEADER_BYTES)
                DataInputStream(input).readFully(header)
                val parsed = parseHeader(header)
                val cipher = cipher(Cipher.DECRYPT_MODE, password, parsed.iterations, parsed.salt, parsed.nonce, header)
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(output::write)
                    }
                    // GCM authenticates the entire backup here. Do not publish before this succeeds.
                    cipher.doFinal().let(output::write)
                    output.fd.sync()
                }
            }
            publish(temporary, destination)
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        } finally {
            password.fill('\u0000')
        }
    }

    private fun requirePassword(password: CharArray) {
        require(password.size >= 12) { "Use a backup password with at least 12 characters" }
    }

    private fun makeHeader(iterations: Int, salt: ByteArray, nonce: ByteArray): ByteArray {
        val header = java.io.ByteArrayOutputStream(HEADER_BYTES)
        java.io.DataOutputStream(header).use { out ->
            out.write(magic)
            out.writeByte(VERSION)
            out.writeInt(iterations)
            out.write(salt)
            out.write(nonce)
        }
        return header.toByteArray()
    }

    private data class ParsedHeader(val iterations: Int, val salt: ByteArray, val nonce: ByteArray)

    private fun parseHeader(header: ByteArray): ParsedHeader {
        val input = DataInputStream(header.inputStream())
        val actualMagic = ByteArray(magic.size).also(input::readFully)
        if (!actualMagic.contentEquals(magic)) throw IOException("This is not a NeaRer backup")
        val version = input.readUnsignedByte()
        if (version != VERSION) throw IOException("This NeaRer backup version is not supported")
        val iterations = input.readInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw IOException("Backup security settings are invalid")
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val nonce = ByteArray(NONCE_BYTES).also(input::readFully)
        return ParsedHeader(iterations, salt, nonce)
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        iterations: Int,
        salt: ByteArray,
        nonce: ByteArray,
        header: ByteArray
    ): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun createTemporary(destination: File): File {
        val parent = destination.absoluteFile.parentFile
            ?: throw IOException("Backup destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create backup folder")
        if (destination.exists()) throw IOException("Destination already exists; choose a new file name")
        return File.createTempFile(".${destination.name.take(40)}.", ".partial", parent)
    }

    private fun publish(temporary: File, destination: File) {
        // Reserve the name first: ATOMIC_MOVE alone may replace an existing target
        // on some filesystem providers, even without REPLACE_EXISTING.
        try {
            Files.createFile(destination.toPath())
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            throw IOException("Destination already exists")
        }
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
    }
}
