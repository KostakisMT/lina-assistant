package dev.lina.core.contacts

/**
 * Vergleicht Telefonnummern trotz unterschiedlicher Schreibweisen (Leerzeichen,
 * Landesvorwahl, führende Null). Vergleicht bewusst nur die letzten Ziffern
 * statt eine vollständige Landesvorwahl-Normalisierung zu versuchen – robuster
 * gegen die vielen Formatvarianten, die auf SIM-Karten und in vCard-Exporten
 * vorkommen.
 */
object PhoneNumberNormalizer {

    private const val COMPARE_SUFFIX_LENGTH = 8
    private const val MIN_LENGTH_FOR_MATCH = 5

    fun normalize(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Zwei Nummern gelten als gleich, wenn ihre letzten [COMPARE_SUFFIX_LENGTH]
     * Ziffern übereinstimmen. Kürzere Nummern (Kurzwahl, Notruf) werden nur bei
     * exakter Übereinstimmung als gleich gewertet, um Kurznummern-Fehltreffer
     * zu vermeiden (z.B. "112" darf nicht zufällig mit "...112" matchen).
     */
    fun matches(a: String, b: String): Boolean {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA.length < MIN_LENGTH_FOR_MATCH || normB.length < MIN_LENGTH_FOR_MATCH) {
            return normA == normB
        }
        val suffixA = normA.takeLast(COMPARE_SUFFIX_LENGTH)
        val suffixB = normB.takeLast(COMPARE_SUFFIX_LENGTH)
        return suffixA == suffixB
    }
}
