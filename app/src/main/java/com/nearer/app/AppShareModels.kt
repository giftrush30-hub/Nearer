package com.nearer.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ShareableApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val packageFiles: List<File>
)

data class AppShareArchiveInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val apkFiles: List<File>
)

class AppShareCatalog(private val context: Context) {
    fun listApps(): List<ShareableApp> {
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        return installed.asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { app ->
                try {
                    val info = packageInfo(pm, app.packageName)
                    val files = buildList {
                        app.sourceDir?.let { add(File(it)) }
                        app.splitSourceDirs?.forEach { add(File(it)) }
                    }.filter { it.isFile && it.canRead() }
                    if (files.isEmpty()) return@mapNotNull null
                    ShareableApp(
                        label = pm.getApplicationLabel(app).toString().ifBlank { app.packageName },
                        packageName = app.packageName,
                        versionName = info.versionName ?: "Unknown version",
                        packageFiles = files
                    )
                } catch (_: Exception) { null }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun preparePackage(app: ShareableApp): ShareableApp {
        require(app.packageFiles.isNotEmpty()) { "This app has no readable APK files." }
        require(app.packageFiles.all { it.isFile && it.canRead() }) { "An APK file is no longer available." }
        return app
    }

    private fun packageInfo(pm: PackageManager, name: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, 0)
        }
}

class AppShareArchive(private val context: Context) {
    fun create(app: ShareableApp): File {
        require(app.packageFiles.isNotEmpty()) { "No APK files were found." }
        val directory = File(context.cacheDir, "appshare/outgoing").apply { mkdirs() }
        val archive = File(directory, "${app.packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${System.currentTimeMillis()}.nearerapp")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            val metadata = "label=${app.label.replace("\n", " ")}\npackage=${app.packageName}\nversion=${app.versionName}\ncount=${app.packageFiles.size}\n"
            zip.putNextEntry(ZipEntry("nearer-app.properties"))
            zip.write(metadata.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            app.packageFiles.forEachIndexed { index, source ->
                zip.putNextEntry(ZipEntry("apk/${index}.apk"))
                FileInputStream(source).use { input -> input.copyTo(zip, 64 * 1024) }
                zip.closeEntry()
            }
        }
        return archive
    }

    fun validateAndExtract(archive: File): AppShareArchiveInfo {
        require(archive.isFile && archive.length() in 1..(1024L * 1024L * 1024L)) { "The app package is missing or too large." }
        val target = File(context.cacheDir, "appshare/incoming/${System.currentTimeMillis()}").apply { mkdirs() }
        var label = "Shared Android app"
        var packageName = ""
        var version = "Unknown"
        val apkFiles = mutableListOf<File>()
        ZipInputStream(FileInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(!entry.isDirectory && !entry.name.startsWith("/") && !entry.name.contains("..")) { "Unsafe package entry." }
                when {
                    entry.name == "nearer-app.properties" -> {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        text.lineSequence().forEach { line ->
                            val key = line.substringBefore("=", "")
                            val value = line.substringAfter("=", "")
                            when (key) {
                                "label" -> label = value.take(100)
                                "package" -> packageName = value.take(255)
                                "version" -> version = value.take(80)
                            }
                        }
                    }
                    entry.name.matches(Regex("apk/[0-9]+\\.apk")) -> {
                        val out = File(target, entry.name.substringAfterLast('/'))
                        FileOutputStream(out).use { output -> zip.copyTo(output, 64 * 1024) }
                        require(out.length() > 0) { "An APK in this package is empty." }
                        apkFiles.add(out)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "The app package identifier is invalid." }
        require(apkFiles.isNotEmpty() && apkFiles.size <= 20) { "No valid APK files were found." }
        val pm = context.packageManager
        apkFiles.forEach { apk ->
            val parsed = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: throw IllegalArgumentException("One of the APK files is not a valid Android package.")
            require(parsed.packageName == packageName) { "The APK package name does not match the transfer details." }
        }
        return AppShareArchiveInfo(label, packageName, version, apkFiles)
    }
}
