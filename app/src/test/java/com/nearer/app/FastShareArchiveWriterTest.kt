package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.util.zip.ZipInputStream

class FastShareArchiveWriterTest {
    @Test fun streamsOriginalBytesIntoOneArchiveAndMakesNamesUnique() {
        val sources = listOf(
            "../photo.jpg" to "photo bytes".toByteArray(),
            "folder\\photo.jpg" to "second photo".toByteArray(),
            "notes.txt" to "hello".toByteArray()
        )
        val items = sources.mapIndexed { index, (name, bytes) ->
            FastShareArchiveItem(index, name, bytes.size.toLong())
        }
        val output = ByteArrayOutputStream()
        var progress = 0L

        val copied = FastShareArchiveWriter.write(
            items = items,
            openInput = { item -> ByteArrayInputStream(sources[item.sourceIndex].second) },
            output = output,
            onBytesCopied = { progress = it }
        )

        assertEquals(sources.sumOf { it.second.size }.toLong(), copied)
        assertEquals(copied, progress)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(listOf("photo.jpg", "photo (2).jpg", "notes.txt"), entries.keys.toList())
        assertEquals("photo bytes", String(entries.getValue("photo.jpg")))
        assertEquals("second photo", String(entries.getValue("photo (2).jpg")))
        assertEquals("hello", String(entries.getValue("notes.txt")))
    }

    @Test fun unknownLengthsAreSupportedAndEmptyFilesArePreserved() {
        val items = listOf(
            FastShareArchiveItem(0, "unknown.bin", -1L),
            FastShareArchiveItem(1, "empty.txt", 0L)
        )
        val output = ByteArrayOutputStream()
        val copied = FastShareArchiveWriter.write(
            items,
            { item -> ByteArrayInputStream(if (item.sourceIndex == 0) byteArrayOf(1, 2, 3) else byteArrayOf()) },
            output
        )
        assertEquals(3L, copied)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names.add(entry.name)
            }
        }
        assertEquals(listOf("unknown.bin", "empty.txt"), names)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsSourceThatDoesNotMatchDeclaredSize() {
        FastShareArchiveWriter.write(
            listOf(FastShareArchiveItem(0, "photo.jpg", 10L)),
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            ByteArrayOutputStream()
        )
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsSourceThatGrowsBeyondDeclaredSize() {
        FastShareArchiveWriter.write(
            listOf(FastShareArchiveItem(0, "growing.bin", 1L)),
            { ByteArrayInputStream(byteArrayOf(1, 2)) },
            ByteArrayOutputStream()
        )
    }
    @Test(expected = InterruptedIOException::class)
    fun stopsWhenTransferIsCancelled() {
        FastShareArchiveWriter.write(
            listOf(FastShareArchiveItem(0, "large.bin", -1L)),
            { ByteArrayInputStream(ByteArray(100)) },
            ByteArrayOutputStream(),
            shouldContinue = { false }
        )
    }

    @Test fun archiveNamesMakeWindowsReservedDeviceNamesSafe() {
        assertEquals(
            listOf("_CON.txt", "_LPT1.pdf", "_AUX"),
            FastShareArchiveNames.uniqueEntryNames(listOf("CON.txt", "LPT1.pdf", "AUX"))
        )
    }

    @Test fun archiveNamesStripPathsAndWindowsReservedCharacters() {
        val names = FastShareArchiveNames.uniqueEntryNames(
            listOf("../folder/name?.txt", "name?.txt", "", "日本語.mp4")
        )
        assertEquals(listOf("name_.txt", "name_ (2).txt", "shared-file", "日本語.mp4"), names)
        assertTrue(names.none { it.startsWith("/") || it.contains("../") || it.contains('\\') })
    }
}
