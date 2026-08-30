package dev.lina.core.contacts

data class DedupResult(val new: List<Contact>, val duplicateCount: Int)

/**
 * Trennt neu zu importierende Kontakte von bereits vorhandenen, per
 * normalisierter Telefonnummer. Bewusst still: bei Dutzenden SIM-/vCard-
 * Kontakten ist eine Einzelbestätigung pro Duplikat für einen blinden Nutzer
 * per Sprache nicht praktikabel – eine einzige Zusammenfassung reicht.
 */
object ContactDedup {

    /**
     * Teilt [incoming] in wirklich neue Kontakte und Dubletten.
     *
     * Geprüft wird gegen den Bestand UND gegen die bereits akzeptierten
     * Kandidaten desselben Durchlaufs. Letzteres wurde am 2026-08-30
     * nachgezogen: beim Zusammenführen mehrerer Quellen (iCloud + Yahoo auf
     * demselben iPad, siehe scripts/merge-vcards.sh) steht dieselbe Person
     * oft in beiden Exporten. Ohne die zweite Prüfung landen beide Einträge
     * im Telefonbuch – und für einen blind bedienenden Nutzer heißt jede
     * Dublette, dass Lina bei jedem Anruf zurückfragen muss, welcher
     * gemeint ist.
     */
    fun partition(existing: List<Contact>, incoming: List<Contact>): DedupResult {
        val new = mutableListOf<Contact>()
        var duplicates = 0
        for (candidate in incoming) {
            val known = existing.any {
                PhoneNumberNormalizer.matches(it.phoneNumber, candidate.phoneNumber)
            } || new.any {
                PhoneNumberNormalizer.matches(it.phoneNumber, candidate.phoneNumber)
            }
            if (known) duplicates++ else new.add(candidate)
        }
        return DedupResult(new, duplicates)
    }
}
