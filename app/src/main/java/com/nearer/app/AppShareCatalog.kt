package com.nearer.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Read-only catalogue and safe APK-set preparation for NeaRer AppShare.
 *
 * This class never installs an application and never copies application-private data.
 * A returned package set contains the installed base APK and every installed split APK.
 */
data class ShareableApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val packageFiles: List<File>,
    val totalBytes: Long
)

data class PreparedAppPackage(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val files: List<File>,
    val totalBytes: Long
)

class AppShareCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    /**
     * Lists packages visible to NeaRer. QUERY_ALL_PACKAGES is required for a complete
     * installed-app catalogue on modern Android and is subject to Google Play policy.
     */
    fun listApps(includeSystemApps: Boolean = false): List<ShareableApp> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { app -> includeSystemApps || !isUnmodifiedSystemApp(app) }
            .mapNotNull(::toShareableApp)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun isUnmodifiedSystemApp(app: ApplicationInfo): Boolean {
        val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return system && !updatedSystem
    }

    private fun toShareableApp(app: ApplicationInfo): ShareableApp? {
        val base = app.sourceDir?.let(::File) ?: return null
        if (!base.isFile || !base.canRead()) return null

        val splitPaths = app.splitSourceDirs.orEmpty()
        val files = (listOf(base) + splitPaths.map(::File))
            .distinctBy { it.absolutePath }
            .filter { it.isFile && it.canRead() }
        if (files.isEmpty() || files.firstOrNull() != base) return null

        val packageInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(app.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }

        return ShareableApp(
            packageName = app.packageName,
            label = app.loadLabel(packageManager).toString().ifBlank { app.packageName },
            versionName = packageInfo.versionName,
            versionCode = packageVersionCode(packageInfo),
            isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            packageFiles = files,
            totalBytes = files.sumOf { it.length() }
        )
    }

    private fun packageVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    /**
     * Copies APKs into NeaRer's cache so the existing file-transfer engine can send them.
     * Split APKs remain a set; callers must send all returned files together and preserve
     * their order. The first file is always base.apk.
     */
    fun preparePackage(app: ShareableApp): PreparedAppPackage {
        require(app.packageFiles.isNotEmpty()) { "This app has no readable installation package." }
        val destination = File(appContext.cacheDir, "appshare/${UUID.randomUUID()}").apply {
            if (!mkdirs()) throw IllegalStateException("Could not prepare the app package.")
        }

        val copied = mutableListOf<File>()
        try {
            app.packageFiles.forEachIndexed { index, source ->
                require(source.isFile && source.canRead()) { "An APK component is no longer available." }
                val targetName = if (index == 0) "base.apk" else "split_${index}.apk"
                val target = File(destination, targetName)
                FileInputStream(source).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
                }
                require(target.length() == source.length()) { "APK preparation was incomplete." }
                copied += target
            }
            return PreparedAppPackage(
                packageName = app.packageName,
                label = app.label,
                versionName = app.versionName,
                versionCode = app.versionCode,
                files = copied,
                totalBytes = copied.sumOf { it.length() }
            )
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }

    fun clearPreparedPackages() {
        File(appContext.cacheDir, "appshare").deleteRecursively()
    }
}
