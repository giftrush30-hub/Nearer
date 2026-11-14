package com.nearer.app.games.ludo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.exp
import kotlin.random.Random

internal enum class LudoSound { ROLL, LAND, MOVE, CAPTURE, HOME, WIN }

/** Tiny generated PCM cues are cached and preloaded for low-latency playback. */
internal class LudoSoundEffects(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val files = mutableListOf<File>()
    private val samples = mutableMapOf<LudoSound, Int>()
    private val ready = mutableSetOf<Int>()
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready.add(id) } }
        LudoSound.values().forEach { cue ->
            val file = File(context.cacheDir, "nearer_ludo_" + cue.name.lowercase() + ".wav")
            file.writeBytes(makeWave(cue)); files.add(file); samples[cue] = pool.load(file.absolutePath, 1)
        }
    }
    fun play(cue: LudoSound) {
        val id = samples[cue] ?: return
        if (synchronized(ready) { id in ready }) pool.play(id, 0.72f, 0.72f, 1, 0, 1f)
    }
    fun release() { pool.release(); files.forEach { it.delete() } }
    private fun makeWave(cue: LudoSound): ByteArray {
        val rate = 22050
        val duration = when (cue) { LudoSound.ROLL -> 0.34; LudoSound.LAND -> 0.13; LudoSound.MOVE -> 0.10; LudoSound.CAPTURE -> 0.24; LudoSound.HOME -> 0.30; LudoSound.WIN -> 0.62 }
        val count = (rate * duration).toInt(); val pcm = ByteArray(count * 2)
        for (i in 0 until count) {
            val t = i.toDouble() / rate; val progress = i.toDouble() / count
            val tones = when (cue) {
                LudoSound.ROLL -> Triple(150.0, 95.0, 0.34)
                LudoSound.LAND -> Triple(520.0, 260.0, 0.05)
                LudoSound.MOVE -> Triple(420.0, 680.0, 0.02)
                LudoSound.CAPTURE -> Triple(260.0, 90.0, 0.22)
                LudoSound.HOME -> Triple(620.0, 980.0, 0.03)
                LudoSound.WIN -> Triple(520.0, 1040.0, 0.02)
            }
            val frequency = tones.first + (tones.second - tones.first) * progress
            val envelope = (progress / 0.12).coerceIn(0.0, 1.0) * exp(-progress * if (cue == LudoSound.WIN) 1.3 else 3.0)
            val tone = sin(2.0 * PI * frequency * t) * 0.72 + sin(2.0 * PI * frequency * 1.51 * t) * 0.18
            val grit = (Random(i).nextDouble() * 2.0 - 1.0) * tones.third
            val sample = ((tone + grit) * envelope * 0.48 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
            pcm[i * 2] = (sample.toInt() and 0xff).toByte(); pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + pcm.size); header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()); header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(rate); header.putInt(rate * 2); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(pcm.size)
        return header.array() + pcm
    }
}
