package com.nearer.app

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioDecodedPcmFileSourceTest {
    private fun fixture(): StudioDecodedPcmInfo {
        val file = File.createTempFile("nearer-studio-pcm-test-", ".f32")
        FileOutputStream(file).use { stream ->
            val bytes = ByteBuffer.allocate(5 * 8).order(ByteOrder.LITTLE_ENDIAN)
            repeat(5) { frame ->
                bytes.putFloat(frame / 10f)
                bytes.putFloat(-frame / 10f)
            }
            stream.write(bytes.array())
        }
        return StudioDecodedPcmInfo(file, 48_000, 5L, 44_100, 2)
    }

    @Test fun readsInterleavedStereoFramesAtRequestedSourceOffset() {
        val decoded = fixture()
        StudioDecodedPcmFileSource(decoded).use { source ->
            val output = FloatArray(10) { 7f }
            val count = source.readStereoFrames(2L, 2, output, 2)
            assertEquals(2, count)
            assertEquals(7f, output[0], 0f)
            assertEquals(0.2f, output[2], 0.0001f)
            assertEquals(-0.2f, output[3], 0.0001f)
            assertEquals(0.3f, output[4], 0.0001f)
            assertEquals(-0.3f, output[5], 0.0001f)
        }
        decoded.file.delete()
    }

    @Test fun clampsReadsAtEndOfDecodedFile() {
        val decoded = fixture()
        StudioDecodedPcmFileSource(decoded).use { source ->
            val output = FloatArray(8)
            assertEquals(1, source.readStereoFrames(4L, 4, output, 0))
            assertEquals(0.4f, output[0], 0.0001f)
            assertEquals(-0.4f, output[1], 0.0001f)
            assertEquals(0, source.readStereoFrames(5L, 2, output, 0))
        }
        decoded.file.delete()
    }

    @Test fun rejectsInvalidOffsetsAndOversizedReads() {
        val decoded = fixture()
        StudioDecodedPcmFileSource(decoded).use { source ->
            assertTrue(runCatching { source.readStereoFrames(0L, 4, FloatArray(4), 0) }.isFailure)
            assertTrue(runCatching { source.readStereoFrames(-1L, 1, FloatArray(2), 0) }.isFailure)
            assertTrue(runCatching {
                source.readStereoFrames(0L, StudioPcmBlockMixer.MAX_BLOCK_FRAMES + 1, FloatArray(20_000), 0)
            }.isFailure)
        }
        decoded.file.delete()
    }
}
