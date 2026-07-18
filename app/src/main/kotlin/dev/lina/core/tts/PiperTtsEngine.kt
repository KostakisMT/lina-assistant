package dev.lina.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

/**
 * Piper TTS über sherpa-onnx – natürliche deutsche Stimme, komplett offline.
 * Modell liegt in assets/piper/<MODEL_NAME>/ und wird beim ersten Start
 * nach filesDir kopiert (espeak-ng-data braucht echte Dateipfade).
 */
class PiperTtsEngine(private val context: Context) : TtsEngine {

    private var tts: OfflineTts? = null
    private val engineLock = Any()
    @Volatile private var ready = false
    @Volatile private var shuttingDown = false
    @Volatile private var rate = 0.9f
    @Volatile var currentVoice: String = AVAILABLE_VOICES.first()
        private set
    @Volatile private var playing = false
    @Volatile private var lastPlaybackEnd = 0L

    /**
     * true solange Lina spricht, Ansagen anstehen oder die Wiedergabe gerade
     * erst endete (Weckwort-Erkennung hinkt ~1s hinterher – Echo-Unterdrückung).
     */
    fun isSpeaking(): Boolean =
        playing || queue.isNotEmpty() ||
            System.currentTimeMillis() - lastPlaybackEnd < ECHO_GUARD_MS
    private val queue = LinkedBlockingDeque<Pair<String, TtsPriority>>()
    private var audioTrack: AudioTrack? = null
    private var workerThread: Thread? = null

    /** Lädt das Modell im Hintergrund; onReady/onError auf beliebigem Thread. */
    fun initialize(onReady: () -> Unit, onError: (Exception) -> Unit) {
        Thread({
            try {
                tts = loadVoice(currentVoice)
                ready = true
                startWorker()
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Piper-Initialisierung fehlgeschlagen", e)
                onError(e)
            }
        }, "piper-init").start()
    }

    /**
     * Wechselt die Stimme (Name aus AVAILABLE_VOICES oder Index 1-basiert).
     * Läuft im Hintergrund; onDone erhält den Namen der aktiven Stimme.
     */
    fun switchVoice(selector: String, onDone: (String) -> Unit, onError: (Exception) -> Unit) {
        val voice = selector.toIntOrNull()?.let { AVAILABLE_VOICES.getOrNull(it - 1) }
            ?: AVAILABLE_VOICES.firstOrNull { it.contains(selector, ignoreCase = true) }
        if (voice == null) {
            onError(IllegalArgumentException("Unbekannte Stimme: $selector"))
            return
        }
        Thread({
            try {
                val newTts = loadVoice(voice)
                stop()
                synchronized(engineLock) {
                    tts?.release()
                    tts = newTts
                    currentVoice = voice
                }
                Log.d(TAG, "Stimme gewechselt zu $voice")
                onDone(voice)
            } catch (e: Exception) {
                Log.e(TAG, "Stimmwechsel zu $voice fehlgeschlagen", e)
                onError(e)
            }
        }, "piper-switch").start()
    }

    private fun loadVoice(voice: String): OfflineTts {
        val modelDir = copyModelFromAssets("vits-piper-$voice")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, "$voice.onnx").absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
            ),
        )
        return OfflineTts(config = config)
    }

    override fun speak(text: String, priority: TtsPriority) {
        if (text.isBlank()) return
        if (priority == TtsPriority.INTERRUPT) {
            queue.clear()
            stopPlayback()
            queue.offerFirst(text to priority)
        } else {
            queue.offer(text to priority)
        }
    }

    override fun stop() {
        queue.clear()
        stopPlayback()
    }

    override fun setRate(rate: Float) {
        this.rate = rate
    }

    override fun shutdown() {
        shuttingDown = true
        stop()
        workerThread?.interrupt()
        workerThread = null
        tts?.release()
        tts = null
        ready = false
    }

    private fun startWorker() {
        workerThread = Thread({
            while (!shuttingDown) {
                val (text, _) = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
                try {
                    synthesizeAndPlay(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Sprachausgabe fehlgeschlagen: \"$text\"", e)
                }
            }
        }, "piper-speak").apply { start() }
    }

    private fun synthesizeAndPlay(text: String) {
        val t0 = System.currentTimeMillis()
        val audio = synchronized(engineLock) {
            val engine = tts ?: return
            engine.generate(text = text, sid = 0, speed = rate)
        }
        Log.d(TAG, "Synthese ${audio.samples.size / audio.sampleRate.toFloat()}s Audio in ${System.currentTimeMillis() - t0}ms")
        if (audio.samples.isEmpty()) return

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSizeBytes(audio.sampleRate))
            .build()

        synchronized(this) { audioTrack = track }
        try {
            playing = true
            track.play()
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            // Blocking write kehrt zurück, sobald der Puffer geschrieben ist –
            // kurz warten bis der Rest gespielt ist
            track.stop()
        } catch (_: IllegalStateException) {
            // stopPlayback() hat den Track parallel freigegeben
        } finally {
            playing = false
            lastPlaybackEnd = System.currentTimeMillis()
            synchronized(this) {
                if (audioTrack === track) audioTrack = null
            }
            track.release()
        }
    }

    private fun bufferSizeBytes(sampleRate: Int): Int {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        // ~0.25s Puffer; muss ein Vielfaches der Framegröße (4 Bytes, Float mono) sein
        val size = maxOf(minBuf, sampleRate)
        return (size + 3) / 4 * 4
    }

    private fun stopPlayback() {
        synchronized(this) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
            } catch (_: IllegalStateException) {}
        }
    }

    private fun copyModelFromAssets(modelName: String): File {
        val targetDir = File(context.filesDir, "piper/$modelName")
        val marker = File(targetDir, ".complete")
        if (marker.exists()) return targetDir
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        copyAssetDir("piper/$modelName", targetDir)
        marker.createNewFile()
        return targetDir
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val children = context.assets.list(assetPath) ?: return
        for (child in children) {
            val childAsset = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            val grandChildren = context.assets.list(childAsset)
            if (grandChildren != null && grandChildren.isNotEmpty()) {
                childTarget.mkdirs()
                copyAssetDir(childAsset, childTarget)
            } else {
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PiperTts"
        private const val ECHO_GUARD_MS = 2000L

        /**
         * Reihenfolge = Nummer im Sprachbefehl "Stimme <n>".
         * Gewählt am 2026-07-04 (ADR-016): de_DE-dii-high (OpenVoiceOS,
         * CC BY-NC-SA – ok, da gemeinnütziger Träger, kein kommerzieller Vertrieb).
         * Weitere Testkandidaten: siehe scripts/download-models.sh.
         */
        val AVAILABLE_VOICES = listOf(
            "de_DE-dii-high",        // 1 – Default (weiblich klingend, high)
        )
    }
}
