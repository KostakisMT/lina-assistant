package dev.lina.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Nimmt eine feste Dauer vom Mikrofon auf und schreibt eine 16kHz-Mono-WAV
 * (Format wie das Wake-Word-Training erwartet). Blockierend – von einem
 * Worker-Thread aufrufen.
 */
object WavRecorder {

    fun record(durationMs: Int, file: File): Boolean {
        val sr = SAMPLE_RATE
        val bufSize = maxOf(
            AudioRecord.getMinBufferSize(
                sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ),
            sr,
        )
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        val pcm = ByteArray(sr * 2 * durationMs / 1000)
        return try {
            rec.startRecording()
            var pos = 0
            while (pos < pcm.size) {
                val n = rec.read(pcm, pos, minOf(sr, pcm.size - pos))
                if (n <= 0) break
                pos += n
            }
            rec.stop()
            writeWav(file, pcm, pos, sr)
            pos > 0
        } catch (e: Exception) {
            false
        } finally {
            rec.release()
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, dataLen: Int, sr: Int) {
        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataLen)
            header.put("WAVEfmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)          // PCM
            header.putShort(1)          // mono
            header.putInt(sr)
            header.putInt(sr * 2)       // byte rate
            header.putShort(2)          // block align
            header.putShort(16)         // bits
            header.put("data".toByteArray())
            out.write(header.array())
            out.write(pcm, 0, dataLen)
        }
    }

    private const val SAMPLE_RATE = 16000
}
