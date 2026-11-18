package com.nearer.app

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Stages a cross-platform LAN file stream in app-private storage, writes only
 * sequential bounded chunks, emits durable 4 MiB checkpoint ACKs, and atomically
 * finalizes only after both declared and computed SHA-256 values match.
 */
internal class NearbyLanFileReceiver(
    val metadata: TransferResumeStreamMetadataCodec.Metadata,
    destinationDirectory: File,
    private val checkpointIntervalBytes: Long = 4L * 1024L * 1024L
) : AutoCloseable {
    data class Finalized(val file: File, val finalAck: NearbyTransferFileChunkCodec.Ack)

    companion object {
        private const val BUFFER_BYTES = 64 * 1024
    }

    private val root = destinationDirectory.apply { mkdirs() }.canonicalFile
    private val stagingDirectory = File(root, ".nearer-staging").apply { mkdirs() }
    private val stagingFile = File(stagingDirectory, "${UUID.randomUUID()}.partial")
    private val destinationFile: File
    private val output: FileOutputStream
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytesWritten = 0L
    private var finished = false

    init {
        require(checkpointIntervalBytes > 0L)
        TransferResumeStreamMetadataCodec.validate(metadata)
        require(metadata.item.kind == TransferBundleItem.Kind.FILE)
        val relative = metadata.item.relativePath
        val target = File(root, relative).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "Unsafe transfer destination path" }
        destinationFile = target
        require(!destinationFile.exists()) { "Destination file already exists" }
        require(stagingDirectory.canonicalPath.startsWith(root.path + File.separator)) {
            "Unsafe staging directory"
        }
        check(stagingFile.createNewFile()) { "Could not create transfer staging file" }
        output = FileOutputStream(stagingFile)
    }

    /** Initial zero-offset ACK; send only after the user accepts the incoming transfer. */
    @Synchronized
    fun initialAck(): NearbyTransferFileChunkCodec.Ack {
        check(!finished && bytesWritten == 0L)
        val emptyDigest = MessageDigest.getInstance("SHA-256").digest().toHex()
        return NearbyTransferFileChunkCodec.Ack(
            metadata.payloadId, metadata.transferId, metadata.item.id, 0L, emptyDigest
        )
    }

    /** Returns an ACK only at a durable checkpoint boundary. */
    @Synchronized
    fun append(chunk: NearbyTransferFileChunkCodec.Chunk): NearbyTransferFileChunkCodec.Ack? {
        check(!finished) { "Transfer receiver is closed" }
        require(chunk.payloadId == metadata.payloadId &&
            chunk.transferId == metadata.transferId && chunk.itemId == metadata.item.id) {
            "Chunk belongs to another transfer"
        }
        require(chunk.offsetBytes == bytesWritten) { "Out-of-order transfer chunk" }
        require(chunk.data.isNotEmpty() && bytesWritten <= metadata.item.sizeBytes - chunk.data.size) {
            "Chunk exceeds declared file size"
        }
        output.write(chunk.data)
        digest.update(chunk.data)
        bytesWritten += chunk.data.size.toLong()
        if (bytesWritten < metadata.item.sizeBytes && bytesWritten % checkpointIntervalBytes == 0L) {
            output.fd.sync()
            val checkpoint = try {
                (digest.clone() as MessageDigest).digest()
            } catch (error: CloneNotSupportedException) {
                throw IllegalStateException("SHA-256 provider cannot safely checkpoint stream state", error)
            }
            return NearbyTransferFileChunkCodec.Ack(
                metadata.payloadId, metadata.transferId, metadata.item.id,
                bytesWritten, checkpoint.toHex()
            )
        }
        return null
    }

    @Synchronized
    fun finish(end: NearbyTransferFileChunkCodec.End): Finalized {
        check(!finished) { "Transfer receiver is closed" }
        require(end.payloadId == metadata.payloadId &&
            end.transferId == metadata.transferId && end.itemId == metadata.item.id &&
            end.totalSizeBytes == metadata.item.sizeBytes && bytesWritten == metadata.item.sizeBytes) {
            "Transfer end does not match received stream"
        }
        val actualDigest = digest.digest().toHex()
        require(actualDigest == metadata.wholeFileSha256.lowercase() &&
            actualDigest == end.wholeFileSha256.lowercase()) {
            "Whole-file SHA-256 mismatch"
        }
        output.fd.sync()
        output.close()
        destinationFile.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "Could not create destination folder" }
            val canonicalParent = parent.canonicalFile
            require(canonicalParent.path == root.path ||
                canonicalParent.path.startsWith(root.path + File.separator)) {
                "Destination folder escapes receive directory"
            }
        }
        require(!destinationFile.exists()) { "Destination file already exists" }
        Files.move(stagingFile.toPath(), destinationFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        finished = true
        return Finalized(
            destinationFile,
            NearbyTransferFileChunkCodec.Ack(
                metadata.payloadId, metadata.transferId, metadata.item.id,
                bytesWritten, actualDigest
            )
        )
    }

    @Synchronized
    override fun close() {
        if (finished) return
        runCatching { output.close() }
        runCatching { stagingFile.delete() }
        finished = true
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
