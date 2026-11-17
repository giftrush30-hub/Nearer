package com.nearer.app

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.TextOverlay

@androidx.media3.common.util.UnstableApi
internal class StudioTimedTextOverlay(
    private val layer: StudioVideoCaptionLayer
) : TextOverlay() {
    private val overlaySettings = OverlaySettings.Builder()
        .setOverlayFrameAnchor(0f, 0f)
        .setBackgroundFrameAnchor(0f, when (layer.verticalPosition) { 0 -> 0.78f; 1 -> 0f; else -> -0.78f })
        .setScale(0.85f, 0.85f)
        .build()

    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1_000L
        if (timeMs < layer.startMs || timeMs >= layer.endMs || layer.text.isBlank()) return SpannableString("")
        return SpannableString(layer.text).apply {
            setSpan(ForegroundColorSpan(layer.colorArgb), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(Color.argb(150, 8, 14, 22)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings
}
