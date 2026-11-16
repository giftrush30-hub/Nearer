package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StudioPitchEngineTest {
    private fun sine(frequency: Float, sampleRate: Int = 22_050, size: Int = 2_048): FloatArray =
        FloatArray(size) { index -> (0.35 * sin(2.0 * PI * frequency * index / sampleRate)).toFloat() }

    @Test
    fun detectsConcertAWithinReasonableCents() {
        val reading = StudioPitchEngine.detect(sine(440f), 22_050)
        assertNotNull(reading)
        assertEquals(440f, reading!!.frequencyHz, 3f)
        assertEquals(69, reading.nearestMidiNote)
        assertEquals("A4", StudioPitchEngine.noteName(reading.nearestMidiNote))
        assertTrue(reading.confidence > 0.75f)
    }

    @Test
    fun detectsLowerMaleVocalRange() {
        val reading = StudioPitchEngine.detect(sine(110f), 22_050)
        assertNotNull(reading)
        assertEquals(110f, reading!!.frequencyHz, 2f)
        assertEquals(45, reading.nearestMidiNote)
    }

    @Test
    fun rejectsSilenceAndInvalidBuffers() {
        assertNull(StudioPitchEngine.detect(FloatArray(2_048), 22_050))
        assertNull(StudioPitchEngine.detect(FloatArray(100) { 0.2f }, 22_050))
        assertNull(StudioPitchEngine.detect(sine(440f), 0))
    }

    @Test
    fun mapsChromaticPitchToNearestNoteInSelectedScale() {
        val chromatic = StudioPitchEngine.detect(sine(466.0f), 22_050)
        assertNotNull(chromatic)
        val target = StudioPitchEngine.scaleTarget(chromatic!!, rootSemitone = 0, isMinor = false)
        assertEquals("A4", target.noteName)
        assertTrue(target.correctionCents < 0)
    }

    @Test
    fun supportsMinorScaleTargets() {
        val reading = StudioPitchEngine.detect(sine(440f), 22_050)
        assertNotNull(reading)
        val target = StudioPitchEngine.scaleTarget(reading!!, rootSemitone = 9, isMinor = true)
        assertEquals("A4", target.noteName)
        assertTrue(kotlin.math.abs(target.correctionCents) <= 2)
    }
}
