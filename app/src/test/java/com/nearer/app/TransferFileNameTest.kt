package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferFileNameTest {
    @Test
    fun preservesUnicodeAndSpaces() {
        assertEquals("Ọjà Ìfẹ́ 2026.jpg", TransferFileName.safeLeafName("Ọjà Ìfẹ́ 2026.jpg"))
    }

    @Test
    fun stripsPathComponentsAndKeepsOnlyLeafName() {
        assertEquals("video.mp4", TransferFileName.safeLeafName("../../folder/video.mp4"))
    }

    @Test
    fun replacesWindowsReservedCharacters() {
        assertEquals("my_file_name_.txt", TransferFileName.safeLeafName("my:file?name*.txt"))
    }

    @Test
    fun preventsEmptyAndDotOnlyNames() {
        assertEquals("shared-file", TransferFileName.safeLeafName("../../..."))
    }

    @Test
    fun capsVeryLongNames() {
        assertEquals(120, TransferFileName.safeLeafName("a".repeat(200)).length)
    }
}
