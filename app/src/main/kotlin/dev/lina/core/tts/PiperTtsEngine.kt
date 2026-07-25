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
    /** Signalisiert dem Sprech-Worker, die laufende Äußerung sofort abzubrechen. */
    @Volatile private var stopRequested = false

    /**
     * true solange Lina spricht, Ansagen anstehen oder die Wiedergabe gerade
     * erst endete (Weckwort-Erkennung hinkt ~1s hinterher – Echo-Unterdrückung).
     */
    override fun isSpeaking(): Boolean =
        playing || queue.isNotEmpty() ||
            System.currentTimeMillis() - lastPlaybackEnd < ECHO_GUARD_MS

    /**
     * Wie [isSpeaking], aber ohne den 2s-Echo-Nachlauf – für Folgefenster
     * (Gespräch/Nachrichten/Onboarding), die direkt nach dem letzten Wort
     * weitermachen sollen. Der Nachlauf ist nur für die Weckwort-Erkennung da.
     */
    fun isBusySpeaking(): Boolean = playing || queue.isNotEmpty()

    private data class QueueItem(
        val text: String,
        val priority: TtsPriority,
        val onDone: (() -> Unit)? = null,
    )

    private val queue = LinkedBlockingDeque<QueueItem>()
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

    override fun speak(text: String, priority: TtsPriority, onDone: (() -> Unit)?) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        if (shuttingDown) {
            // Engine ist heruntergefahren (z.B. eine bereits zerstörte Activity-
            // Instanz spricht noch nach) – stiller Verlust in einer toten Queue
            // wäre schlimmer als der fehlende Ton: sichtbar loggen und verwerfen.
            Log.w(TAG, "speak() nach shutdown() ignoriert: \"${text.take(60)}\"")
            onDone?.invoke()
            return
        }
        val item = QueueItem(text, priority, onDone)
        if (priority == TtsPriority.INTERRUPT) {
            queue.clear()
            stopPlayback()
            queue.offerFirst(item)
        } else {
            queue.offer(item)
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
                val item = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    break
                }
                // Ab Entnahme gilt "spricht": sonst meldet isSpeaking() während
                // der Synthese (0.5–3s, Queue leer, noch keine Wiedergabe) fälschlich
                // Stille – und der Gesprächsmodus nimmt Linas eigene Antwort auf
                playing = true
                stopRequested = false
                try {
                    synthesizeAndPlay(item.text)
                } catch (e: Exception) {
                    Log.e(TAG, "Sprachausgabe fehlgeschlagen: \"${item.text}\"", e)
                } finally {
                    playing = false
                    lastPlaybackEnd = System.currentTimeMillis()
                    item.onDone?.invoke()
                }
            }
        }, "piper-speak").apply { start() }
    }

    /**
     * Spricht einen Text. Lange Texte (vorgelesene Briefe, ausführliche
     * Nachrichten) werden in Sätze zerlegt und Stück für Stück synthetisiert:
     * ein einzelner generate()-Aufruf über sehr langen Text kann minutenlang
     * blockieren und friert damit die ganze Sprachschleife ein. Chunking hält
     * jeden Aufruf kurz, lässt Audio früher beginnen und macht "Stopp" wirksam.
     */
    private fun synthesizeAndPlay(text: String) {
        for (chunk in splitIntoChunks(text)) {
            if (shuttingDown || stopRequested) break
            synthesizeAndPlayChunk(chunk)
        }
    }

    private fun synthesizeAndPlayChunk(chunk: String) {
        val t0 = System.currentTimeMillis()
        val audio = synchronized(engineLock) {
            val engine = tts ?: return
            engine.generate(text = chunk, sid = 0, speed = rate)
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
            track.play()
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            // Blocking write kehrt zurück, sobald der Puffer geschrieben ist –
            // kurz warten bis der Rest gespielt ist
            track.stop()
        } catch (_: IllegalStateException) {
            // stopPlayback() hat den Track parallel freigegeben
        } finally {
            synchronized(this) {
                if (audioTrack === track) audioTrack = null
            }
            track.release()
        }
    }

    /**
     * Zerlegt langen Text in sprechbare Stücke: zuerst an Satzenden, dann
     * greedy auf höchstens [max] Zeichen gepackt; überlange Sätze werden an
     * Wortgrenzen hart umbrochen. Kurze Texte bleiben ein einziges Stück.
     */
    private fun splitIntoChunks(text: String, max: Int = 240): List<String> {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        if (normalized.length <= max) return listOf(normalized)

        val sentences = Regex("""[^.!?…]*[.!?…]+|[^.!?…]+$""")
            .findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val chunks = ArrayList<String>()
        val sb = StringBuilder()
        for (sentence in sentences) {
            val pieces = if (sentence.length <= max) listOf(sentence) else hardWrap(sentence, max)
            for (piece in pieces) {
                if (sb.isNotEmpty() && sb.length + 1 + piece.length > max) {
                    chunks.add(sb.toString())
                    sb.setLength(0)
                }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(piece)
            }
        }
        if (sb.isNotEmpty()) chunks.add(sb.toString())
        return chunks
    }

    /** Bricht einen überlangen Satz an Wortgrenzen (Fallback: hart) auf ≤ max. */
    private fun hardWrap(sentence: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var start = 0
        while (start < sentence.length) {
            var end = minOf(start + max, sentence.length)
            if (end < sentence.length) {
                val space = sentence.lastIndexOf(' ', end)
                if (space > start) end = space
            }
            out.add(sentence.substring(start, end).trim())
            start = end
        }
        return out.filter { it.isNotEmpty() }
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
        stopRequested = true
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
            "de_DE-dii-high",                   // 1 – Default (weiblich klingend, high)
            "de_DE-thorsten_emotional-medium",  // 2 – Thorsten "fröhlich" (amused = sid 0, CC0)
        )
    }
}
