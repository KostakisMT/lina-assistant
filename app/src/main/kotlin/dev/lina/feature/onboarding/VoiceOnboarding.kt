package dev.lina.feature.onboarding

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lina.core.audio.Earcons
import dev.lina.core.audio.WavRecorder
import dev.lina.core.stt.WhisperSttEngine
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import org.json.JSONObject
import java.io.File

/**
 * Gesprochene Ersteinrichtung beim ersten Start (oder per Befehl "Einrichtung"):
 *
 * 1. Weckwort-Aufnahmen: Nutzer:in sagt mehrmals "Hey Lina" (→ Nachtraining)
 * 2. Befehls-Aufnahmen: einige Kernbefehle einsprechen (→ STT-Robustheit prüfen)
 * 3. Fragenkatalog: Lina stellt Fragen, Whisper transkribiert die Antworten
 *    (→ Anrede + Interessen fließen in die Claude-Persona ein)
 *
 * Alle Dateien landen in getExternalFilesDir("onboarding")/<session>/ und sind
 * per adb pull abholbar (siehe scripts/remote.sh). Der Ablauf ist komplett
 * gesprochen – keine sehende Hilfe nötig.
 */
class VoiceOnboarding(
    private val tts: TtsEngine,
    private val stt: WhisperSttEngine?,
    private val isTtsSpeaking: () -> Boolean,
    baseDir: File,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val dir = File(baseDir, "session_${System.currentTimeMillis()}").apply { mkdirs() }
    private val answers = JSONObject()
    @Volatile private var cancelled = false

    /**
     * Startet den Ablauf. [onFinished] liefert die gesammelten Interessen
     * (für die Claude-Persona) und die gewünschte Anrede – beides kann leer sein.
     */
    fun start(onFinished: (interests: String, name: String) -> Unit) {
        speakThen(
            "Hallo! Ich bin Lina, deine Sprachassistentin. Bevor es losgeht, " +
                "lernen wir uns kurz kennen. Das dauert etwa fünf Minuten. " +
                "Zuerst übe ich, deine Stimme zu erkennen. " +
                "Ich sage dir immer genau, was zu tun ist. " +
                "Wenn du einen hellen Ton hörst, sprichst du – und wartest dann kurz."
        ) { wakeRound(1, onFinished) }
    }

    fun cancel() {
        cancelled = true
        stt?.stopListening()
    }

    // ── Phase 1: Weckwort ────────────────────────────────────────────────

    private fun wakeRound(round: Int, onFinished: (String, String) -> Unit) {
        if (cancelled) return
        if (round > WAKE_ROUNDS) {
            speakThen("Sehr gut, das reicht. Jetzt üben wir ein paar Befehle.") {
                commandRound(0, onFinished)
            }
            return
        }
        val prompt = when (round) {
            1 -> "Sag jetzt bitte nach dem Ton, laut und deutlich: Hey Lina."
            else -> "Und noch einmal: Hey Lina."
        }
        speakThen(prompt) {
            recordClip("wake_%02d.wav".format(round), WAKE_CLIP_MS) {
                wakeRound(round + 1, onFinished)
            }
        }
    }

    // ── Phase 2: Befehle ─────────────────────────────────────────────────

    private fun commandRound(index: Int, onFinished: (String, String) -> Unit) {
        if (cancelled) return
        if (index >= COMMANDS.size) {
            speakThen(
                "Prima. Zum Schluss habe ich noch ein paar Fragen an dich, " +
                    "damit ich dich besser kennenlerne. Antworte einfach nach dem Ton."
            ) { question(0, onFinished) }
            return
        }
        speakThen("Sprich mir nach dem Ton bitte nach: ${COMMANDS[index]}") {
            recordClip("cmd_%02d.wav".format(index + 1), CMD_CLIP_MS) {
                commandRound(index + 1, onFinished)
            }
        }
    }

    // ── Phase 3: Fragenkatalog ───────────────────────────────────────────

    private fun question(index: Int, onFinished: (String, String) -> Unit) {
        if (cancelled) return
        if (index >= QUESTIONS.size || stt == null) {
            finish(onFinished)
            return
        }
        val (key, text) = QUESTIONS[index]
        speakThen(text) {
            Earcons.go()
            var handled = false
            val timeout = Runnable {
                if (!handled) {
                    handled = true
                    stt.stopListening()
                    answers.put(key, "")
                    question(index + 1, onFinished)
                }
            }
            handler.postDelayed(timeout, ANSWER_TIMEOUT_MS)
            handler.postDelayed({
                if (handled) return@postDelayed
                stt.startListening { result ->
                    handler.post {
                        if (handled) return@post
                        handled = true
                        handler.removeCallbacks(timeout)
                        answers.put(key, result)
                        Log.d(TAG, "Antwort [$key]: \"$result\"")
                        question(index + 1, onFinished)
                    }
                }
            }, 300)
        }
    }

    // ── Abschluss ────────────────────────────────────────────────────────

    private fun finish(onFinished: (String, String) -> Unit) {
        File(dir, "answers.json").writeText(answers.toString(2))
        val name = answers.optString("anrede").trim()
        val interests = listOf("nachrichten_interessen", "buecher")
            .map { answers.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
        val greeting = if (name.isNotEmpty()) ", $name" else ""
        speakThen(
            "Danke$greeting! Die Einrichtung ist fertig. " +
                "Ab jetzt sage einfach: Hey Lina – und dann, was du möchtest. " +
                "Zum Beispiel: Hey Lina, was gibt es Neues?"
        ) { onFinished(interests, name) }
        Log.d(TAG, "Onboarding fertig: ${dir.absolutePath}, Interessen=\"$interests\"")
    }

    // ── Helfer ───────────────────────────────────────────────────────────

    /** TTS sprechen, aufs Ende warten (Polling), kleine Pause, dann weiter. */
    private fun speakThen(text: String, then: () -> Unit) {
        tts.speak(text, TtsPriority.INTERRUPT)
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (cancelled) return
                if (isTtsSpeaking()) {
                    handler.postDelayed(this, 200)
                } else {
                    handler.postDelayed({ if (!cancelled) then() }, PAUSE_AFTER_TTS_MS)
                }
            }
        }, 1200)
    }

    /** Signalton, dann feste Dauer aufnehmen, dann weiter (auf Main-Thread). */
    private fun recordClip(name: String, ms: Int, then: () -> Unit) {
        Earcons.go()
        handler.postDelayed({
            Thread({
                val ok = WavRecorder.record(ms, File(dir, name))
                if (!ok) Log.w(TAG, "Aufnahme $name fehlgeschlagen")
                handler.post { if (!cancelled) then() }
            }, "onboarding-rec").start()
        }, GO_TONE_LEAD_MS)
    }

    companion object {
        private const val TAG = "VoiceOnboarding"
        private const val WAKE_ROUNDS = 5
        private const val WAKE_CLIP_MS = 3000
        private const val CMD_CLIP_MS = 4000
        private const val ANSWER_TIMEOUT_MS = 15_000L
        private const val PAUSE_AFTER_TTS_MS = 500L
        private const val GO_TONE_LEAD_MS = 400L

        private val COMMANDS = listOf(
            "Ruf Boris an.",
            "Lies meine Nachrichten vor.",
            "Was gibt es Neues?",
            "Spiel mein Hörbuch ab.",
            "Stopp.",
        )

        /** Schlüssel → gesprochene Frage. Antworten landen in answers.json. */
        private val QUESTIONS = listOf(
            "anrede" to "Wie möchtest du von mir angesprochen werden?",
            "nachrichten_interessen" to
                "Welche Themen interessieren dich in den Nachrichten am meisten?",
            "buecher" to "Welche Bücher, Autoren oder Themen hörst oder liest du gern?",
            "wichtigste_person" to
                "Wen möchtest du am häufigsten anrufen? Und wie nennst du diese Person meistens?",
            "wunsch" to "Und zum Schluss: Was soll ich für dich besonders gut können?",
        )
    }
}
