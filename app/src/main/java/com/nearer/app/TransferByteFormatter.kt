package com.nearer.app

import java.util.Locale

/**
 * Formats byte counts consistently across transfer and file-history surfaces.
 * Uses binary 1024-based units to match the existing NeaRer UI convention.
 */
internal fun formatTransferBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
