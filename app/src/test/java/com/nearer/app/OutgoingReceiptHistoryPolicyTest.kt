package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OutgoingReceiptHistoryPolicyTest {
    @Test fun appendsUniqueReceiptWithoutReorderingUnrelatedEntries() {
        assertEquals(
            listOf("A", "B", "C"),
            OutgoingReceiptHistoryPolicy.append(listOf("A", "B"), "C")
        )
    }

    @Test fun duplicateReceiptMovesToNewestPosition() {
        assertEquals(
            listOf("B", "A"),
            OutgoingReceiptHistoryPolicy.append(listOf("A", "B"), "A")
        )
    }

    @Test fun historyEvictsOldestWhenBoundIsReached() {
        assertEquals(
            listOf("B", "C"),
            OutgoingReceiptHistoryPolicy.append(listOf("A", "B"), "C", maxIds = 2)
        )
    }

    @Test fun blankIDsAreNotAdded() {
        assertEquals(listOf("A", "B"), OutgoingReceiptHistoryPolicy.append(listOf("A", "B"), " "))
    }
}
