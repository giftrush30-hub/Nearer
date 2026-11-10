package com.nearer.app

import android.app.Application
import android.util.Log

/**
 * Records fatal startup/runtime exceptions locally so a phone-only tester can
 * share the real failure instead of relying on the generic Android crash toast.
 */
class NeaRerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = Log.getStackTraceString(error).take(16000)
                getSharedPreferences("nearer_diagnostics", MODE_PRIVATE)
                    .edit()
                    .putString("nearer_startup_crash", report)
                    .commit()
            } catch (_: Exception) {
                // Preserve the original crash even if diagnostic storage fails.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
