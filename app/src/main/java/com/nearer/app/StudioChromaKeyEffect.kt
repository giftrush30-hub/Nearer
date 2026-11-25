package com.nearer.app

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.IOException

/** User-facing controls for removing a green or blue screen without an online service. */
internal data class StudioChromaKeySettings(
    val keyColorArgb: Int = Color.GREEN,
    val tolerance: Float = 0.22f,
    val edgeSoftness: Float = 0.12f,
    val despill: Float = 0.65f
) {
    fun validate(): List<String> = buildList {
        if (tolerance !in 0f..1f || !tolerance.isFinite()) add("Key tolerance must be between 0 and 1.")
        if (edgeSoftness !in 0.001f..1f || !edgeSoftness.isFinite()) add("Edge softness must be between 0.001 and 1.")
        if (despill !in 0f..1f || !despill.isFinite()) add("Spill cleanup must be between 0 and 1.")
    }
    val isValid: Boolean get() = validate().isEmpty()
}

/**
 * GPU chroma-key pass. Produces transparent pixels where the selected key colour is found.
 * Background replacement is a separate composition layer and must be placed underneath this
 * foreground sequence; this effect intentionally does not fabricate a replacement background.
 */
@UnstableApi
internal class StudioChromaKeyEffect(private val settings: StudioChromaKeySettings) : GlEffect {
    init {
        require(settings.isValid) { settings.validate().joinToString() }
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        StudioChromaKeyShaderProgram(context, useHdr, settings)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = false
}

@UnstableApi
private class StudioChromaKeyShaderProgram(
    context: Context,
    useHdr: Boolean,
    settings: StudioChromaKeySettings
) : BaseGlShaderProgram(useHdr, 1) {
    private val glProgram: GlProgram

    init {
        try {
            glProgram = GlProgram(
                context,
                R.raw.studio_vertex_shader_es2,
                R.raw.studio_chroma_key_fragment_es2
            )
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
            val color = settings.keyColorArgb
            glProgram.setFloatsUniform("uKeyColor", floatArrayOf(
                Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f
            ))
            glProgram.setFloatUniform("uTolerance", settings.tolerance)
            glProgram.setFloatUniform("uSoftness", settings.edgeSoftness)
            glProgram.setFloatUniform("uDespill", settings.despill)
        } catch (error: IOException) {
            throw VideoFrameProcessingException(error)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }
}
