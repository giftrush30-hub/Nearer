package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageIdHistoryPolicyTest {
    @Test fun trimsOldestIdsAndKeepsMostRecent() {
        val ids = linkedSetOf("one", "two", "three", "four")
        MessageIdHistoryPolicy.trimOldest(ids, 2)
        assertEquals(listOf("three", "four"), ids.toList())
    }

    @Test fun preservesInsertionOrderWithinLimit() {
        val ids = linkedSetOf("first", "second")
        MessageIdHistoryPolicy.trimOldest(ids, 2)
        assertEquals(listOf("first", "second"), ids.toList())
    }

    @Test fun zeroLimitClearsAllIds() {
        val ids = linkedSetOf("first", "second")
        MessageIdHistoryPolicy.trimOldest(ids, 0)
        assertEquals(emptyList<String>(), ids.toList())
    }

    @Test fun rejectsNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageIdHistoryPolicy.trimOldest(linkedSetOf("one"), -1)
        }
    }
}
