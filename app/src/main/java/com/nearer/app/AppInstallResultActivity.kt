package com.nearer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle

/**
 * Receives Android PackageInstaller results and displays the system installation outcome.
 *
 * Kept separate from MainActivity so app-install lifecycle handling does not grow the
 * main application coordinator.
 */
internal class AppInstallResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            if (confirmation != null) startActivity(confirmation)
            finish()
            return
        }
        val success = status == PackageInstaller.STATUS_SUCCESS
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val message = if (success) {
            "Android reports that the app was installed successfully."
        } else {
            "Android did not complete the installation." + if (detail.isBlank()) "" else "\n\n" + detail
        }
        AlertDialog.Builder(this)
            .setTitle(if (success) "App installed" else "Installation not completed")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
