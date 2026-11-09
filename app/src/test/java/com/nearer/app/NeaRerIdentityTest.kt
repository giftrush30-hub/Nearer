package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class NeaRerIdentityTest {
    @Test fun generatesTwelveCharacterUnambiguousIds() {
        repeat(100) {
            val id = NeaRerIdentity.generate()
            assertEquals(12, id.length)
            assertEquals(id, NeaRerIdentity.normalize(id))
            assertTrue(id.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
        }
    }

    @Test fun generatedIdsAreIndependent() {
        assertNotEquals(NeaRerIdentity.generate(), NeaRerIdentity.generate())
    }

    @Test fun preservesExistingLegacyIdsForConversationContinuity() {
        assertEquals("ABC234", NeaRerIdentity.getOrCreate("abc234"))
        assertEquals("ABC234", NeaRerIdentity.normalize(" ABC234 "))
        assertEquals("0F1A2B", NeaRerIdentity.normalize("0f1a2b"))
    }

    @Test fun rejectsMalformedOrAmbiguousIds() {
        assertNull(NeaRerIdentity.normalize(null))
        assertNull(NeaRerIdentity.normalize(""))
        assertNull(NeaRerIdentity.normalize("ABC123!"))
        assertNull(NeaRerIdentity.normalize("ABC12O"))
        assertNull(NeaRerIdentity.normalize("TOO-LONG-IDENTITY"))
    }

    @Test fun replacesInvalidStoredIds() {
        val generated = NeaRerIdentity.getOrCreate("bad-id", object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                bytes.indices.forEach { bytes[it] = it.toByte() }
            }
        })
        assertEquals(12, generated.length)
        assertEquals(generated, NeaRerIdentity.normalize(generated))
    }
}
