package dev.lina.feature.onboarding

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.lina.core.audio.Earcons
import dev.lina.core.audio.WavRecorder
import dev.lina.core.stt.SpeechDetector
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
     * (für die Claude-Persona), die gewünschte Anrede und die Wohnregion –
     * alles kann leer sein.
     */
    fun start(onFinished: (interests: String, name: String, region: String) -> Unit) {
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
        restoreSttDefaults()
        stt?.stopListening()
    }

    // ── Phase 1: Weckwort ────────────────────────────────────────────────

    private fun wakeRound(round: Int, onFinished: (String, String, String) -> Unit) {
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

    private fun commandRound(index: Int, onFinished: (String, String, String) -> Unit) {
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

    private fun question(index: Int, onFinished: (String, String, String) -> Unit) {
        if (cancelled) return
        if (index == 0) {
            // Denkzeit VOR dem ersten Wort – sonst verwirft die Sprachlos-
            // Notbremse die Antwort, bevor sie überhaupt beginnt.
            stt?.noSpeechTimeoutMs = ANSWER_NO_SPEECH_TIMEOUT_MS
        }
        if (index >= QUESTIONS.size || stt == null) {
            finish(onFinished)
            return
        }
        val q = QUESTIONS[index]
        val key = q.key
        // Persönliche Fragen bekommen mehr Luft: längere Pause mitten in der
        // Antwort UND eine höhere Obergrenze für die Gesamtdauer.
        stt.endSilenceMs = if (q.openEnded) OPEN_END_SILENCE_MS else ANSWER_END_SILENCE_MS
        stt.maxRecordMs = if (q.openEnded) OPEN_MAX_RECORD_MS else ANSWER_MAX_RECORD_MS
        val answerTimeout = if (q.openEnded) OPEN_TIMEOUT_MS else ANSWER_TIMEOUT_MS
        speakThen(q.text) {
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
            handler.postDelayed(timeout, answerTimeout)
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

    /** Alle drei Aufnahme-Stellschrauben zurück auf den Normalbetrieb. */
    private fun restoreSttDefaults() {
        stt?.endSilenceMs = DEFAULT_END_SILENCE_RESTORE_MS
        stt?.noSpeechTimeoutMs = SpeechDetector.NO_SPEECH_TIMEOUT_MS
        stt?.maxRecordMs = ANSWER_MAX_RECORD_MS
    }

    // ── Abschluss ────────────────────────────────────────────────────────

    private fun finish(onFinished: (String, String, String) -> Unit) {
        restoreSttDefaults()
        File(dir, "answers.json").writeText(answers.toString(2))
        val name = answers.optString("anrede").trim()
        val region = answers.optString("region")
            .trim(' ', '.', '!', ',')
        val interests = listOf("nachrichten_interessen", "buecher")
            .map { answers.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
        val greeting = if (name.isNotEmpty()) ", $name" else ""
        speakThen(
            "Danke$greeting! Die Einrichtung ist fertig. " +
                "Ab jetzt sage einfach: Hey Lina – und dann, was du möchtest. " +
                "Zum Beispiel: Hey Lina, was gibt es Neues?"
        ) { onFinished(interests, name, region) }
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

    /** Eine Frage der Einrichtung samt Antwort-Tempo. */
    private data class Question(
        val key: String,
        val text: String,
        /** Erzählend/persönlich → längere Pausen und höhere Obergrenze. */
        val openEnded: Boolean = false,
    )

    companion object {
        private const val TAG = "VoiceOnboarding"
        private const val WAKE_ROUNDS = 5
        private const val WAKE_CLIP_MS = 3000
        private const val CMD_CLIP_MS = 4000
        private const val ANSWER_TIMEOUT_MS = 15_000L
        private const val ANSWER_MAX_RECORD_MS = 10_000

        // Persönliche Fragen ("Wen rufst du am häufigsten an? Und wie nennst
        // du diese Person?", "Was hörst oder liest du gern?") werden erzählend
        // und oft mehrteilig beantwortet, mit Denkpausen mittendrin. Im
        // Testlauf am 2026-08-30 kamen genau dort die kürzesten Aufnahmen
        // heraus (Bücher 3,8s, wichtigste Person 4,3s) – die Antworten wurden
        // sehr wahrscheinlich abgeschnitten, was zusätzlich die Erkennung
        // verschlechtert. Der Preis ist eine längere Pause, bevor Lina
        // reagiert; das ist in der einmaligen Einrichtung vertretbar.
        private const val OPEN_END_SILENCE_MS = 2500
        private const val OPEN_MAX_RECORD_MS = 20_000
        // Muss über OPEN_MAX_RECORD_MS + Transkriptionsdauer liegen
        // (~0,2x Echtzeit, also ~4s für 20s Audio).
        private const val OPEN_TIMEOUT_MS = 30_000L
        private const val PAUSE_AFTER_TTS_MS = 800L
        // Ältere Nutzer machen Denkpausen mitten in der Antwort – großzügiger
        // als die Standard-Stille-Erkennung (1200ms), sonst wird abgeschnitten
        private const val ANSWER_END_SILENCE_MS = 1800
        // Passend zu ANSWER_TIMEOUT_MS (15s): lieber auf eine Denkpause warten
        // als eine Antwort verwerfen, die gerade erst anfangen wollte.
        private const val ANSWER_NO_SPEECH_TIMEOUT_MS = 12_000
        private const val DEFAULT_END_SILENCE_RESTORE_MS = 1200
        private const val GO_TONE_LEAD_MS = 400L

        private val COMMANDS = listOf(
            "Ruf Boris an.",
            "Lies meine Nachrichten vor.",
            "Was gibt es Neues?",
            "Spiel mein Hörbuch ab.",
            "Stopp.",
        )

        /**
         * Schlüssel → gesprochene Frage. Antworten landen in answers.json.
         * [Question.openEnded] markiert die persönlichen, erzählenden Fragen,
         * die mehr Zeit bekommen – im Gegensatz zu den kurzen Sachfragen
         * (Anrede, Wohnort), die mit einem Wort beantwortet werden.
         */
        private val QUESTIONS = listOf(
            Question("anrede", "Wie möchtest du von mir angesprochen werden?"),
            Question(
                "nachrichten_interessen",
                "Welche Themen interessieren dich in den Nachrichten am meisten?",
                openEnded = true,
            ),
            Question(
                "buecher",
                "Welche Bücher, Autoren oder Themen hörst oder liest du gern?",
                openEnded = true,
            ),
            Question(
                "wichtigste_person",
                "Wen möchtest du am häufigsten anrufen? Und wie nennst du diese Person meistens?",
                openEnded = true,
            ),
            Question(
                "region",
                "In welcher Stadt oder Region wohnst du? Das brauche ich für Wetter und Nachrichten aus deiner Nähe.",
            ),
            Question(
                "wunsch",
                "Und zum Schluss: Was soll ich für dich besonders gut können?",
                openEnded = true,
            ),
        )
    }
}
