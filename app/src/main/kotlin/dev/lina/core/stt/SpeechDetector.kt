package dev.lina.core.stt

/**
 * Entscheidet frameweise, ob eine laufende Aufnahme weiterlaufen, wegen
 * Sprachende enden oder als "gar nicht gesprochen" verworfen werden soll.
 * Bewusst frei von Android-Typen, damit die Logik unit-testbar ist –
 * [WhisperSttEngine] selbst braucht einen `Context` und bleibt es nicht.
 *
 * Hintergrund (2026-08-30, am Lenovo-Testtablet gemessen): die Vorgänger-
 * fassung setzte `speechStarted` schon nach EINEM 100-ms-Frame über der
 * Schwelle und nahm es nie zurück. Weil die Notbremse für "hat gar nicht
 * gesprochen" im `else if`-Zweig dahinter hing, war sie danach unerreichbar.
 * Ein einzelnes Klacken genügte also, und Whisper bekam bis zu 10 s reines
 * Raumrauschen zu hören. Whisper base ist auf Untertitel-Korpora trainiert
 * und antwortet auf Nicht-Sprache mit Untertitel-Artefakten – gemessen kam
 * "* Erinnungsvolle Musik *" heraus und ging ungefiltert als Befehl an die
 * Claude-API. Von zwei echten Mikrofonöffnungen in dem Testlauf lieferten
 * beide Geistereingaben.
 *
 * Zwei Gegenmaßnahmen, beide hier:
 *  1. Der Sprachbeginn rastet erst nach [SPEECH_ONSET_MS] **zusammenhängender**
 *     lauter Frames ein – ein Klacken (ein Frame) reicht nicht mehr.
 *  2. Die Notbremse wird unabhängig geprüft, nicht mehr hinter `speechStarted`
 *     versteckt; zusätzlich muss ein fertiger Clip insgesamt mindestens
 *     [MIN_SPEECH_MS] Sprachenergie enthalten ([hasEnoughSpeech]).
 *
 * Die Schwellen sind ein Kompromiss: kurze Bestätigungen ("Ja", "Nein") sollen
 * durchkommen, einzelne Störgeräusche nicht. Falls im Betrieb kurze Antworten
 * verschluckt werden, ist [MIN_SPEECH_MS] die Stellschraube.
 */
class SpeechDetector(
    private val endSilenceMs: Int,
    private val noSpeechTimeoutMs: Int = NO_SPEECH_TIMEOUT_MS,
    private val speechAmpThreshold: Int = SPEECH_AMP_THRESHOLD,
) {

    enum class Decision {
        /** Weiter aufnehmen. */
        CONTINUE,

        /** Sprache erkannt und danach lange genug still – Aufnahme auswerten. */
        STOP_SPEECH_ENDED,

        /** Nie Sprache erkannt – Aufnahme verwerfen, gar nicht erst transkribieren. */
        ABORT_NO_SPEECH,
    }

    /** Sprachbeginn eingerastet (erst nach [SPEECH_ONSET_MS] am Stück). */
    var speechStarted = false
        private set

    /** Summe aller lauten Frames – Grundlage für [hasEnoughSpeech]. */
    var speechMs = 0
        private set

    private var consecutiveSpeechMs = 0
    private var silenceMs = 0
    private var totalMs = 0

    /**
     * Einen aufgenommenen Frame melden. [maxAmp] ist der Spitzenwert des Frames
     * (16-Bit-PCM, also 0..32767), [frameMs] seine Länge in Millisekunden.
     */
    fun offer(maxAmp: Int, frameMs: Int): Decision {
        totalMs += frameMs
        val loud = maxAmp >= speechAmpThreshold

        if (loud) {
            consecutiveSpeechMs += frameMs
            speechMs += frameMs
        } else {
            consecutiveSpeechMs = 0
        }
        if (!speechStarted && consecutiveSpeechMs >= SPEECH_ONSET_MS) {
            speechStarted = true
        }

        return when {
            speechStarted && loud -> {
                silenceMs = 0
                Decision.CONTINUE
            }
            speechStarted -> {
                silenceMs += frameMs
                if (silenceMs >= endSilenceMs) Decision.STOP_SPEECH_ENDED else Decision.CONTINUE
            }
            // Unabhängig geprüft – das war der eigentliche Fehler der Vorfassung.
            totalMs >= noSpeechTimeoutMs -> Decision.ABORT_NO_SPEECH
            else -> Decision.CONTINUE
        }
    }

    /**
     * Ob der fertige Clip genug Sprachenergie enthält, um ihn überhaupt an
     * Whisper zu geben. Fängt den Fall ab, dass der Sprachbeginn zwar
     * einrastete (z.B. Türklacken mit Nachhall), danach aber nichts mehr kam.
     */
    fun hasEnoughSpeech(): Boolean = speechMs >= MIN_SPEECH_MS

    companion object {
        /**
         * ~ -36 dBFS. Am 2026-08-30 von 1000 (-30 dBFS) gesenkt: bei
         * Raumdistanz lag die Stimme des Nutzers regelmäßig darunter, das
         * Einrasten blieb aus und die Aufnahme wurde als "nie gesprochen"
         * verworfen – obwohl das Weckwort sauber mit Score 0.99 gefeuert
         * hatte. Am Gerät beobachtet: dreimal in Folge "Ja?" von Lina, dann
         * Stille, weil die Antwort weggefiltert wurde.
         */
        const val SPEECH_AMP_THRESHOLD = 500

        /**
         * So lange muss es am Stück laut sein, damit "Sprache" einrastet.
         * Das ist die eigentliche Absicherung gegen einzelne Störgeräusche –
         * ein Klacken ist ein Frame, nicht drei am Stück.
         */
        const val SPEECH_ONSET_MS = 300

        /**
         * Mindest-Sprachenergie im fertigen Clip. Bewusst gleich
         * [SPEECH_ONSET_MS]: wer eingerastet ist, hat die Schwelle damit
         * automatisch erreicht.
         *
         * Vorher 400ms – das verwarf kurze, leise Äußerungen NACH dem
         * Einrasten, am Gerät als "Zu wenig Sprachenergie (300ms)" gesehen.
         * Kurze Bestätigungen ("Ja", "Nein", "Stopp") tragen den SIM-Import
         * und die Rückfragen; sie zu verschlucken ist schlimmer als ein
         * gelegentliches Artefakt, das ohnehin noch durch
         * [TranscriptPlausibility] muss. Die Messung vom selben Tag zeigt,
         * dass die Geister-Abwehr fast vollständig am Einrasten hängt:
         * 9 von 10 wurden abgefangen, weil gar nichts einrastete.
         */
        const val MIN_SPEECH_MS = 300

        /** Ohne Sprachbeginn nach dieser Zeit abbrechen. */
        const val NO_SPEECH_TIMEOUT_MS = 5000
    }
}
