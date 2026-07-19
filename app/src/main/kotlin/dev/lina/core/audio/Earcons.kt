package dev.lina.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Kurze akustische Rückmeldungen (Earcons) für Wartezeiten, in denen Lina
 * nichts sagt: "gehört" nach der Aufnahme (vor der ~2s-Transkription) und
 * "denkt nach" vor einer Claude-Anfrage. Synthetisierte weiche Sinus-Blips
 * mit Hüllkurve – kein Klicken, kein schrilles DTMF.
 *
 * Fire-and-forget: spielt auf eigenem Thread, blockiert nie.
 */
object Earcons {

    /** Einzelner Blip: "Ich habe dich gehört, ich arbeite." */
    fun ack() = play(floatArrayOf(880f), 120)

    /** Zwei aufsteigende Blips: "Ich denke nach." */
    fun thinking() = play(floatArrayOf(660f, 880f), 110)

    /** Heller Einzelton: "Jetzt sprechen." (Onboarding-Aufnahmen) */
    fun go() = play(floatArrayOf(1040f), 140)

    private fun play(frequencies: FloatArray, toneMs: Int) {
        Thread({
            try {
                val pcm = synthesize(frequencies, toneMs)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // MODE_STATIC: warten bis fertig, dann freigeben
                Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 50)
                track.release()
            } catch (_: Exception) {
                // Earcon ist reine Komfortfunktion – Fehler nie eskalieren
            }
        }, "earcon").start()
    }

    /** Sinus-Blips mit 10ms Attack / 30ms Release, 60ms Pause zwischen Tönen. */
    private fun synthesize(frequencies: FloatArray, toneMs: Int): ShortArray {
        val toneSamples = SAMPLE_RATE * toneMs / 1000
        val gapSamples = SAMPLE_RATE * GAP_MS / 1000
        val attack = SAMPLE_RATE * 10 / 1000
        val release = SAMPLE_RATE * 30 / 1000
        val total = frequencies.size * toneSamples + (frequencies.size - 1) * gapSamples
        val out = ShortArray(total)
        var pos = 0
        for ((index, freq) in frequencies.withIndex()) {
            for (i in 0 until toneSamples) {
                val env = min(
                    1f,
                    min(i / attack.toFloat(), (toneSamples - i) / release.toFloat())
                )
                val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * env * AMPLITUDE
                out[pos++] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            if (index < frequencies.size - 1) pos += gapSamples
        }
        return out
    }

    private const val SAMPLE_RATE = 22050
    private const val GAP_MS = 60
    private const val AMPLITUDE = 0.35
}
