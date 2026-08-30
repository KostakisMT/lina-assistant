package dev.lina.core.contacts

/**
 * Erkennt Nummern, bei denen Lina vor dem Wählen nachfragen muss.
 *
 * Hintergrund (Klientenbesuch 2026-08-30): Der Import einer echten
 * Vodafone-SIM brachte 21 Einträge ins Telefonbuch, davon **keinen einzigen
 * persönlichen** – nur Diensteinträge des Anbieters, 13 davon mit 199ct/Min
 * ("Tarot, Kartenlegen", "Horoskop", "PartnerschaftLiebe", "Auskunft 11880").
 *
 * Das Risiko ist die Kette, nicht der einzelne Eintrag: Whisper verhört bei
 * Raumdistanz Eigennamen (am Gerät belegt: "Tolstoi" → "Teustol"), das
 * Fuzzy-Matching landet auf "Tarot" oder "Auskunft", Lina wählt – und der
 * blinde Nutzer **sieht nicht, wen er anruft**. Er merkt es frühestens auf der
 * Rechnung.
 *
 * Diese Prüfung sitzt bewusst am ANRUF und nicht am Import: So wirkt sie
 * unabhängig davon, wie eine Nummer ins Telefonbuch gekommen ist – auch über
 * den Google-Konto-Sync während der Android-Ersteinrichtung, an dem gar kein
 * Lina-Code beteiligt ist. Ein reiner Importfilter ließe genau diese Tür offen.
 *
 * Bewusst konservativ bei den Notrufnummern: die dürfen NIE eine Rückfrage
 * auslösen. Wer 112 wählt, hat keine Zeit für ein Bestätigungsgespräch.
 */
object PhoneNumberRisk {

    enum class Category {
        /** Notruf – wird sofort gewählt, niemals nachgefragt. */
        EMERGENCY,

        /** Teure Sondernummer (0900, 0137/0138, Auskunft 118xx). */
        PREMIUM,

        /** Servicenummer mit Sondertarif (0180, 0181). */
        SERVICE,

        /** Kurzwahl des Anbieters – Tarif für den Nutzer nicht erkennbar. */
        SHORT_CODE,

        /** Normale Rufnummer. */
        NORMAL,
        ;

        /** Ob Lina vor dem Wählen bestätigen lassen muss. */
        fun needsConfirmation(): Boolean =
            this == PREMIUM || this == SERVICE || this == SHORT_CODE
    }

    /** Notrufe und notrufähnliche Kurznummern – niemals blockieren. */
    private val EMERGENCY = setOf(
        "110",     // Polizei
        "112",     // Feuerwehr/Rettung
        "116117",  // Ärztlicher Bereitschaftsdienst
        "116116",  // Sperr-Notruf (Karten)
        "19222",   // Krankentransport
    )

    private val PREMIUM_PREFIXES = listOf(
        "0900",  // Premium-Dienste
        "0137",  // Massenverkehrsdienste (Televoting)
        "0138",  // Massenverkehrsdienste
        "118",   // Auskunftsdienste (11880, 11833, 11890 …)
    )

    private val SERVICE_PREFIXES = listOf(
        "0180",  // Service-Dienste, geteilte Kosten
        "0181",  // internationale VPN
    )

    /** Ab dieser Länge gilt eine Nummer nicht mehr als Kurzwahl. */
    private const val SHORT_CODE_MAX_LENGTH = 6

    /**
     * Bringt eine Nummer in nationale Schreibweise mit führender Null, damit
     * die Präfixe unten greifen. Nicht-deutsche Auslandsnummern bleiben
     * unverändert – deren Tarifstruktur lässt sich hier nicht beurteilen,
     * und lieber nicht nachfragen als falsch nachfragen.
     */
    private fun toNational(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '+' }
        val cleaned = when {
            digits.startsWith("+49") -> "0" + digits.removePrefix("+49")
            digits.startsWith("0049") -> "0" + digits.removePrefix("0049")
            digits.startsWith("+") -> return null       // anderes Land
            digits.startsWith("00") -> return null      // anderes Land
            else -> digits
        }
        return cleaned.filter { it.isDigit() }
    }

    fun classify(raw: String?): Category {
        val number = raw?.filter { it.isDigit() || it == '+' }.orEmpty()
        if (number.isEmpty()) return Category.NORMAL

        val national = toNational(number) ?: return Category.NORMAL
        if (national.isEmpty()) return Category.NORMAL

        // Notruf zuerst – vor jeder anderen Regel, auch vor der Kurzwahl-Länge.
        if (national in EMERGENCY) return Category.EMERGENCY

        if (PREMIUM_PREFIXES.any { national.startsWith(it) }) return Category.PREMIUM
        if (SERVICE_PREFIXES.any { national.startsWith(it) }) return Category.SERVICE
        if (national.length <= SHORT_CODE_MAX_LENGTH) return Category.SHORT_CODE

        return Category.NORMAL
    }

    /**
     * Gesprochene Rückfrage. Bewusst ohne Cent-Angaben: die stimmen selten und
     * veralten, und der Nutzer soll die Entscheidung treffen, nicht rechnen.
     */
    fun confirmationPrompt(name: String, category: Category): String = when (category) {
        Category.PREMIUM ->
            "$name ist eine teure Sondernummer. Soll ich wirklich anrufen?"
        Category.SERVICE ->
            "$name ist eine Servicenummer mit Sondertarif. Soll ich wirklich anrufen?"
        Category.SHORT_CODE ->
            "$name ist eine Kurzwahlnummer des Anbieters. Soll ich wirklich anrufen?"
        Category.EMERGENCY, Category.NORMAL ->
            "Soll ich $name anrufen?"
    }
}
