package dev.lina.core.stt

/**
 * Verwirft Transkripte, die erkennbar keine Nutzeräußerung sind.
 *
 * Whisper ist auf Untertitel-Korpora trainiert und erzeugt auf Nicht-Sprache
 * reproduzierbar Untertitel-Artefakte statt eines leeren Ergebnisses. Am
 * Testtablet gemessen (2026-08-30): "* Erinnungsvolle Musik *" aus 5,5 s
 * stillem Raum. Ohne Filter geht so etwas denselben Weg wie ein echter Befehl
 * – im Gesprächsfenster also direkt an die Claude-API.
 *
 * [SpeechDetector] setzt akustisch an und fängt den Großteil ab; dieser Filter
 * ist die zweite Reihe für Clips, die genug Energie hatten (Musik im Raum,
 * Fernseher, Gespräch von nebenan), aber trotzdem kein Befehl sind.
 *
 * Bewusst eng gehalten: lieber ein Artefakt durchlassen als eine echte
 * Äußerung verschlucken. Deshalb nur (a) Texte, die vollständig in Klammern
 * oder Sternchen stehen – die Untertitel-Notation für Geräusche – und (b) eine
 * kurze Liste wörtlicher Abspann-Floskeln, die niemand an Lina richtet.
 */
object TranscriptPlausibility {

    /**
     * Wörtliche Whisper-Halluzinationen aus deutschen Untertitel-Daten.
     * Alle stammen aus Abspann-/Kanalhinweisen und kommen als Befehl an eine
     * Sprachassistentin praktisch nicht vor.
     */
    private val ARTIFACT_MARKERS = listOf(
        "amara.org",
        "untertitel",
        "untertitelung",
        "fürs zuschauen",
        "für's zuschauen",
        "abonniert",
        "abonniere",
    )

    /** Ganzer Text steht in Klammern/Sternchen: "[Musik]", "(Applaus)", "* Musik *". */
    private val FULLY_BRACKETED = Regex("""^[*\[(<♪~_-].*[*\])>♪~_-]$""", RegexOption.DOT_MATCHES_ALL)

    /** Mindestens ein Buchstabe – reine Satzzeichen/Ziffern sind kein Befehl. */
    private val HAS_LETTER = Regex("""\p{L}""")

    /**
     * `true`, wenn der Text als echte Nutzeräußerung behandelt werden darf.
     * Leerer Text gilt als nicht plausibel (Aufrufer behandeln ihn ohnehin
     * schon als "nichts verstanden").
     */
    fun isPlausible(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (!HAS_LETTER.containsMatchIn(text)) return false
        if (FULLY_BRACKETED.matches(text)) return false

        val lower = text.lowercase()
        if (ARTIFACT_MARKERS.any { lower.contains(it) }) return false

        return true
    }

    /** Bequemlichkeit für Aufrufer, die den Text direkt weiterreichen. */
    fun filter(raw: String?): String = if (isPlausible(raw)) raw!!.trim() else ""
}
