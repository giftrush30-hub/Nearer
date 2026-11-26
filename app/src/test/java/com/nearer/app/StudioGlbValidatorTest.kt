package com.nearer.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioGlbValidatorTest {
    private fun glb(json: String, binary: ByteArray? = null): ByteArray {
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val paddedJsonLength = (jsonBytes.size + 3) / 4 * 4
        val binaryLength = binary?.size ?: 0
        val total = 12 + 8 + paddedJsonLength + if (binary != null) 8 + binaryLength else 0
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46546C67).putInt(2).putInt(total)
        buffer.putInt(paddedJsonLength).putInt(0x4E4F534A)
        buffer.put(jsonBytes)
        repeat(paddedJsonLength - jsonBytes.size) { buffer.put(0x20) }
        if (binary != null) buffer.putInt(binaryLength).putInt(0x004E4942).put(binary)
        return buffer.array()
    }

    @Test fun acceptsValidGlb2WithAndWithoutBinaryChunk() {
        val json = """{"asset":{"version":"2.0"}}"""
        assertTrue(StudioGlbValidator.validate(glb(json)).isValid)
        assertTrue(StudioGlbValidator.validate(glb(json, byteArrayOf(1, 2, 3, 4))).hasBinaryChunk)
    }

    @Test fun rejectsInvalidMagicVersionAndDeclaredLength() {
        val valid = glb("""{"asset":{"version":"2.0"}}""")
        val badMagic = valid.clone().also { it[0] = 0 }
        assertFalse(StudioGlbValidator.validate(badMagic).isValid)
        val badVersion = valid.clone().also { it[4] = 1 }
        assertFalse(StudioGlbValidator.validate(badVersion).isValid)
        val badLength = valid.clone().also { it[8] = (it[8].toInt() - 1).toByte() }
        assertFalse(StudioGlbValidator.validate(badLength).isValid)
    }

    @Test fun rejectsMissingOrInvalidGltfAssetVersion() {
        assertFalse(StudioGlbValidator.validate(glb("""{"asset":{"version":"1.0"}}""")).isValid)
        assertFalse(StudioGlbValidator.validate(glb("""{"scene":0}""")).isValid)
    }

    @Test fun rejectsTruncatedChunkAndShortHeader() {
        assertFalse(StudioGlbValidator.validate(byteArrayOf(1, 2, 3)).isValid)
        val valid = glb("""{"asset":{"version":"2.0"}}""")
        val truncated = valid.copyOf(valid.size - 4)
        // Keep the header's declared size accurate so validation reaches the chunk boundary.
        ByteBuffer.wrap(truncated).order(ByteOrder.LITTLE_ENDIAN).putInt(8, truncated.size)
        assertFalse(StudioGlbValidator.validate(truncated).isValid)
    }
}
