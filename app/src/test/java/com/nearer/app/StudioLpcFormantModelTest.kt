package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class StudioLpcFormantModelTest {
    @Test fun estimatedPredictorsRemainFiniteAndBounded() {
        val signal = FloatArray(2048) { index ->
            (0.55 * sin(2.0 * Math.PI * 180.0 * index / 44_100.0) +
                0.2 * sin(2.0 * Math.PI * 720.0 * index / 44_100.0)).toFloat()
        }
        val model = StudioLpcFormantModel.estimate(signal)
        assertEquals(12, model.order)
        assertTrue(model.predictor.all { it.isFinite() && kotlin.math.abs(it) <= 0.95f })
    }

    @Test fun silenceProducesStableZeroPredictors() {
        val model = StudioLpcFormantModel.estimate(FloatArray(2048))
        assertTrue(model.predictor.all { it == 0f })
    }

    @Test fun whiteningAndRecolouringRoundTripWithSameHistory() {
        val model = StudioLpcFormantModel.estimate(FloatArray(2048) { index ->
            (0.6 * sin(2.0 * Math.PI * 220.0 * index / 44_100.0) +
                0.25 * sin(2.0 * Math.PI * 900.0 * index / 44_100.0)).toFloat()
        })
        val history = FloatArray(model.order) { index -> (index + 1) * 0.025f }
        val inputHistory = history.copyOf()
        val outputHistory = history.copyOf()
        val sample = 0.37f
        val residual = model.whiten(sample, inputHistory)
        val restored = model.restore(residual, outputHistory)
        assertEquals(sample, restored, 0.00001f)
    }

    @Test fun voiceLikePredictorsRemainFiniteForACompositeSpectrum() {
        val voiceLike = FloatArray(2048) { index ->
            (0.22 * sin(2.0 * Math.PI * 140.0 * index / 48_000.0) +
                0.08 * sin(2.0 * Math.PI * 720.0 * index / 48_000.0) +
                0.035 * sin(2.0 * Math.PI * 1_850.0 * index / 48_000.0)).toFloat()
        }
        val model = StudioLpcFormantModel.estimate(voiceLike, 12)
        assertTrue(model.predictor.all { it.isFinite() && kotlin.math.abs(it) <= 0.95f })
    }

    @Test fun historyPushKeepsNewestSampleFirst() {
        val history = floatArrayOf(1f, 2f, 3f)
        pushLpcHistory(history, 9f)
        assertEquals(listOf(9f, 1f, 2f), history.toList())
    }
}
