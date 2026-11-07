package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsEnvelopeTest {
    @Test
    fun roundTripsFreeFormTextIncludingUnicodeAndSeparators() {
        val original = SmsEnvelope("msg-123", "Ada | NeaRer", "Meet me at 7:30 👋🏽\nBring the blue folder | please.")
        assertEquals(original, SmsEnvelope.decode(SmsEnvelope.encode(original)))
    }

    @Test
    fun rejectsMessagesThatAreNotNeaRerEnvelopes() {
        assertNull(SmsEnvelope.decode("ordinary SMS from a person"))
        assertNull(SmsEnvelope.decode("NR2|id|sender|body"))
        assertNull(SmsEnvelope.decode("NR1||sender|body"))
        assertNull(SmsEnvelope.decode("NR1|id|%%%|%%%"))
    }

    @Test
    fun preservesLongFreeFormMessages() {
        val body = buildString {
            repeat(80) { append("NeaRer lets us send real messages without limiting the text. ") }
        }
        val original = SmsEnvelope("long-message", "Gift", body)
        assertEquals(body, SmsEnvelope.decode(SmsEnvelope.encode(original))?.body)
    }
}
