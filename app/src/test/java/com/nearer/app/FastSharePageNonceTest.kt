package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSharePageNonceTest {
    @Test fun createsFreshBase64NonceValuesSuitableForCsp() {
        val first = FastSharePageNonce.create()
        val second = FastSharePageNonce.create()
        assertEquals(24, first.length)
        assertTrue(first.matches(Regex("[A-Za-z0-9+/]{24}")))
        assertNotEquals(first, second)
    }
}
