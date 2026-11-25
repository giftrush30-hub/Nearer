package com.nearer.app

/** A downscaled grayscale frame supplied by the offline media decoder. */
internal data class StudioGrayFrame(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    fun value(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF
}

internal data class StudioTrackedPoint(
    val frameIndex: Int,
    val x: Float,
    val y: Float,
    val confidence: Float,
    val isTracked: Boolean
)

internal data class StudioMotionTrack(
    val points: List<StudioTrackedPoint>,
    val lostAtFrame: Int?
)

/**
 * Lightweight, deterministic template matcher for offline point tracking.
 *
 * It is deliberately not AI: it uses normalized cross-correlation over small grayscale patches.
 * Feed preview-sized frames (for example 160–320 px wide) rather than full-resolution video.
 */
internal object StudioTemplateMotionTracker {
    fun track(
        frames: List<StudioGrayFrame>,
        startX: Float,
        startY: Float,
        patchRadius: Int = 4,
        searchRadius: Int = 12,
        minimumConfidence: Float = 0.72f
    ): StudioMotionTrack {
        require(frames.isNotEmpty()) { "At least one frame is required." }
        require(frames.all { it.width == frames.first().width && it.height == frames.first().height }) {
            "All tracking frames must have identical dimensions."
        }
        require(startX.isFinite() && startY.isFinite() && startX in 0f..1f && startY in 0f..1f)
        require(patchRadius in 2..12)
        require(searchRadius in 1..32)
        require(minimumConfidence.isFinite() && minimumConfidence in 0f..1f)

        val first = frames.first()
        val margin = patchRadius + searchRadius
        require(first.width > margin * 2 + 1 && first.height > margin * 2 + 1) {
            "Frames are too small for the selected patch and search radius."
        }
        var x = (startX * (first.width - 1)).toInt().coerceIn(margin, first.width - margin - 1)
        var y = (startY * (first.height - 1)).toInt().coerceIn(margin, first.height - margin - 1)
        val side = patchRadius * 2 + 1
        val template = DoubleArray(side * side)
        val candidate = DoubleArray(side * side)
        copyPatch(first, x, y, patchRadius, template)
        val points = mutableListOf(StudioTrackedPoint(0, x / (first.width - 1f), y / (first.height - 1f), 1f, true))

        for (frameIndex in 1 until frames.size) {
            val frame = frames[frameIndex]
            var bestX = x
            var bestY = y
            var bestScore = -1.0
            val minX = (x - searchRadius).coerceAtLeast(patchRadius)
            val maxX = (x + searchRadius).coerceAtMost(frame.width - patchRadius - 1)
            val minY = (y - searchRadius).coerceAtLeast(patchRadius)
            val maxY = (y + searchRadius).coerceAtMost(frame.height - patchRadius - 1)
            for (candidateY in minY..maxY) {
                for (candidateX in minX..maxX) {
                    copyPatch(frame, candidateX, candidateY, patchRadius, candidate)\n                    val score = normalizedCorrelation(template, candidate)
                    if (score > bestScore) {
                        bestScore = score
                        bestX = candidateX
                        bestY = candidateY
                    }
                }
            }
            val confidence = ((bestScore + 1.0) * 0.5).toFloat().coerceIn(0f, 1f)
            if (confidence < minimumConfidence) {
                return StudioMotionTrack(
                    points + StudioTrackedPoint(frameIndex, x / (frame.width - 1f), y / (frame.height - 1f), confidence, false),
                    lostAtFrame = frameIndex
                )
            }
            x = bestX
            y = bestY
            points += StudioTrackedPoint(frameIndex, x / (frame.width - 1f), y / (frame.height - 1f), confidence, true)
        }
        return StudioMotionTrack(points, lostAtFrame = null)
    }

    private fun copyPatch(
        frame: StudioGrayFrame,
        centerX: Int,
        centerY: Int,
        radius: Int,
        destination: DoubleArray
    ) {
        val side = radius * 2 + 1
        require(destination.size == side * side)
        var index = 0
        for (y in centerY - radius..centerY + radius) {
            for (x in centerX - radius..centerX + radius) {
                destination[index++] = frame.value(x, y).toDouble()
            }
        }
    }

    private fun normalizedCorrelation(a: DoubleArray, b: DoubleArray): Double {
        var sumA = 0.0
        var sumB = 0.0
        for (i in a.indices) {
            sumA += a[i]
            sumB += b[i]
        }
        val meanA = sumA / a.size
        val meanB = sumB / b.size
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        for (i in a.indices) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            covariance += da * db
            varianceA += da * da
            varianceB += db * db
        }
        if (varianceA < 1e-9 || varianceB < 1e-9) {
            return if (varianceA < 1e-9 && varianceB < 1e-9 && kotlin.math.abs(meanA - meanB) < 1.0) 1.0 else -1.0
        }
        return (covariance / kotlin.math.sqrt(varianceA * varianceB)).coerceIn(-1.0, 1.0)
    }
}
