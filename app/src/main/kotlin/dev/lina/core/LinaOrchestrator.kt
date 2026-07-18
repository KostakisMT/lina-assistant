package dev.lina.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.lina.core.intent.IntentResolver
import dev.lina.core.intent.ResolvedIntent
import dev.lina.core.stt.SttEngine
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import dev.lina.core.wakeword.WakeWordService

class LinaOrchestrator(
    private val context: Context,
    private val ttsEngine: TtsEngine,
    private val sttEngine: SttEngine,
    private val intentResolver: IntentResolver,
    private val onIntent: (ResolvedIntent) -> Unit,
) {

    private var listening = false

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                onWakeWordDetected()
            }
        }
    }

    fun start() {
        context.registerReceiver(
            wakeWordReceiver,
            IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        WakeWordService.start(context)
        ttsEngine.speak("Lina ist bereit.", TtsPriority.HIGH)
    }

    fun stop() {
        try {
            context.unregisterReceiver(wakeWordReceiver)
        } catch (_: Exception) {}
        sttEngine.stopListening()
        ttsEngine.shutdown()
    }

    private fun onWakeWordDetected() {
        if (listening) return
        listening = true
        ttsEngine.speak("Ja?", TtsPriority.INTERRUPT)

        sttEngine.startListening { spokenText ->
            listening = false
            val resolved = intentResolver.resolve(spokenText)
                ?: ResolvedIntent.Unknown(spokenText)
            onIntent(resolved)
        }
    }
}
