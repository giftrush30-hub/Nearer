package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HyperTransferTuningTest {
    @Test
    fun capableCoolPhoneUsesLargerCopyBuffer() {
        assertEquals(HyperTransferTuning.BUFFER_FAST, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 2L * 1024L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = false, thermalStatus = 0
        ))
    }

    @Test
    fun capablePhoneUsesOneMiBProductionReceiveBuffer() {
        assertEquals(1024 * 1024, HyperTransferTuning.BUFFER_FAST)
        assertEquals(HyperTransferTuning.BUFFER_FAST, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 2L * 1024L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = false, thermalStatus = 0
        ))
    }

    @Test
    fun lowRamPhoneUsesBalancedBuffer() {
        assertEquals(HyperTransferTuning.BUFFER_BALANCED, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 512L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = true, thermalStatus = 0
        ))
    }

    @Test
    fun lowAvailableMemoryUsesBalancedBuffer() {
        assertEquals(HyperTransferTuning.BUFFER_BALANCED, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 128L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = false, thermalStatus = 0
        ))
    }

    @Test
    fun androidLowMemorySignalUsesBalancedBufferEvenAboveThreshold() {
        assertEquals(HyperTransferTuning.BUFFER_BALANCED, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 512L * 1024L * 1024L,
            lowMemory = true, lowRamDevice = false, thermalStatus = 0
        ))
    }

    @Test
    fun moderateThermalStatusUsesBalancedBuffer() {
        assertEquals(HyperTransferTuning.BUFFER_BALANCED, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 2L * 1024L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = false, thermalStatus = 2
        ))
    }

    @Test
    fun severeThermalStatusUsesSmallBuffer() {
        assertEquals(HyperTransferTuning.BUFFER_SMALL, HyperTransferTuning.chooseReceiveBufferBytes(
            availableMemoryBytes = 2L * 1024L * 1024L * 1024L,
            lowMemory = false, lowRamDevice = false, thermalStatus = 3
        ))
    }
    @Test
    fun fastCoolPhoneUsesOneMiBStreamAndEightMiBSocketWindows() {
        assertEquals(HyperTransferTuning.STREAM_BUFFER_FAST,
            HyperTransferTuning.chooseTransferBufferBytes(2L * 1024 * 1024 * 1024, false, false, 0))
        assertEquals(HyperTransferTuning.SOCKET_BUFFER_FAST,
            HyperTransferTuning.chooseSocketBufferBytes(2L * 1024 * 1024 * 1024, false, false, 0))
    }

    @Test
    fun lowMemoryPhoneUsesBoundedBalancedTransferAndSocketWindows() {
        assertEquals(HyperTransferTuning.STREAM_BUFFER_BALANCED,
            HyperTransferTuning.chooseTransferBufferBytes(128L * 1024 * 1024, false, false, 0))
        assertEquals(HyperTransferTuning.SOCKET_BUFFER_BALANCED,
            HyperTransferTuning.chooseSocketBufferBytes(128L * 1024 * 1024, false, false, 0))
    }

    @Test
    fun severeThermalStateBacksOffBothPipelineAndSocketBuffers() {
        assertEquals(HyperTransferTuning.STREAM_BUFFER_SMALL,
            HyperTransferTuning.chooseTransferBufferBytes(2L * 1024 * 1024 * 1024, false, false, 3))
        assertEquals(HyperTransferTuning.SOCKET_BUFFER_SMALL,
            HyperTransferTuning.chooseSocketBufferBytes(2L * 1024 * 1024 * 1024, false, false, 3))
    }

}
