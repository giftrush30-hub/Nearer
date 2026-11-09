package com.nearer.app

import org.junit.Assert.*
import org.junit.Test

class FastShareAccessKeyTest {
    @Test fun createsUnique256BitHexadecimalKeys() {
        val first = FastShareAccessKey.create()
        val second = FastShareAccessKey.create()
        assertEquals(64, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(first, second)
    }
    @Test fun acceptsOnlyTheExactSessionKey() {
        val key = "a".repeat(64)
        assertTrue(FastShareAccessKey.matches(key, key))
        assertFalse(FastShareAccessKey.matches(key, null))
        assertFalse(FastShareAccessKey.matches(key, "b".repeat(64)))
        assertFalse(FastShareAccessKey.matches(key, key.dropLast(1)))
        assertFalse(FastShareAccessKey.matches(key, "$key "))
    }
    @Test fun rejectsNonHexadecimalAndCaseChangedKeys() {
        val key = "abcdef0123456789".repeat(4)
        assertTrue(FastShareAccessKey.matches(key, key))
        assertFalse(FastShareAccessKey.matches(key, key.uppercase(java.util.Locale.ROOT)))
        assertFalse(FastShareAccessKey.matches(key, "g" + key.drop(1)))
    }

    @Test fun everyGeneratedKeyHasExactlyThirtyTwoBytesOfEntropyEncoding() {
        repeat(20) {
            val key = FastShareAccessKey.create()
            assertEquals(64, key.length)
            assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        }
    }

}
