package com.nearer.app

import android.content.Context
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds one combined alpha plane; multiple masks intersect rather than overwrite each other. */
internal object StudioGpuMaskTexturePlanner {
    const val MAX_MASKS = 8

    fun rasterizeCombined(masks: List<StudioMaskLayer>, width: Int, height: Int): StudioAlphaMask {
        require(masks.size in 1..MAX_MASKS) { "GPU mask pass requires 1–8 masks." }
        require(width > 0 && height > 0 && width.toLong() * height <= StudioMaskRasterizer.MAX_PIXELS) {
            "GPU mask dimensions must be positive and no larger than 16 megapixels."
        }
        val combined = ByteArray(width * height) { 0xFF.toByte() }
        masks.forEach { layer ->
            val next = StudioMaskRasterizer.rasterize(layer, width, height).alpha
            for (index in combined.indices) {
                val currentAlpha = combined[index].toInt() and 0xFF
                val nextAlpha = next[index].toInt() and 0xFF
                combined[index] = (currentAlpha * nextAlpha / 255).toByte()
            }
        }
        return StudioAlphaMask(width, height, combined)
    }
}

/**
 * GPU mask pass for one Media3 input stream. Mask geometry is rasterized once per input-size
 * configuration, uploaded as a single-channel texture, and sampled by the fragment shader for
 * every video frame. The full-resolution video pixels are never copied through the CPU.
 */
@UnstableApi
internal class StudioGpuMaskEffect(private val masks: List<StudioMaskLayer>) : GlEffect {
    init {
        require(masks.size in 1..StudioGpuMaskTexturePlanner.MAX_MASKS) {
            "GPU mask pass requires 1–8 masks."
        }
        val errors = masks.flatMap { StudioKeyframeEvaluator.validateMask(it).errors }
        require(errors.isEmpty()) { errors.joinToString() }
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        StudioGpuMaskShaderProgram(context, useHdr, masks)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = masks.isEmpty()
}

@UnstableApi
private class StudioGpuMaskShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val masks: List<StudioMaskLayer>
) : BaseGlShaderProgram(useHdr, 1) {
    private val glProgram: GlProgram
    private var maskTextureId = 0

    init {
        try {
            glProgram = GlProgram(
                context,
                R.raw.studio_vertex_shader_es2,
                R.raw.studio_mask_fragment_es2
            )
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            val identity = GlUtil.create4x4IdentityMatrix()
            glProgram.setFloatsUniform("uTransformationMatrix", identity)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
        } catch (error: IOException) {
            throw VideoFrameProcessingException(error)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        try {
            deleteMaskTexture()
            val mask = StudioGpuMaskTexturePlanner.rasterizeCombined(masks, inputWidth, inputHeight)
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            maskTextureId = textureIds[0]
            check(maskTextureId != 0) { "Could not allocate the GPU mask texture." }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val pixels = ByteBuffer.allocateDirect(mask.alpha.size).order(ByteOrder.nativeOrder())
            pixels.put(mask.alpha)
            pixels.position(0)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                inputWidth, inputHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, pixels
            )
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
            val glError = GLES20.glGetError()
            check(glError == GLES20.GL_NO_ERROR) { "GPU mask texture upload failed (GL error " + glError + ")." }
            return Size(inputWidth, inputHeight)
        } catch (error: Exception) {
            deleteMaskTexture()
            throw VideoFrameProcessingException(error)
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setSamplerTexIdUniform("uMaskSampler", maskTextureId, 1)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            super.release()
        } finally {
            deleteMaskTexture()
            try {
                glProgram.delete()
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error)
            }
        }
    }

    private fun deleteMaskTexture() {
        if (maskTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(maskTextureId), 0)
            maskTextureId = 0
        }
    }
}
