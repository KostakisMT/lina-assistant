package dev.lina.core.contacts

data class DedupResult(val new: List<Contact>, val duplicateCount: Int)

/**
 * Trennt neu zu importierende Kontakte von bereits vorhandenen, per
 * normalisierter Telefonnummer. Bewusst still: bei Dutzenden SIM-/vCard-
 * Kontakten ist eine Einzelbestätigung pro Duplikat für einen blinden Nutzer
 * per Sprache nicht praktikabel – eine einzige Zusammenfassung reicht.
 */
object ContactDedup {

    fun partition(existing: List<Contact>, incoming: List<Contact>): DedupResult {
        val new = mutableListOf<Contact>()
        var duplicates = 0
        for (candidate in incoming) {
            val isDuplicate = existing.any { PhoneNumberNormalizer.matches(it.phoneNumber, candidate.phoneNumber) }
            if (isDuplicate) duplicates++ else new.add(candidate)
        }
        return DedupResult(new, duplicates)
    }
}
