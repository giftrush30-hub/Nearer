package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Durable receiver-side idempotency record for successfully published streams.
 * It lets a sender that crashed before seeing the completion receipt safely
 * retry metadata without causing the same file to be published a second time.
 */
internal class VerifiedTransferReceiptStore(private val directory: File) {
    data class ReceiptRecord(
        val peerId: String,
        val transferId: String,
        val itemId: String,
        val sha256: String,
        val sizeBytes: Long,
        val completedFile: File
    )

    @Synchronized
    fun record(
        peerId: String,
        metadata: TransferResumeStreamMetadataCodec.Metadata,
        completedFile: File,
        verifiedSha256: String
    ) {
        val normalized = NeaRerIdentity.normalize(peerId)
            ?: throw SecurityException("The source peer is not authenticated")
        val completed = completedFile.canonicalFile
        val receivedRoot = File(directory.parentFile, "received").canonicalFile
        require(completed.path.startsWith(receivedRoot.path + File.separator)) {
            "Verified transfer must be published inside the received-files folder"
        }
        require(completed.isFile && completed.length() == metadata.item.sizeBytes)
        require(verifiedSha256.equals(metadata.wholeFileSha256, ignoreCase = true))
        require(directory.exists() || directory.mkdirs()) { "Could not create completed-transfer journal" }
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("peerId", normalized)
            setProperty("transferId", metadata.transferId)
            setProperty("itemId", metadata.item.id)
            setProperty("relativePath", metadata.item.relativePath)
            setProperty("displayName", metadata.item.displayName)
            metadata.item.mimeType?.let { setProperty("mimeType", it) }
            setProperty("sha256", metadata.wholeFileSha256.lowercase())
            setProperty("sizeBytes", metadata.item.sizeBytes.toString())
            setProperty("completedPath", completed.canonicalPath)
        }
        val target = recordFile(metadata.transferId, metadata.item.id)
        val temporary = File(directory, target.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, "NeaRer verified HyperTransfer receipt")
            output.fd.sync()
        }
        try {
            java.nio.file.Files.move(
                temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not commit completed-transfer receipt", error)
        }
    }

    @Synchronized
    fun containsVerified(
        peerId: String,
        metadata: TransferResumeStreamMetadataCodec.Metadata
    ): Boolean {
        val normalized = NeaRerIdentity.normalize(peerId) ?: return false
        val target = recordFile(metadata.transferId, metadata.item.id)
        if (!target.isFile) return false
        val record = try {
            val p = Properties()
            FileInputStream(target).use { input -> p.load(input) }
            if (p.getProperty("version") != "1") return false
            val storedPeer = p.getProperty("peerId") ?: return false
            val transferId = p.getProperty("transferId") ?: return false
            val itemId = p.getProperty("itemId") ?: return false
            val relativePath = p.getProperty("relativePath") ?: return false
            val displayName = p.getProperty("displayName") ?: return false
            val mimeType = p.getProperty("mimeType")
            val sha = p.getProperty("sha256") ?: return false
            val size = p.getProperty("sizeBytes")?.toLongOrNull() ?: return false
            val path = p.getProperty("completedPath") ?: return false
            if (storedPeer != normalized || transferId != metadata.transferId ||
                itemId != metadata.item.id || relativePath != metadata.item.relativePath ||
                displayName != metadata.item.displayName || mimeType != metadata.item.mimeType ||
                !sha.equals(metadata.wholeFileSha256, true) || size != metadata.item.sizeBytes) return false
            val file = File(path).canonicalFile
            val receivedRoot = File(directory.parentFile, "received").canonicalFile
            if (!file.path.startsWith(receivedRoot.path + File.separator) ||
                !file.isFile || file.length() != size ||
                !hash(file).equals(sha, ignoreCase = true)) return false
            ReceiptRecord(storedPeer, transferId, itemId, sha, size, file)
        } catch (_: Exception) {
            null
        } ?: return false
        return record.peerId == normalized
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun recordFile(transferId: String, itemId: String): File =
        File(directory, digestName("$transferId|$itemId") + ".properties")

    private fun digestName(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

}
