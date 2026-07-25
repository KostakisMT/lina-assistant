package dev.lina.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.LinkedList
import java.util.UUID

class AndroidTtsEngine(context: Context) : TtsEngine, TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val queue = LinkedList<Triple<String, TtsPriority, (() -> Unit)?>>()
    private val pendingCallbacks = mutableMapOf<String, () -> Unit>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("de", "DE")
            tts.setSpeechRate(0.9f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = fireCallback(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = fireCallback(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = fireCallback(utteranceId)
            })
            ready = true
            drainQueue()
        }
    }

    private fun fireCallback(utteranceId: String?) {
        utteranceId ?: return
        pendingCallbacks.remove(utteranceId)?.invoke()
    }

    override fun speak(text: String, priority: TtsPriority, onDone: (() -> Unit)?) {
        if (!ready) {
            queue.add(Triple(text, priority, onDone))
            return
        }
        val queueMode = if (priority == TtsPriority.INTERRUPT) {
            tts.stop()
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        val id = UUID.randomUUID().toString()
        if (onDone != null) pendingCallbacks[id] = onDone
        tts.speak(text, queueMode, null, id)
    }

    override fun stop() {
        tts.stop()
    }

    override fun setRate(rate: Float) {
        tts.setSpeechRate(rate)
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun drainQueue() {
        while (queue.isNotEmpty()) {
            val (text, priority, onDone) = queue.poll() ?: break
            speak(text, priority, onDone)
        }
    }
}
