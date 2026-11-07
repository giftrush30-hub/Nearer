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
}
