package dev.lina.core.stt

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File

class VoskSttEngine(private val context: Context) : SttEngine {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var initialized = false

    fun initialize(onReady: () -> Unit, onError: (Exception) -> Unit) {
        try {
            val modelDir = copyModelFromAssets()
            model = Model(modelDir.absolutePath)
            initialized = true
            onReady()
        } catch (e: Exception) {
            onError(e)
        }
    }

    override fun startListening(onResult: (String) -> Unit) {
        val m = model ?: return
        val recognizer = Recognizer(m, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f).apply {
            startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}

                override fun onResult(hypothesis: String?) {
                    val text = hypothesis?.let {
                        JSONObject(it).optString("text", "")
                    } ?: ""
                    android.util.Log.d("VoskStt", "onResult: \"$text\"")
                    if (text.isNotBlank()) {
                        stopListening()
                        onResult(text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = hypothesis?.let {
                        JSONObject(it).optString("text", "")
                    } ?: ""
                    if (text.isNotBlank()) {
                        onResult(text)
                    }
                }

                override fun onError(exception: Exception?) {
                    android.util.Log.e("VoskStt", "Erkennungsfehler", exception)
                }
                override fun onTimeout() {
                    android.util.Log.w("VoskStt", "Timeout ohne Ergebnis")
                }
            })
        }
    }

    override fun stopListening() {
        speechService?.stop()
        speechService = null
    }

    override fun destroy() {
        stopListening()
        model?.close()
        model = null
        initialized = false
    }

    private fun copyModelFromAssets(): File {
        val targetDir = File(context.filesDir, "vosk-model-small-de")
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            return targetDir
        }
        targetDir.mkdirs()
        copyAssetDir("vosk-model-small-de", targetDir)
        return targetDir
    }

    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val assets = context.assets.list(assetPath) ?: return
        if (assets.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                File(targetDir, "").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            for (child in assets) {
                val childTarget = File(targetDir, child)
                val childAsset = "$assetPath/$child"
                val subAssets = context.assets.list(childAsset)
                if (subAssets != null && subAssets.isNotEmpty()) {
                    childTarget.mkdirs()
                    copyAssetDir(childAsset, childTarget)
                } else {
                    context.assets.open(childAsset).use { input ->
                        childTarget.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
