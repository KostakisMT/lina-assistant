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
    private val queue = LinkedList<Pair<String, TtsPriority>>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("de", "DE")
            tts.setSpeechRate(0.9f)
            ready = true
            drainQueue()
        }
    }

    override fun speak(text: String, priority: TtsPriority) {
        if (!ready) {
            queue.add(text to priority)
            return
        }
        val queueMode = if (priority == TtsPriority.INTERRUPT) {
            tts.stop()
            TextToSpeech.QUEUE_FLUSH
        } else {
            TextToSpeech.QUEUE_ADD
        }
        tts.speak(text, queueMode, null, UUID.randomUUID().toString())
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
            val (text, priority) = queue.poll() ?: break
            speak(text, priority)
        }
    }
}
