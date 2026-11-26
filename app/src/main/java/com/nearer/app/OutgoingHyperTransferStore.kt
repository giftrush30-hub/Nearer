package com.nearer.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Durable sender-side source and metadata for HyperTransfer.
 *
 * Unlike cache staging, files in this store survive normal process death and
 * cache eviction. A record is published atomically only after the staged source
 * has been synced and its length/hash have been verified.
 */
internal class OutgoingHyperTransferStore(private val directory: File) {
    data class Record(
        val transferId: String,
        val peerId: String,
        val itemId: String,
        val relativePath: String,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String?,
        val sha256: String,
        val sourceFile: File
    )

    @Synchronized
    fun persist(
        transferId: String,
        peerId: String,
        item: TransferBundleItem,
        sha256: String,
        sourceFile: File
    ): Record {
        require(transferId.isNotBlank() && transferId.length <= 128)
        require(NeaRerIdentity.normalize(peerId) == peerId)
        require(item.kind == TransferBundleItem.Kind.FILE)
        require(sha256.matches(SHA256))
        require(sourceFile.isFile && sourceFile.length() == item.sizeBytes)
        require(directory.exists() || directory.mkdirs()) { "Could not create durable outgoing transfer folder" }

        // The staged source must already be inside this app-private durable folder.
        val root = directory.canonicalFile
        val source = sourceFile.canonicalFile
        require(source.parentFile == root) { "Outgoing source is outside the durable transfer folder" }
        // Force staged bytes to stable storage before publishing their recovery record.
        FileOutputStream(source, true).use { it.fd.sync() }
        require(hash(source).equals(sha256, ignoreCase = true)) { "Outgoing source digest does not match its record" }

        val record = Record(
            transferId, peerId, item.id, item.relativePath, item.displayName,
            item.sizeBytes, item.mimeType, sha256.lowercase(), source
        )
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("transferId", record.transferId)
            setProperty("peerId", record.peerId)
            setProperty("itemId", record.itemId)
            setProperty("relativePath", record.relativePath)
            setProperty("displayName", record.displayName)
            setProperty("sizeBytes", record.sizeBytes.toString())
            record.mimeType?.let { setProperty("mimeType", it) }
            setProperty("sha256", record.sha256)
            setProperty("sourceName", record.sourceFile.name)
        }
        val target = recordFile(transferId)
        if (target.exists()) {
            if (readRecord(target) != null) {
                throw IllegalStateException("This transfer is already durably queued")
            }
            target.delete()
        }
        val temp = File(directory, target.name + ".tmp")
        FileOutputStream(temp).use { output ->
            properties.store(output, "NeaRer durable outgoing HyperTransfer")
            output.fd.sync()
        }
        try {
            java.nio.file.Files.move(
                temp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            temp.delete()
            throw IllegalStateException("Could not commit outgoing transfer record", error)
        }
        return record
    }

    @Synchronized
    fun containsVerifiedTransfer(transferId: String, peerId: String): Boolean {
        val normalized = NeaRerIdentity.normalize(peerId) ?: return false
        return readRecord(recordFile(transferId))?.peerId == normalized
    }

    @Synchronized
    fun restoreForPeer(peerId: String): List<Record> {
        val normalized = NeaRerIdentity.normalize(peerId) ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.name.endsWith(".properties") }
            .orEmpty().mapNotNull { readRecord(it) }
            .filter { it.peerId == normalized }
    }

    @Synchronized
    fun remove(transferId: String, deleteSource: Boolean = true) {
        val file = recordFile(transferId)
        val sourceName = if (file.isFile) {
            runCatching {
                val properties = Properties()
                FileInputStream(file).use { input -> properties.load(input) }
                properties.getProperty("sourceName")
            }.getOrNull()
        } else null
        if (file.exists()) file.delete()
        if (deleteSource && !sourceName.isNullOrBlank() && sourceName == File(sourceName).name) {
            runCatching {
                val source = File(directory, sourceName).canonicalFile
                if (source.parentFile == directory.canonicalFile) source.delete()
            }
        }
    }

    @Synchronized
    fun removeRecordOnly(transferId: String) = remove(transferId, deleteSource = false)

    private fun readRecord(file: File): Record? {
        return try {
        val p = Properties()
        FileInputStream(file).use { input -> p.load(input) }
        if (p.getProperty("version") != "1") return null
        val transferId = p.getProperty("transferId") ?: return null
        val peerId = p.getProperty("peerId") ?: return null
        val itemId = p.getProperty("itemId") ?: return null
        val relativePath = p.getProperty("relativePath") ?: return null
        val displayName = p.getProperty("displayName") ?: return null
        val size = p.getProperty("sizeBytes")?.toLongOrNull() ?: return null
        val mimeType = p.getProperty("mimeType")
        val sha = p.getProperty("sha256") ?: return null
        val sourceName = p.getProperty("sourceName") ?: return null
        if (file.name != recordFile(transferId).name ||
            NeaRerIdentity.normalize(peerId) != peerId ||
            !sha.matches(SHA256) || size < 0L ||
            sourceName != File(sourceName).name || sourceName.isBlank()) return null
        val source = File(directory, sourceName).canonicalFile
        if (source.parentFile != directory.canonicalFile || !source.isFile || source.length() != size) return null
        if (!hash(source).equals(sha, ignoreCase = true)) return null
        val item = TransferBundleItem(itemId, relativePath, displayName, size, mimeType)
        if (TransferBundleManifest(transferId, listOf(item)).validationErrors().isNotEmpty()) return null
        Record(transferId, peerId, itemId, relativePath, displayName, size, mimeType, sha.lowercase(), source)
        } catch (_: Exception) {
            null
        }
    }

    private fun recordFile(transferId: String): File =
        File(directory, digestName(transferId) + ".properties")

    private fun digestName(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val SHA256 = Regex("^[a-fA-F0-9]{64}$")
    }
}
