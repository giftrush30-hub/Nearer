package com.nearer.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast
import java.io.File
import java.io.FileInputStream

object AppShareInstaller {
    const val ACTION_INSTALL_STATUS = "com.nearer.app.APPSHARE_INSTALL_STATUS"
    const val ACTION_INSTALL_RESULT = "com.nearer.app.APPSHARE_INSTALL_RESULT"
    private const val EXTRA_PACKAGE = "package_name"
    private const val EXTRA_LABEL = "app_label"

    fun canRequestInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /**
     * Stages every APK component into one PackageInstaller session. Android validates
     * package identity/signing again and presents its own confirmation UI when required.
     */
    fun stageAndCommit(context: Context, app: AppShareArchiveInfo): Int {
        require(app.apkFiles.isNotEmpty()) { "The app package contains no APK files." }
        check(canRequestInstall(context)) {
            "Allow NeaRer to install apps from this source in Android settings, then try again."
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                app.apkFiles.forEachIndexed { index, file ->
                    require(file.isFile && file.canRead()) { "An APK component is no longer available." }
                    val name = if (index == 0) "base.apk" else "split_$index.apk"
                    session.openWrite(name, 0L, file.length()).use { output ->
                        FileInputStream(file).use { input -> input.copyTo(output, 64 * 1024) }
                        session.fsync(output)
                    }
                }
                val callbackIntent = Intent(context, AppShareInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(EXTRA_PACKAGE, app.packageName)
                    .putExtra(EXTRA_LABEL, app.label)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            return sessionId
        } catch (error: Exception) {
            try { installer.abandonSession(sessionId) } catch (_: Exception) { }
            throw error
        }
    }

    internal fun statusIntent(context: Context, packageName: String, label: String, status: Int, message: String?): Intent =
        Intent(ACTION_INSTALL_RESULT).setPackage(context.packageName)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message)

    internal fun statusText(status: Int, label: String, message: String?): String = when (status) {
        PackageInstaller.STATUS_SUCCESS -> "$label installed successfully."
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation of $label was cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked installation of $label."
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "$label conflicts with an app already installed."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "$label is not compatible with this phone."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The APK package for $label is invalid."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough storage to install $label."
        else -> message?.takeIf { it.isNotBlank() } ?: "Android could not complete installation of $label."
    }
}

class AppShareInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package_name").orEmpty()
        val label = intent.getStringExtra("app_label").ifNullOrBlank { packageName }
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmation)
            }
            return
        }

        val text = AppShareInstaller.statusText(status, label, message)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        context.sendBroadcast(
            AppShareInstaller.statusIntent(context, packageName, label, status, message)
        )
    }

    private fun String?.ifNullOrBlank(fallback: () -> String): String =
        this?.takeIf { it.isNotBlank() } ?: fallback()
}
