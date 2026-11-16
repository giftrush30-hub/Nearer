package com.nearer.app

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

internal data class StudioPitchReading(
    val frequencyHz: Float,
    val midiNote: Float,
    val nearestMidiNote: Int,
    val cents: Int,
    val confidence: Float
)

internal data class StudioScaleTarget(
    val midiNote: Int,
    val noteName: String,
    val correctionCents: Int
)

internal object StudioPitchEngine {
    private val noteNames = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    /** YIN difference/CMND pitch estimate. Input is mono floating-point PCM in [-1, 1]. */
    fun detect(samples: FloatArray, sampleRate: Int, minHz: Float = 70f, maxHz: Float = 1_000f): StudioPitchReading? {
        if (sampleRate <= 0 || samples.size < 512 || minHz <= 0f || maxHz <= minHz) return null
        var energy = 0.0
        for (sample in samples) energy += sample * sample
        val rms = sqrt(energy / samples.size).toFloat()
        if (rms < 0.012f) return null

        val minTau = (sampleRate / maxHz).toInt().coerceAtLeast(2)
        val maxTau = (sampleRate / minHz).toInt().coerceAtMost(samples.size / 2)
        if (maxTau <= minTau + 2) return null
        val difference = FloatArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            val limit = samples.size - maxTau
            for (i in 0 until limit) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
            }
            difference[tau] = sum.toFloat()
        }

        val normalized = FloatArray(maxTau + 1)
        normalized[0] = 1f
        var running = 0f
        for (tau in 1..maxTau) {
            running += difference[tau]
            normalized[tau] = if (running > 0f) difference[tau] * tau / running else 1f
        }

        val threshold = 0.16f
        var selectedTau = -1
        var tau = minTau
        while (tau < maxTau) {
            if (normalized[tau] < threshold) {
                while (tau + 1 < maxTau && normalized[tau + 1] < normalized[tau]) tau++
                selectedTau = tau
                break
            }
            tau++
        }
        if (selectedTau < 0) {
            var best = minTau
            for (candidate in minTau + 1..maxTau) if (normalized[candidate] < normalized[best]) best = candidate
            if (normalized[best] > 0.28f) return null
            selectedTau = best
        }

        val left = normalized[(selectedTau - 1).coerceAtLeast(1)]
        val centre = normalized[selectedTau]
        val right = normalized[(selectedTau + 1).coerceAtMost(maxTau)]
        val denominator = left - 2f * centre + right
        val adjustment = if (abs(denominator) > 1e-7f) 0.5f * (left - right) / denominator else 0f
        val refinedTau = selectedTau + adjustment.coerceIn(-0.5f, 0.5f)
        val frequency = sampleRate / refinedTau
        if (frequency !in minHz..maxHz) return null
        val midi = (69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))).toFloat()
        val nearest = midi.toInt().let { if (midi - it >= 0.5f) it + 1 else it }
        return StudioPitchReading(
            frequencyHz = frequency,
            midiNote = midi,
            nearestMidiNote = nearest,
            cents = ((midi - nearest) * 100f).toInt(),
            confidence = (1f - centre).coerceIn(0f, 1f)
        )
    }

    fun noteName(midiNote: Int): String {
        val octave = Math.floorDiv(midiNote, 12) - 1
        return "${noteNames[Math.floorMod(midiNote, 12)]}$octave"
    }

    /** Finds the closest note in the selected key and scale, including adjacent octaves. */
    fun scaleTarget(reading: StudioPitchReading, rootSemitone: Int, isMinor: Boolean): StudioScaleTarget {
        val intervals = if (isMinor) minor else major
        val candidates = (-4..4).flatMap { octave ->
            intervals.map { interval -> 60 + rootSemitone.coerceIn(0, 11) + octave * 12 + interval }
        }
        val target = candidates.minByOrNull { abs(it - reading.midiNote) } ?: reading.nearestMidiNote
        return StudioScaleTarget(target, noteName(target), ((target - reading.midiNote) * 100f).toInt())
    }
}
 
@Composable
internal fun StudioPitchMonitorScreen(onBack: () -> Unit, preferredInputId: Int? = null) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<StudioPitchReading?>(null) }
    var status by remember { mutableStateOf("Listening for a clear single vocal note…") }
    var keyRoot by remember { mutableStateOf(0) }
    var minorScale by remember { mutableStateOf(false) }
    var monitorEnabled by remember { mutableStateOf(false) }
    val hasHeadphoneOutput = remember(context) {
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
        manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
            it.isSink && it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            )
        } == true
    }
    val keys = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    LaunchedEffect(monitorEnabled, preferredInputId) {
        if (!monitorEnabled) {
            reading = null
            status = "Microphone monitoring is off. Turn it on when you are ready."
            return@LaunchedEffect
        }
        val frameSize = 2_048
        val supportedRate = listOf(22_050, 44_100, 48_000).firstNotNullOfOrNull { rate ->
            val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (size > 0) rate to size else null
        }
        if (supportedRate == null) {
            status = "This device could not open a compatible microphone stream."
            return@LaunchedEffect
        }
        val sampleRate = supportedRate.first
        val minBuffer = supportedRate.second
        val recorder = withContext(Dispatchers.IO) {
            runCatching {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer, frameSize * 2 * 2))
                    .build()
                    .also { audioRecord ->
                        if (preferredInputId != null) {
                            val preferred = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                            val device = preferred?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.firstOrNull { it.id == preferredInputId }
                            check(device != null && audioRecord.setPreferredDevice(device)) {
                                "Android could not route the live monitor to the selected USB microphone. Reconnect it or choose Phone Default."
                            }
                        }
                        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) audioRecord.startRecording()
                    }
            }.getOrNull()
        }
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED || recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            withContext(Dispatchers.IO) { runCatching { recorder?.release() } }
            status = "Microphone unavailable. Close other recording apps and try again."
            return@LaunchedEffect
        }

        val monitorTrack = if (monitorEnabled) {
            runCatching {
                val minOutputBuffer = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                check(minOutputBuffer > 0) { "No compatible audio output is available." }
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(minOutputBuffer, 512 * 2 * 2))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
                    .also { track ->
                        check(track.state == AudioTrack.STATE_INITIALIZED) { "Android could not initialise the monitoring output." }
                        track.play()
                    }
            }.getOrElse {
                status = "Live monitor unavailable: ${it.message ?: "audio output could not start"}"
                null
            }
        } else null

        val chunkSize = 512
        val pcm = ShortArray(chunkSize)
        val mono = FloatArray(frameSize)
        var pitchOffset = 0
        try {
            while (isActive) {
                val count = withContext(Dispatchers.IO) {
                    val read = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                    if (read > 0 && monitorTrack != null) {
                        val written = monitorTrack.write(pcm, 0, read, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) -2 else read
                    } else read
                }
                if (count == -2) {
                    status = "Live monitor output was interrupted."
                    continue
                }
                if (count <= 0) {
                    status = "Microphone read interrupted."
                    continue
                }
                for (i in 0 until count) {
                    mono[pitchOffset + i] = pcm[i] / 32768f
                }
                pitchOffset += count
                if (pitchOffset >= frameSize) {
                    reading = StudioPitchEngine.detect(mono, sampleRate, 70f, 1_000f)
                    status = if (reading == null) "No stable pitch yet — sing or hold one clear note." else "Live pitch detected"
                    pitchOffset = 0
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { monitorTrack?.pause() }
                runCatching { monitorTrack?.flush() }
                runCatching { monitorTrack?.release() }
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    val current = reading
    val target = current?.let { StudioPitchEngine.scaleTarget(it, keyRoot, minorScale) }
    Column(
        Modifier.fillMaxSize().background(Midnight).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹  STUDIO", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("PITCH MONITOR", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Text("Vocal pitch & key guide", color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (preferredInputId == null) "Live microphone analysis · Phone default input · No recording is saved" else "Live microphone analysis · Selected USB input · No recording is saved", color = Muted, fontSize = 11.sp)
        Button(
            onClick = { monitorEnabled = !monitorEnabled },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (monitorEnabled) Mint else Panel,
                contentColor = if (monitorEnabled) Midnight else Gold
            )
        ) { Text(if (monitorEnabled) "LIVE VOCAL MONITOR · ON" else "TURN ON LIVE VOCAL MONITOR") }
        Text(
            if (monitorEnabled) "Monitoring is playing the microphone through Android's low-latency AudioTrack mode. Wired or USB headphones are strongly recommended to prevent speaker feedback."
            else if (hasHeadphoneOutput) "Headphone output detected. Bluetooth may still introduce noticeable delay."
            else "Connect wired or USB headphones before monitoring. Speaker playback can feed back into the microphone.",
            color = Muted, fontSize = 10.sp, lineHeight = 15.sp
        )
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (current == null) "WAITING FOR VOICE" else "LIVE NOTE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(current?.let { StudioPitchEngine.noteName(it.nearestMidiNote) } ?: "—", color = White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold)
                Text(current?.let { "${"%.1f".format(it.frequencyHz)} Hz   ·   ${it.cents} cents" } ?: status,
                    color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(current?.let { "Pitch confidence ${(it.confidence * 100).toInt()}%" } ?: "Sing one note at a time for the clearest reading",
                    color = Muted, fontSize = 10.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("SET THE SONG KEY", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(false, true).forEach { minor ->
                        Button(onClick = { minorScale = minor }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (minorScale == minor) Mint else Midnight,
                                contentColor = if (minorScale == minor) Midnight else White)) {
                            Text(if (minor) "MINOR" else "MAJOR", fontSize = 10.sp)
                        }
                    }
                }
                keys.chunked(6).forEachIndexed { rowIndex, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowKeys.forEachIndexed { columnIndex, key ->
                            val index = rowIndex * 6 + columnIndex
                            Button(onClick = { keyRoot = index }, modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (keyRoot == index) Mint else Midnight,
                                    contentColor = if (keyRoot == index) Midnight else White)) {
                                Text(key, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("Nearest note in ${keys[keyRoot]} ${if (minorScale) "minor" else "major"}: ${target?.noteName ?: "—"}",
                    color = Mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Target offset: ${target?.correctionCents ?: 0} cents · This is a tuning guide, not pitch-shifted audio yet.",
                    color = Muted, fontSize = 10.sp)
            }
        }
        Text(status, color = Muted, fontSize = 11.sp)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) { Text("BACK TO RECORDER") }
    }
}
