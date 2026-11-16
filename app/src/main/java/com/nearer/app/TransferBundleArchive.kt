package com.nearer.app

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Deterministic safe container for folder/multi-file HyperTransfer.
 * The outer archive is sent as one resumable STREAM; the embedded manifest
 * restores the original relative paths only after the complete archive hash
 * has been verified by the normal HyperTransfer finalizer.
 */
internal object TransferBundleArchive {
    const val MANIFEST_ENTRY = "manifest.bin"
    const val FILES_PREFIX = "files/"

    data class Source(
        val file: File,
        val relativePath: String,
        val mimeType: String? = null
    )

    fun pack(
        transferId: String,
        sources: List<Source>,
        archive: File
    ): TransferBundleManifest {
        require(sources.isNotEmpty()) { "Choose at least one file to share" }
        require(sources.size <= TransferBundleManifest.MAX_ITEMS) { "Too many files in this bundle" }
        val items = LinkedHashMap<String, TransferBundleItem>()
        val parentDirectories = linkedSetOf<String>()
        sources.forEach { source ->
            require(source.file.isFile) { "A selected bundle source is not a file" }
            val path = source.relativePath
            val name = path.substringAfterLast('/')
            val item = TransferBundleItem(
                UUID.randomUUID().toString(), path, name, source.file.length(), source.mimeType
            )
            val errors = TransferBundleManifest(transferId, listOf(item)).validationErrors()
            require(errors.isEmpty()) { errors.joinToString("; ") }
            require(items.put(path, item) == null) { "Two selected files use the same relative path" }
            val parts = path.split('/')
            for (index in 1 until parts.size) parentDirectories += parts.take(index).joinToString("/")
        }
        parentDirectories.forEach { directory ->
            require(directory !in items) { "A file cannot be a parent folder" }
            items.putIfAbsent(
                directory,
                TransferBundleItem(
                    UUID.randomUUID().toString(), directory, directory.substringAfterLast('/'),
                    0L, null, TransferBundleItem.Kind.DIRECTORY
                )
            )
        }
        val manifest = TransferBundleManifest(transferId, items.values.toList())
        val errors = manifest.validationErrors()
        require(errors.isEmpty()) { errors.joinToString("; ") }

        archive.parentFile?.let { require(it.exists() || it.mkdirs()) }
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(TransferBundleManifestCodec.encode(manifest))
            zip.closeEntry()
            manifest.items.filter { it.kind == TransferBundleItem.Kind.DIRECTORY }
                .forEach { item ->
                    zip.putNextEntry(ZipEntry(FILES_PREFIX + item.relativePath.trimEnd('/') + "/"))
                    zip.closeEntry()
                }
            val byPath = sources.associateBy { it.relativePath }
            manifest.items.filter { it.kind == TransferBundleItem.Kind.FILE }.forEach { item ->
                val source = byPath[item.relativePath]
                    ?: throw IllegalStateException("Bundle source is missing")
                require(source.file.length() == item.sizeBytes) { "A selected file changed while preparing the bundle" }
                zip.putNextEntry(ZipEntry(FILES_PREFIX + item.relativePath))
                FileInputStream(source.file).use { input -> input.copyTo(zip, 256 * 1024) }
                zip.closeEntry()
                require(source.file.length() == item.sizeBytes) { "A selected file changed while preparing the bundle" }
            }
        }
        return manifest
    }

    /**
     * Extracts only entries named by the validated manifest. No archive entry
     * is allowed to choose an arbitrary output path or overwrite another item.
     * On any failure, the incomplete destination tree is removed.
     */
    private fun readBounded(input: java.io.InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(output.size() <= maximumBytes - count) { "Bundle manifest exceeds its maximum size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun extract(archive: File, destinationDirectory: File): TransferBundleManifest {
        require(archive.isFile) { "Bundle archive is unavailable" }
        require(!destinationDirectory.exists()) { "Bundle destination already exists" }
        val parent = destinationDirectory.parentFile
            ?: throw IllegalArgumentException("Bundle destination has no parent folder")
        require(parent.exists() || parent.mkdirs()) { "Could not create bundle destination parent" }
        try {
            ZipFile(archive).use { zip ->
                val manifestEntry = zip.getEntry(MANIFEST_ENTRY)
                    ?: throw IllegalArgumentException("Bundle manifest is missing")
                require(!manifestEntry.isDirectory && manifestEntry.size in 1..TransferBundleManifestCodec.MAX_ENCODED_BYTES.toLong()) {
                    "Bundle manifest size is invalid"
                }
                val manifest = zip.getInputStream(manifestEntry).use { input ->
                    TransferBundleManifestCodec.decode(readBounded(input, TransferBundleManifestCodec.MAX_ENCODED_BYTES))
                }
                val expectedNames = linkedSetOf(MANIFEST_ENTRY)
                manifest.items.forEach { item ->
                    expectedNames += if (item.kind == TransferBundleItem.Kind.DIRECTORY) {
                        FILES_PREFIX + item.relativePath.trimEnd('/') + "/"
                    } else {
                        FILES_PREFIX + item.relativePath
                    }
                }
                val actualNames = linkedSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(actualNames.add(entry.name)) { "Bundle contains duplicate archive entries" }
                }
                require(actualNames == expectedNames) { "Bundle entries do not match its manifest" }

                var totalBytes = 0L
                manifest.items.filter { it.kind == TransferBundleItem.Kind.FILE }.forEach { item ->
                    totalBytes = Math.addExact(totalBytes, item.sizeBytes)
                }
                require(ReceivedFileStoragePolicy.hasEnoughSpace(totalBytes, parent.usableSpace)) {
                    "Not enough free storage to extract this folder"
                }

                require(destinationDirectory.mkdirs()) { "Could not create bundle destination folder" }
                val root = destinationDirectory.canonicalFile
                manifest.items.forEach { item ->
                    val target = File(root, item.relativePath).canonicalFile
                    require(target.path.startsWith(root.path + File.separator)) {
                        "Bundle path escaped the destination folder"
                    }
                    if (item.kind == TransferBundleItem.Kind.DIRECTORY) {
                        require(target.exists() || target.mkdirs()) { "Could not create a received folder" }
                    } else {
                        val entry = zip.getEntry(FILES_PREFIX + item.relativePath)
                            ?: throw IllegalArgumentException("A bundle file is missing")
                        require(!entry.isDirectory && entry.size == item.sizeBytes) {
                            "A bundle file size does not match its manifest"
                        }
                        target.parentFile?.let { require(it.exists() || it.mkdirs()) }
                        var copied = 0L
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    copied = Math.addExact(copied, count.toLong())
                                    require(copied <= item.sizeBytes) { "A bundle file exceeded its declared size" }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        require(copied == item.sizeBytes) { "A bundle file was truncated" }
                    }
                }
                return manifest
            }
        } catch (error: Exception) {
            destinationDirectory.deleteRecursively()
            throw error
        }
    }
}
