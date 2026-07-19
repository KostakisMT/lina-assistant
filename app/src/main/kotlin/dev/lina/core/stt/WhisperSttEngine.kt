package dev.lina.core.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * Whisper STT über sherpa-onnx (base, int8, multilingual, language=de).
 * Nicht-streamend: nimmt auf bis ~1.2s Stille nach Sprachbeginn
 * (max. 10s), transkribiert dann in einem Stück.
 * Modell wird direkt aus den Assets gelesen (kein Kopieren nötig).
 */
class WhisperSttEngine(private val context: Context) : SttEngine {

    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var listenThread: Thread? = null
    @Volatile private var listening = false

    /**
     * Optional: wird aufgerufen, sobald die Aufnahme beendet ist und die
     * (~2s dauernde) Transkription beginnt – z.B. für einen Bestätigungston.
     * Nicht Teil des SttEngine-Interface (Whisper-spezifisch, da Vosk streamt).
     */
    var onSpeechCaptured: (() -> Unit)? = null

    fun initialize(onReady: () -> Unit, onError: (Exception) -> Unit) {
        Thread({
            try {
                val config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = "$ASSET_DIR/base-encoder.int8.onnx",
                            decoder = "$ASSET_DIR/base-decoder.int8.onnx",
                            language = "de",
                            task = "transcribe",
                        ),
                        tokens = "$ASSET_DIR/base-tokens.txt",
                        modelType = "whisper",
                        numThreads = 4,
                    ),
                )
                recognizer = OfflineRecognizer(context.assets, config)
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Whisper-Initialisierung fehlgeschlagen", e)
                onError(e)
            }
        }, "whisper-init").start()
    }

    override fun startListening(onResult: (String) -> Unit) {
        val rec = recognizer ?: return
        if (listening) return
        listening = true

        listenThread = Thread({
            val samples = try {
                recordUntilSilence()
            } catch (e: Exception) {
                Log.e(TAG, "Aufnahme fehlgeschlagen", e)
                listening = false
                return@Thread
            }
            if (!listening) return@Thread // abgebrochen
            listening = false
            if (samples.size < SAMPLE_RATE / 2) {
                Log.d(TAG, "Zu wenig Audio (${samples.size} Samples), verworfen")
                onResult("")
                return@Thread
            }
            onSpeechCaptured?.invoke()

            val t0 = System.currentTimeMillis()
            val text = try {
                val stream = rec.createStream()
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)
                val result = rec.getResult(stream).text.trim()
                stream.release()
                result
            } catch (e: Exception) {
                Log.e(TAG, "Transkription fehlgeschlagen", e)
                ""
            }
            Log.d(
                TAG,
                "Transkription \"${text}\" (${samples.size / SAMPLE_RATE.toFloat()}s Audio " +
                    "in ${System.currentTimeMillis() - t0}ms)"
            )
            onResult(text)
        }, "whisper-listen").apply { start() }
    }

    override fun stopListening() {
        listening = false
        stopAudioRecord()
    }

    override fun destroy() {
        stopListening()
        listenThread?.join(1000)
        listenThread = null
        recognizer?.release()
        recognizer = null
    }

    /** Nimmt auf bis Stille nach Sprachbeginn oder MAX_RECORD_MS erreicht. */
    private fun recordUntilSilence(): FloatArray {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING),
            FRAME_SAMPLES * 2,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL, ENCODING, bufferSize,
        )
        audioRecord = record
        record.startRecording()

        val collected = ArrayList<FloatArray>()
        val frame = ShortArray(FRAME_SAMPLES)
        var speechStarted = false
        var silenceMs = 0
        var totalMs = 0

        try {
            while (listening && totalMs < MAX_RECORD_MS) {
                val read = record.read(frame, 0, FRAME_SAMPLES)
                if (read <= 0) break
                totalMs += read * 1000 / SAMPLE_RATE

                var maxAmp = 0
                val floats = FloatArray(read)
                for (i in 0 until read) {
                    val s = frame[i]
                    floats[i] = s / 32768f
                    val a = if (s < 0) -s.toInt() else s.toInt()
                    if (a > maxAmp) maxAmp = a
                }
                collected.add(floats)

                if (maxAmp >= SPEECH_AMP_THRESHOLD) {
                    speechStarted = true
                    silenceMs = 0
                } else if (speechStarted) {
                    silenceMs += read * 1000 / SAMPLE_RATE
                    if (silenceMs >= END_SILENCE_MS) break
                } else if (totalMs >= NO_SPEECH_TIMEOUT_MS) {
                    // Nutzer hat gar nicht gesprochen
                    return FloatArray(0)
                }
            }
        } finally {
            stopAudioRecord()
        }

        val total = collected.sumOf { it.size }
        val all = FloatArray(total)
        var pos = 0
        for (chunk in collected) {
            chunk.copyInto(all, pos)
            pos += chunk.size
        }
        return all
    }

    private fun stopAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    companion object {
        private const val TAG = "WhisperStt"
        private const val ASSET_DIR = "whisper/sherpa-onnx-whisper-base"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 1600 // 100ms
        private const val SPEECH_AMP_THRESHOLD = 1000
        private const val END_SILENCE_MS = 1200
        private const val NO_SPEECH_TIMEOUT_MS = 5000
        private const val MAX_RECORD_MS = 10000
    }
}
