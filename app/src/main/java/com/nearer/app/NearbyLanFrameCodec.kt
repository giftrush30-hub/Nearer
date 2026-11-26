package com.nearer.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/** Byte-compatible with iOS TransportFrameCodec: uint32 big-endian length + payload. */
internal object NearbyLanFrameCodec {
    const val MAX_FRAME_BYTES = 1_048_576

    fun validate(payload: ByteArray) {
        require(payload.isNotEmpty()) { "Empty LAN frames are not allowed" }
        require(payload.size <= MAX_FRAME_BYTES) { "LAN frame exceeds 1 MiB" }
    }

    fun encode(payload: ByteArray): ByteArray {
        validate(payload)
        val result = ByteArray(payload.size + 4)
        val size = payload.size
        result[0] = (size ushr 24).toByte()
        result[1] = (size ushr 16).toByte()
        result[2] = (size ushr 8).toByte()
        result[3] = size.toByte()
        payload.copyInto(result, destinationOffset = 4)
        return result
    }

    /** Reads exactly one bounded frame. EOF before the header is a normal disconnect. */
    fun read(input: DataInputStream): ByteArray {
        val length = try {
            input.readInt()
        } catch (e: EOFException) {
            throw e
        }
        if (length !in 1..MAX_FRAME_BYTES) throw IOException("Invalid LAN frame length: $length")
        return ByteArray(length).also(input::readFully)
    }

    fun write(output: DataOutputStream, payload: ByteArray) {
        validate(payload)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }
}
