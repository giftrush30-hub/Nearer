package com.nearer.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats local chat and transfer-history timestamps using the existing 24-hour UI convention.
 */
internal fun formatChatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
