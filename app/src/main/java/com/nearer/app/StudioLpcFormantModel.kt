package com.nearer.app

import kotlin.math.abs
import kotlin.math.pow

/**
 * Lightweight LPC spectral-envelope model for monophonic voice formant compensation.
 * Predictor coefficients are kept bounded and can be used to whiten then re-colour a
 * pitch-shifted residual while keeping the original vocal spectral envelope.
 */
internal class StudioLpcFormantModel private constructor(
    val predictor: FloatArray
) {
    val order: Int get() = predictor.size

    fun whiten(sample: Float, history: FloatArray): Float {
        var predicted = 0f
        for (i in predictor.indices) predicted += predictor[i] * history[i]
        return sample - predicted
    }

    fun restore(residual: Float, history: FloatArray): Float {
        var predicted = 0f
        for (i in predictor.indices) predicted += predictor[i] * history[i]
        return residual + predicted
    }

    companion object {
        fun estimate(samples: FloatArray, requestedOrder: Int = 12): StudioLpcFormantModel {
            val order = requestedOrder.coerceIn(4, 20).coerceAtMost((samples.size / 4).coerceAtLeast(1))
            if (samples.size < order * 4) return StudioLpcFormantModel(FloatArray(order))
            val windowed = FloatArray(samples.size)
            for (i in samples.indices) {
                val window = 0.5f - 0.5f * kotlin.math.cos((2.0 * Math.PI * i / (samples.size - 1)).toFloat())
                windowed[i] = samples[i] * window
            }
            val autocorrelation = DoubleArray(order + 1)
            for (lag in 0..order) {
                var sum = 0.0
                for (i in lag until windowed.size) sum += windowed[i].toDouble() * windowed[i - lag]
                autocorrelation[lag] = sum
            }
            if (autocorrelation[0] < 1e-8) return StudioLpcFormantModel(FloatArray(order))

            val coefficients = DoubleArray(order + 1)
            coefficients[0] = 1.0
            var error = autocorrelation[0]
            for (degree in 1..order) {
                var residual = autocorrelation[degree]
                for (j in 1 until degree) residual += coefficients[j] * autocorrelation[degree - j]
                val reflection = (-residual / error.coerceAtLeast(1e-12)).coerceIn(-0.92, 0.92)
                val previous = coefficients.copyOf()
                coefficients[degree] = reflection
                for (j in 1 until degree) coefficients[j] = previous[j] + reflection * previous[degree - j]
                error *= (1.0 - reflection * reflection).coerceAtLeast(1e-4)
            }
            val predictor = FloatArray(order)
            for (i in 1..order) {
                val bandwidth = 0.985.pow(i.toDouble())
                predictor[i - 1] = (-coefficients[i] * bandwidth).toFloat().coerceIn(-0.95f, 0.95f)
            }
            return StudioLpcFormantModel(predictor)
        }
    }
}

internal fun pushLpcHistory(history: FloatArray, sample: Float) {
    for (index in history.lastIndex downTo 1) history[index] = history[index - 1]
    if (history.isNotEmpty()) history[0] = sample
}
