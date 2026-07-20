package dev.lina.core.text

/**
 * Deutsche Zahlwörter für gesprochene Eingaben ("kapitel drei",
 * "in zwanzig minuten"). Gemeinsam genutzt von GermanTimeParser und
 * LocalCommandResolver, damit beide dieselben Wörter verstehen.
 */
object GermanNumbers {

    val WORDS: Map<String, Long> = mapOf(
        "eins" to 1L, "eine" to 1L, "einer" to 1L, "ein" to 1L, "zwei" to 2L,
        "drei" to 3L, "vier" to 4L, "fünf" to 5L, "sechs" to 6L, "sieben" to 7L,
        "acht" to 8L, "neun" to 9L, "zehn" to 10L, "elf" to 11L, "zwölf" to 12L,
        "dreizehn" to 13L, "vierzehn" to 14L, "fünfzehn" to 15L, "sechzehn" to 16L,
        "siebzehn" to 17L, "achtzehn" to 18L, "neunzehn" to 19L, "zwanzig" to 20L,
        "dreißig" to 30L, "vierzig" to 40L, "fünfzig" to 50L, "sechzig" to 60L,
    )

    /**
     * Alternation für Regex-Einbettung, absteigend nach Länge sortiert.
     * Ohne die Sortierung würde "ein" schon in "eins" greifen und die
     * Erkennung abschneiden – die Reihenfolge in [WORDS] darf dadurch
     * beliebig sein.
     */
    val ALTERNATION: String = WORDS.keys.sortedByDescending { it.length }.joinToString("|")

    /** Ziffernfolge oder Zahlwort → Zahl, sonst null. */
    fun parse(word: String): Long? {
        val w = word.trim().lowercase()
        w.toLongOrNull()?.let { return it }
        return WORDS[w]
    }
}
