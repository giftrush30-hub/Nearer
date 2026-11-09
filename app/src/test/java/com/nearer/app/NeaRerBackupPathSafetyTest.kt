package com.nearer.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeaRerBackupPathSafetyTest {
    @Test fun acceptsARegularChildInsideItsStorageRoot() {
        val root = Files.createTempDirectory("nearer-path-root").toFile()
        try {
            val child = File(root, "received/photo.jpg").apply {
                parentFile?.mkdirs()
                writeText("image")
            }
            assertTrue(NeaRerBackupPathSafety.isContained(root, child))
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun directChildCheckRejectsParentItselfAndPrefixSibling() {
        val root = Files.createTempDirectory("nearer-direct-child").toFile()
        try {
            val valid = File(root, "nearer-restore-stage-abc")
            val prefixSibling = File(root.parentFile, root.name + "-elsewhere")
            assertTrue(NeaRerBackupPathSafety.isDirectChild(root, valid))
            assertFalse(NeaRerBackupPathSafety.isDirectChild(root, root))
            assertFalse(NeaRerBackupPathSafety.isDirectChild(root, prefixSibling))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsAChildReachedThroughAnEscapingSymlink() {
        val root = Files.createTempDirectory("nearer-path-root").toFile()
        val outside = Files.createTempDirectory("nearer-path-outside").toFile()
        try {
            val redirected = File(root, "received")
            Files.createSymbolicLink(redirected.toPath(), outside.toPath())
            val externalFile = File(redirected, "private.txt").apply { writeText("not app data") }

            assertFalse(NeaRerBackupPathSafety.isContained(root, redirected))
            assertFalse(NeaRerBackupPathSafety.isContained(redirected, externalFile))
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
