package com.nearer.app

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * HTTP-date validators for resumable local downloads.
 *
 * A Range request with If-Range is honoured only when the receiver's validator
 * exactly identifies the current source modification second. If metadata is
 * missing or malformed, the safe behaviour is to send the complete file.
 */
internal object FastShareResumePolicy {
    private const val HTTP_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"

    fun formatHttpDate(lastModifiedMillis: Long?): String? {
        if (lastModifiedMillis == null || lastModifiedMillis < 0L) return null
        return formatter().format(Date(lastModifiedMillis))
    }

    fun shouldHonorRange(
        rangeHeader: String?,
        ifRangeHeader: String?,
        lastModifiedMillis: Long?
    ): Boolean {
        if (rangeHeader.isNullOrBlank()) return false
        if (ifRangeHeader.isNullOrBlank()) return true
        if (lastModifiedMillis == null || lastModifiedMillis < 0L) return false

        val parsed = parseHttpDate(ifRangeHeader) ?: return false
        return parsed / 1000L == lastModifiedMillis / 1000L
    }

    private fun parseHttpDate(value: String): Long? {
        val parser = formatter()
        val position = ParsePosition(0)
        val parsed = parser.parse(value, position) ?: return null
        if (position.index != value.length) return null
        return parsed.time
    }

    private fun formatter(): SimpleDateFormat =
        SimpleDateFormat(HTTP_DATE_PATTERN, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("GMT")
        }
}
