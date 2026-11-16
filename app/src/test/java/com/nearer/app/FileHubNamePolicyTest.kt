package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileHubNamePolicyTest {
    @Test fun rejectsEmptyTraversalAndPathSeparatorNames() {
        assertNull(FileHubNamePolicy.normalize(""))
        assertNull(FileHubNamePolicy.normalize("   "))
        assertNull(FileHubNamePolicy.normalize("."))
        assertNull(FileHubNamePolicy.normalize(".."))
        assertNull(FileHubNamePolicy.normalize("../secret.txt"))
        assertNull(FileHubNamePolicy.normalize("folder\\file.txt"))
        assertNull(FileHubNamePolicy.normalize("bad\u0000name"))
    }

    @Test fun boundsNamesWithoutChangingValidUnicode() {
        assertNull(FileHubNamePolicy.normalize("x".repeat(FileHubNamePolicy.MAX_NAME_LENGTH + 1)))
        assertEquals("Résumé.pdf", FileHubNamePolicy.normalize("  Résumé.pdf  "))
    }

    @Test fun generatesCaseInsensitiveCollisionFreeNamesAndPreservesExtension() {
        assertEquals(
            "Film (2).mp4",
            FileHubNamePolicy.uniqueCopyName("Film.mp4", listOf("Film.mp4", "film (1).MP4"))
        )
        assertEquals(
            "notes (1)",
            FileHubNamePolicy.uniqueCopyName("notes", listOf("notes"))
        )
    }

    @Test fun copyNameAlwaysStaysWithinLengthBound() {
        val name = FileHubNamePolicy.uniqueCopyName(
            "a".repeat(150) + ".mp4",
            listOf("a".repeat(114) + ".mp4")
        )
        assertTrue(name.length <= FileHubNamePolicy.MAX_NAME_LENGTH)
    }
}
