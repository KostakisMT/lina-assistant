package dev.lina.core.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class OpenWakeWordEngine(
    private val context: Context,
    private val modelName: String = "hey_lina_v1.onnx",
    private val threshold: Float = 0.3f,
    private val patienceCount: Int = 2,
) : WakeWordEngine {

    private val ortEnv = OrtEnvironment.getEnvironment()
    private var melSession: OrtSession? = null
    private var embSession: OrtSession? = null
    private var wakeSession: OrtSession? = null
    private var audioRecord: AudioRecord? = null
    private var listenThread: Thread? = null
    @Volatile private var running = false

    private val melBuffer = ArrayDeque<FloatArray>()
    private val embBuffer = ArrayDeque<FloatArray>()
    private var consecutiveDetections = 0

    override fun start(onDetected: () -> Unit) {
        if (running) return
        loadModels()
        running = true
        listenThread = Thread({
            runListenLoop(onDetected)
        }, "oww-listen").apply { start() }
    }

    override fun stop() {
        running = false
        listenThread?.join(2000)
        listenThread = null
        stopAudioRecord()
    }

    override fun destroy() {
        stop()
        melSession?.close()
        embSession?.close()
        wakeSession?.close()
        melSession = null
        embSession = null
        wakeSession = null
    }

    private fun loadModels() {
        if (melSession != null) return
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        melSession = ortEnv.createSession(loadAsset("openwakeword/melspectrogram.onnx"), opts)
        embSession = ortEnv.createSession(loadAsset("openwakeword/embedding_model.onnx"), opts)
        wakeSession = ortEnv.createSession(loadAsset("openwakeword/$modelName"), opts)
    }

    private fun loadAsset(name: String): ByteArray {
        return context.assets.open(name).use { it.readBytes() }
    }

    private fun runListenLoop(onDetected: () -> Unit) {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING),
            FRAME_SAMPLES * 2
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
        ).also { it.startRecording() }

        val audioFrame = ShortArray(FRAME_SAMPLES)
        melBuffer.clear()
        embBuffer.clear()
        consecutiveDetections = 0

        var frameCount = 0
        var maxScore = -1f
        var maxAmp = 0
        try {
            while (running) {
            val read = audioRecord!!.read(audioFrame, 0, FRAME_SAMPLES)
            if (read != FRAME_SAMPLES) continue

            for (s in audioFrame) {
                val a = if (s < 0) -s.toInt() else s.toInt()
                if (a > maxAmp) maxAmp = a
            }
            if (++frameCount % 25 == 0) { // ~alle 2s
                android.util.Log.d("OpenWakeWord", "amp=$maxAmp maxScore=$maxScore melBuf=${melBuffer.size} embBuf=${embBuffer.size}")
                maxScore = -1f
                maxAmp = 0
            }

            val melFrames = computeMelspectrogram(audioFrame)
            for (frame in melFrames) {
                melBuffer.addLast(frame)
            }
            while (melBuffer.size > MAX_MEL_FRAMES) melBuffer.removeFirst()

            if (melBuffer.size < EMB_WINDOW_SIZE) continue

            val embedding = computeEmbedding()
            embBuffer.addLast(embedding)
            while (embBuffer.size > MAX_EMB_FRAMES) embBuffer.removeFirst()

            if (embBuffer.size < WAKE_INPUT_FRAMES) continue

            val score = computeWakeWordScore()
            if (score > maxScore) maxScore = score
            if (score >= threshold) {
                consecutiveDetections++
                if (consecutiveDetections >= patienceCount) {
                    consecutiveDetections = 0
                    embBuffer.clear()
                    onDetected()
                }
            } else {
                consecutiveDetections = 0
            }
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenWakeWord", "Listen-Loop abgebrochen", e)
        } finally {
            stopAudioRecord()
        }
    }

    private fun computeMelspectrogram(audio: ShortArray): List<FloatArray> {
        val floatAudio = FloatArray(audio.size) { audio[it] / 32768f }
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(floatAudio),
            longArrayOf(1, floatAudio.size.toLong())
        )
        val result = melSession!!.run(mapOf("input" to inputTensor))
        inputTensor.close()

        val outputTensor = result[0] as OnnxTensor
        val shape = outputTensor.info.shape // [1, 1, frames, 32]
        val numFrames = shape[shape.size - 2].toInt()
        val melBins = shape[shape.size - 1].toInt()
        val rawData = outputTensor.floatBuffer

        val frames = mutableListOf<FloatArray>()
        for (i in 0 until numFrames) {
            val frame = FloatArray(melBins)
            rawData.get(frame)
            // OpenWakeWord-Normalisierung vor dem Embedding-Modell
            for (j in frame.indices) frame[j] = frame[j] / 10f + 2f
            frames.add(frame)
        }
        result.close()
        return frames
    }

    private fun computeEmbedding(): FloatArray {
        val windowStart = melBuffer.size - EMB_WINDOW_SIZE
        val inputData = FloatArray(EMB_WINDOW_SIZE * MEL_BINS)
        for (i in 0 until EMB_WINDOW_SIZE) {
            val frame = melBuffer.elementAt(windowStart + i)
            System.arraycopy(frame, 0, inputData, i * MEL_BINS, MEL_BINS)
        }

        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, EMB_WINDOW_SIZE.toLong(), MEL_BINS.toLong(), 1)
        )
        val result = embSession!!.run(mapOf("input_1" to inputTensor))
        inputTensor.close()

        val outputTensor = result[0] as OnnxTensor
        val embedding = FloatArray(EMB_DIM)
        outputTensor.floatBuffer.get(embedding)
        result.close()
        return embedding
    }

    private fun computeWakeWordScore(): Float {
        val startIdx = embBuffer.size - WAKE_INPUT_FRAMES
        val inputData = FloatArray(WAKE_INPUT_FRAMES * EMB_DIM)
        for (i in 0 until WAKE_INPUT_FRAMES) {
            val emb = embBuffer.elementAt(startIdx + i)
            System.arraycopy(emb, 0, inputData, i * EMB_DIM, EMB_DIM)
        }

        val inputName = wakeSession!!.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(inputData),
            longArrayOf(1, WAKE_INPUT_FRAMES.toLong(), EMB_DIM.toLong())
        )
        val result = wakeSession!!.run(mapOf(inputName to inputTensor))
        inputTensor.close()

        val outputTensor = result[0] as OnnxTensor
        val score = outputTensor.floatBuffer.get()
        result.close()
        return score
    }

    private fun stopAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SAMPLES = 1280 // 80ms
        private const val MEL_BINS = 32
        private const val EMB_WINDOW_SIZE = 76 // ~760ms
        private const val EMB_DIM = 96
        private const val WAKE_INPUT_FRAMES = 16
        private const val MAX_MEL_FRAMES = 970 // ~10s
        private const val MAX_EMB_FRAMES = 120 // ~10s
    }
}
