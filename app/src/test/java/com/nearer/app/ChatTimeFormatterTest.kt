package com.nearer.app

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimeFormatterTest {
    @Test
    fun formatsTimestampUsingLocalTimeAndTwentyFourHourClock() {
        val previousZone = TimeZone.getDefault()
        val previousLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            Locale.setDefault(Locale.US)
            assertEquals("13:05", formatChatTime(1_699_967_100_000L))
        } finally {
            TimeZone.setDefault(previousZone)
            Locale.setDefault(previousLocale)
        }
    }
}
