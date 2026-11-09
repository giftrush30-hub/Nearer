package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FastShareFileNameTest {
    @Test fun decodesNormalBrowserFilename() {
        assertEquals("holiday photo.jpg", FastShareFileName.sanitize("holiday%20photo.jpg"))
    }

    @Test fun stripsPathSeparatorsAndTraversalCharacters() {
        val name = FastShareFileName.sanitize("%2E%2E%2F..%5Csecret.txt")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name == "..")
        assertEquals("_.._secret.txt", name)
    }

    @Test fun replacesControlAndUnsupportedCharacters() {
        assertEquals("my_file_.txt", FastShareFileName.sanitize("my:file?.txt"))
    }

    @Test fun boundsLengthAndProvidesFallback() {
        assertEquals(180, FastShareFileName.sanitize("a".repeat(250)).length)
        assertEquals("Received file", FastShareFileName.sanitize("..."))
    }
}
