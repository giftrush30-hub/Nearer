package com.nearer.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Stages every APK from a NeaRer split-app archive in one Android install session.
 * Android still owns signature checks and the final user approval screen.
 */
object SplitApkInstaller {
    fun install(context: Context, archive: File) {
        require(archive.isFile) { "The received app package could not be found." }
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            throw SecurityException("Allow NeaRer to install apps from this source in Android settings, then tap the app package again.")
        }

        val installer = packageManager.packageInstaller
        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // NeaRer is a peer-to-peer installer, not a privileged device owner.
        // Always make Android's user approval step explicit.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(sessionParams)
        try {
            installer.openSession(sessionId).use { session ->
                var hasBaseApk = false
                var apkCount = 0
                val names = HashSet<String>()
                ZipInputStream(FileInputStream(archive)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                            require('/' !in entry.name && '\\' !in entry.name && entry.name.isNotBlank()) {
                                "The app package contains an unsafe APK entry name."
                            }
                            require(names.add(entry.name)) { "The app package contains duplicate APK parts." }
                            if (entry.name.equals("base.apk", ignoreCase = true)) hasBaseApk = true
                            val output = session.openWrite(entry.name, 0L, -1L)
                            try {
                                zip.copyTo(output, 64 * 1024)
                                session.fsync(output)
                            } finally {
                                output.close()
                            }
                            apkCount += 1
                        }
                        zip.closeEntry()
                    }
                }
                require(hasBaseApk && apkCount >= 2) {
                    "This split-app package is incomplete. It must contain base.apk and its split APK parts."
                }

                val callbackIntent = Intent(context, PackageInstallStatusReceiver::class.java)
                    .setAction(PackageInstallStatusReceiver.ACTION_INSTALL_STATUS)
                    .putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
                session.commit(callback.intentSender)
            }
        } catch (error: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) { }
            throw error
        }
    }
}

class PackageInstallStatusReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmation != null) context.startActivity(confirmation)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "NeaRer app installation completed.", Toast.LENGTH_LONG).show()
            }
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(140).orEmpty()
                Toast.makeText(
                    context,
                    if (reason.isBlank()) "Android could not install this app package." else "Install failed: $reason",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.nearer.app.PACKAGE_INSTALL_STATUS"
    }
}
