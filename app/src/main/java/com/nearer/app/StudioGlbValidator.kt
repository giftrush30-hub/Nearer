package com.nearer.app

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class StudioGlbValidation(
    val isValid: Boolean,
    val jsonChunk: String?,
    val hasBinaryChunk: Boolean,
    val errors: List<String>
)

/** Strict GLB 2.0 container validation before native model loading. */
internal object StudioGlbValidator {
    private const val MAGIC = 0x46546C67
    private const val JSON_CHUNK = 0x4E4F534A
    private const val BIN_CHUNK = 0x004E4942

    fun validate(bytes: ByteArray): StudioGlbValidation {
        if (bytes.size < 20) return invalid("GLB file is too short.")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) return invalid("File is not a binary glTF (GLB).")
        val version = buffer.int
        if (version != 2) return invalid("Only glTF binary version 2 is supported.")
        val declaredLength = buffer.int.toLong() and 0xFFFF_FFFFL
        if (declaredLength != bytes.size.toLong()) return invalid("GLB declared length does not match the file length.")

        var json: String? = null
        var hasBinary = false
        var chunkIndex = 0
        while (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) return invalid("GLB contains a truncated chunk header.")
            val chunkLength = buffer.int.toLong() and 0xFFFF_FFFFL
            val chunkType = buffer.int
            if (chunkLength % 4L != 0L || chunkLength > buffer.remaining().toLong()) {
                return invalid("GLB contains an invalid or truncated chunk.")
            }
            if (chunkLength > Int.MAX_VALUE) return invalid("GLB chunk is too large for this device.")
            val chunk = ByteArray(chunkLength.toInt())
            buffer.get(chunk)
            when (chunkType) {
                JSON_CHUNK -> {
                    if (chunkIndex != 0 || json != null) return invalid("GLB JSON must be the first and only JSON chunk.")
                    json = chunk.toString(Charsets.UTF_8).trim()
                }
                BIN_CHUNK -> {
                    if (hasBinary) return invalid("GLB contains duplicate binary chunks.")
                    hasBinary = true
                }
            }
            chunkIndex++
        }
        val jsonText = json ?: return invalid("GLB is missing its JSON chunk.")
        val assetVersion = runCatching {
            JSONObject(jsonText).optJSONObject("asset")?.optString("version")
        }.getOrNull()
        if (assetVersion != "2.0") return invalid("GLB asset metadata must declare glTF version 2.0.")
        return StudioGlbValidation(true, jsonText, hasBinary, emptyList())
    }

    private fun invalid(message: String) = StudioGlbValidation(false, null, false, listOf(message))
}
