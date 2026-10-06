package com.masselis.tpmsadvanced.feature.background.usecase

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

/**
 * The tone patterns played instead of the speech, see docs/alerts.md. Shaped after the medical
 * alarms' (IEC 60601-1-8), which people already tell apart: a medium priority's 3 pulses for a red
 * alert, a high priority's fast burst of 10 for a crimson one. Generated rather than bundled, a
 * pulse being a tone with a few harmonics, which carries through road noise and a helmet better
 * than a pure one.
 */
@Suppress("MagicNumber")
internal object AlertTones {

    private val logger = Logger.withTag("AlertTones")

    /** Plays the crimson or the red pattern with [attributes], returning once it's played */
    suspend fun play(isCrimson: Boolean, attributes: AudioAttributes) {
        val samples = if (isCrimson) crimson else red
        val track = AudioTrack
            .Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat
                    .Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .build()
        try {
            track.write(samples, 0, samples.size)
            val start = TimeSource.Monotonic.markNow()
            track.play()
            // Until the track says it played everything, rather than for the pattern's duration:
            // after a few seconds of silence, the output is on standby and takes a while to start
            // again, more so over Bluetooth, which used to cut the pattern's end off
            withTimeoutOrNull(samples.size * MILLIS_PER_SECOND / SAMPLE_RATE + MAX_START_MILLIS) {
                while (track.playbackHeadPosition < samples.size) delay(POLL_MILLIS)
            }
            logger.d { "Played ${samples.size * MILLIS_PER_SECOND / SAMPLE_RATE} ms of tones in ${start.elapsedNow()}" }
            // The last frames played still have to leave the speaker, a few hundred milliseconds
            // later over Bluetooth
            delay(TAIL_MILLIS)
        } finally {
            // Stopping an ended static track throws on some versions, it's released anyway
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Pulses starting at their offset, in milliseconds, at their frequency */
    private data class Pulse(val startMillis: Int, val frequency: Double)

    /** 3 pulses, twice */
    private val red by lazy {
        listOf(0, RED_SPACING, 2 * RED_SPACING)
            .let { pulses -> pulses + pulses.map { it + RED_REPEAT } }
            .map { Pulse(it, RED_FREQUENCY) }
            .samples(RED_PULSE)
    }

    /** 3 pulses and 2 after a pause, twice: the high priority burst */
    private val crimson by lazy {
        listOf(0, 1, 2)
            .map { it * CRIMSON_SPACING }
            .plus(listOf(3, 4).map { it * CRIMSON_SPACING + CRIMSON_PAUSE })
            .let { pulses -> pulses + pulses.map { it + CRIMSON_REPEAT } }
            .map { Pulse(it, CRIMSON_FREQUENCY) }
            .samples(CRIMSON_PULSE)
    }

    private fun List<Pulse>.samples(pulseMillis: Int): ShortArray {
        val pulseLength = pulseMillis * SAMPLE_RATE / MILLIS_PER_SECOND.toInt()
        val ramp = RAMP_MILLIS * SAMPLE_RATE / MILLIS_PER_SECOND.toInt()
        val samples = ShortArray(maxOf { it.startMillis } * SAMPLE_RATE / MILLIS_PER_SECOND.toInt() + pulseLength)
        forEach { pulse ->
            val start = pulse.startMillis * SAMPLE_RATE / MILLIS_PER_SECOND.toInt()
            for (i in 0 until pulseLength) {
                // Ramps in and out, a click otherwise
                val envelope = minOf(1.0, i.toDouble() / ramp, (pulseLength - i).toDouble() / ramp)
                val time = i.toDouble() / SAMPLE_RATE
                val wave = HARMONICS.withIndex().sumOf { (index, amplitude) ->
                    amplitude * sin(2 * PI * pulse.frequency * (index + 1) * time)
                } / HARMONICS.sum()
                samples[start + i] = (wave * envelope * PEAK * Short.MAX_VALUE).roundToInt().toShort()
            }
        }
        return samples
    }

    private const val SAMPLE_RATE = 44_100
    private const val MILLIS_PER_SECOND = 1000L
    private const val TAIL_MILLIS = 500L
    private const val POLL_MILLIS = 20L

    /** Gives up waiting after that, a track which never plays doesn't hold the speech queue */
    private const val MAX_START_MILLIS = 3000L
    private const val RAMP_MILLIS = 10
    private const val PEAK = 0.8

    /** The fundamental and 3 harmonics, decreasing */
    private val HARMONICS = listOf(1.0, 0.5, 0.33, 0.25)

    private const val RED_FREQUENCY = 523.0
    private const val RED_PULSE = 200
    private const val RED_SPACING = 300
    private const val RED_REPEAT = 1600

    private const val CRIMSON_FREQUENCY = 880.0
    private const val CRIMSON_PULSE = 120
    private const val CRIMSON_SPACING = 200
    private const val CRIMSON_PAUSE = 200
    private const val CRIMSON_REPEAT = 1600
}
