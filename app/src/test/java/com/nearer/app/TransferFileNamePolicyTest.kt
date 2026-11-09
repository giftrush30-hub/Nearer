package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferFileNamePolicyTest {
    @Test fun stripsUnixAndWindowsPathComponents() {
        assertEquals("holiday.jpg", TransferFileNamePolicy.safeName("../../private/holiday.jpg"))
        assertEquals("report.pdf", TransferFileNamePolicy.safeName("C:\\Users\\someone\\report.pdf"))
    }

    @Test fun replacesControlAndHeaderInjectionCharacters() {
        val safe = TransferFileNamePolicy.safeName("video\r\nX-Evil: yes.mp4")
        assertEquals("video__X-Evil_ yes.mp4", safe)
        assertTrue(safe.none { it == '\r' || it == '\n' || it == '\u0000' })
    }

    @Test fun fallsBackForBlankOrDotPathNames() {
        assertEquals("shared-file", TransferFileNamePolicy.safeName(""))
        assertEquals("shared-file", TransferFileNamePolicy.safeName("../"))
        assertEquals("shared-file", TransferFileNamePolicy.safeName(".."))
    }

    @Test fun limitsNameLength() {
        assertEquals(100, TransferFileNamePolicy.safeName("a".repeat(300) + ".mp4").length)
    }

    @Test fun preservesOrdinaryNamesAndExtensions() {
        assertEquals("My holiday video 01.mp4", TransferFileNamePolicy.safeName("My holiday video 01.mp4"))
    }
}
