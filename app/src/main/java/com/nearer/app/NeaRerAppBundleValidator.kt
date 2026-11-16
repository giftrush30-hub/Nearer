package com.nearer.app

import java.io.File
import java.util.zip.ZipFile

/**
 * Checks the minimum archive structure required for a NeaRer install bundle.
 *
 * This is intentionally only a structural check. It does not establish that the
 * APK is trusted, signed by an expected key, or safe to install.
 */
internal fun isNeaRerAppBundle(file: File): Boolean {
    if (!file.isFile || !(file.extension.equals("zip", ignoreCase = true) ||
            file.extension.equals("nearerapp", ignoreCase = true))) return false
    return runCatching {
        ZipFile(file).use { archive ->
            archive.getEntry("INSTALLATION.txt") != null && archive.getEntry("base.apk") != null
        }
    }.getOrDefault(false)
}
