package com.nearer.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AppShareArchiveInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val apkFiles: List<File>,
    val totalBytes: Long
)

/**
 * Packs a complete installed APK set into one transferable archive and validates it
 * on the receiving phone before it can be offered to Android's package installer.
 *
 * This never includes application-private data, accounts, preferences or cache.
 */
class AppShareArchive(private val context: Context) {
    companion object {
        private const val MANIFEST_ENTRY = "nearer-appshare.json"
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_APK_COUNT = 64
    }

    fun create(prepared: PreparedAppPackage): File {
        require(prepared.files.isNotEmpty()) { "No APK files were prepared." }
        require(prepared.files.size <= MAX_APK_COUNT) { "This app has too many APK components to share." }
        val directory = File(context.cacheDir, "appshare/outgoing").apply { mkdirs() }
        val archive = File(directory, "${UUID.randomUUID()}.nearerapp")
        val entries = JSONArray()
        prepared.files.forEachIndexed { index, file ->
            require(file.isFile && file.canRead()) { "An APK component is unavailable." }
            val entryName = if (index == 0) "base.apk" else "splits/split_$index.apk"
            entries.put(JSONObject()
                .put("name", entryName)
                .put("size", file.length())
                .put("sha256", sha256(file)))
        }
        val manifest = JSONObject()
            .put("format", "nearer-appshare")
            .put("formatVersion", 1)
            .put("packageName", prepared.packageName)
            .put("label", prepared.label)
            .put("versionName", prepared.versionName ?: JSONObject.NULL)
            .put("versionCode", prepared.versionCode)
            .put("apkFiles", entries)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                prepared.files.forEachIndexed { index, file ->
                    val entryName = if (index == 0) "base.apk" else "splits/split_$index.apk"
                    zip.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
            }
            return archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        }
    }

    /**
     * Extracts only known APK entries, checks every size/hash, then verifies package
     * identity, version and signing-certificate continuity across all APK components.
     */
    fun validateAndExtract(archive: File): AppShareArchiveInfo {
        require(archive.isFile && archive.canRead()) { "The received app package is unavailable." }
        val destination = File(context.cacheDir, "appshare/incoming/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var manifestBytes: ByteArray? = null
            var expandedBytes = 0L
            val maxExpandedBytes = archive.length().coerceAtLeast(1L) * 8L + 32L * 1024L * 1024L
            val extracted = linkedMapOf<String, File>()
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Unexpected folder in the app package." }
                    val name = entry.name
                    require(name == MANIFEST_ENTRY || name == "base.apk" || name.matches(Regex("splits/split_[1-9][0-9]*\\.apk"))) {
                        "The app package contains an unexpected file."
                    }
                    require(!name.startsWith("/") && !name.contains("..") && !name.contains('\\')) {
                        "The app package contains an unsafe file path."
                    }
                    if (name == MANIFEST_ENTRY) {
                        require(manifestBytes == null) { "The app package contains duplicate manifests." }
                        val bytes = readCurrentEntryLimited(zip, MAX_MANIFEST_BYTES)
                        manifestBytes = bytes
                    } else {
                        require(!extracted.containsKey(name)) { "The app package contains duplicate APK files." }
                        val target = File(destination, name).apply { parentFile?.mkdirs() }
                        val declaredSize = entry.size
                        require(declaredSize < 0L || declaredSize <= maxExpandedBytes - expandedBytes) {
                            "The app package expands beyond a safe size."
                        }
                        val copied = FileOutputStream(target).use { output ->
                            copyCurrentEntryBounded(zip, output, maxExpandedBytes - expandedBytes)
                        }
                        expandedBytes += copied
                        extracted[name] = target
                    }
                    zip.closeEntry()
                }
            }

            val manifest = JSONObject(String(manifestBytes ?: error("The app package manifest is missing."), Charsets.UTF_8))
            require(manifest.optString("format") == "nearer-appshare" && manifest.optInt("formatVersion") == 1) {
                "This NeaRer app package version is not supported."
            }
            val packageName = manifest.optString("packageName")
            val label = manifest.optString("label").ifBlank { packageName }
            val versionName = manifest.optString("versionName").takeUnless { it == "null" }
            val versionCode = manifest.optLong("versionCode", -1L)
            require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) && versionCode >= 0L) {
                "The app package identity is invalid."
            }
            val records = manifest.optJSONArray("apkFiles") ?: error("The APK list is missing.")
            require(records.length() in 1..MAX_APK_COUNT) { "The app package has an invalid number of APK components." }
            val expected = linkedMapOf<String, Pair<Long, String>>()
            for (i in 0 until records.length()) {
                val record = records.getJSONObject(i)
                val name = record.optString("name")
                val size = record.optLong("size", -1L)
                val hash = record.optString("sha256")
                require(size >= 0L && hash.matches(Regex("[a-fA-F0-9]{64}"))) { "An APK integrity record is invalid." }
                require(expected.put(name, size to hash) == null) { "The APK list contains duplicate entries." }
            }
            require(expected.keys == extracted.keys && expected.keys.firstOrNull() == "base.apk") {
                "The app package is missing its base APK or contains unexpected components."
            }

            val apkFiles = expected.map { (name, integrity) ->
                val file = extracted[name] ?: error("An APK component is missing.")
                require(file.length() == integrity.first && sha256(file).equals(integrity.second, ignoreCase = true)) {
                    "An APK component failed its integrity check."
                }
                file
            }
            verifyPackageIdentity(packageName, versionCode, apkFiles)
            return AppShareArchiveInfo(
                packageName = packageName,
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                apkFiles = apkFiles,
                totalBytes = apkFiles.sumOf { it.length() }
            )
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }

    private fun readCurrentEntryLimited(input: ZipInputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "The app package manifest is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun copyCurrentEntryBounded(input: ZipInputStream, output: FileOutputStream, remainingLimit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            require(copied <= remainingLimit) { "The app package expands beyond a safe size." }
            output.write(buffer, 0, count)
        }
        return copied
    }

    private fun verifyPackageIdentity(expectedPackage: String, expectedVersion: Long, files: List<File>) {
        var expectedSigners: Set<String>? = null
        files.forEach { apk ->
            val info = archivePackageInfo(apk) ?: error("Android could not read one of the APK files.")
            require(info.packageName == expectedPackage && packageVersionCode(info) == expectedVersion) {
                "The APK components do not belong to the same app version."
            }
            val signers = signingCertificates(info)
            require(signers.isNotEmpty()) { "The APK signing identity could not be verified." }
            if (expectedSigners == null) expectedSigners = signers
            else require(expectedSigners == signers) { "The APK components have different signing certificates." }
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun signingCertificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else info.signatures ?: return emptySet()
        return signatures.map { digest(it.toByteArray()) }.toSet()
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
