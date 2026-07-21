package dev.lina.core.text

/**
 * Buchstabiertafel für gesprochene Codes – gebraucht für die Gerätekopplung
 * (ADR-020): Lina nennt einen Pairing-Code, eine Vertrauensperson tippt ihn
 * auf der Webseite ein. Über Lautsprecher und Telefon sind "B" und "P" oder
 * "D" und "T" kaum zu unterscheiden; "Berta" und "Paula" dagegen schon.
 *
 * Verwendet bewusst die **traditionelle** Tafel (Anton, Berta, Cäsar) statt
 * der DIN 5009:2022 mit Städtenamen (Aachen, Berlin, Chemnitz). Linas
 * Nutzer:innen sind überwiegend ältere Menschen, die die klassischen Namen
 * ihr Leben lang gehört haben – beim Vorlesen zählt sofortiges Verstehen
 * mehr als die aktuelle Normfassung.
 */
object GermanSpelling {

    /** Kleinbuchstabe → Ansagewort. */
    val LETTERS: Map<Char, String> = mapOf(
        'a' to "Anton", 'ä' to "Ärger", 'b' to "Berta", 'c' to "Cäsar",
        'd' to "Dora", 'e' to "Emil", 'f' to "Friedrich", 'g' to "Gustav",
        'h' to "Heinrich", 'i' to "Ida", 'j' to "Julius", 'k' to "Kaufmann",
        'l' to "Ludwig", 'm' to "Martha", 'n' to "Nordpol", 'o' to "Otto",
        'ö' to "Ökonom", 'p' to "Paula", 'q' to "Quelle", 'r' to "Richard",
        's' to "Samuel", 't' to "Theodor", 'u' to "Ulrich", 'ü' to "Übermut",
        'v' to "Viktor", 'w' to "Wilhelm", 'x' to "Xanthippe", 'y' to "Ypsilon",
        'z' to "Zeppelin",
    )

    /**
     * Ziffer → Ansagewort. Bewusst eigene Tabelle statt einer Umkehrung von
     * [GermanNumbers]: dort führen mehrere Wörter auf dieselbe Zahl
     * ("eins"/"eine"/"ein" → 1), eine Umkehrung wäre also nicht eindeutig.
     * Außerdem kennt [GermanNumbers] die Null nicht.
     */
    val DIGITS: Map<Char, String> = mapOf(
        '0' to "Null", '1' to "Eins", '2' to "Zwei", '3' to "Drei", '4' to "Vier",
        '5' to "Fünf", '6' to "Sechs", '7' to "Sieben", '8' to "Acht", '9' to "Neun",
    )

    /**
     * Zeichenvorrat für **erzeugte** Codes. Ausgelassen sind Zeichen, die
     * beim Zuhören oder Abtippen kippen können: 0/O, 1/I/L sehen einander
     * ähnlich, Q und Y sind im Deutschen selten und werden oft verhört.
     * Wer einen Code erzeugt, sollte nur hieraus wählen – gelesen und
     * ausgesprochen werden über [spell] trotzdem alle Zeichen.
     */
    const val CODE_ALPHABET: String = "ABCDEFGHJKMNPRSTUVWXZ23456789"

    /**
     * Wandelt einen Code in eine vorlesbare Ansage:
     * `"B7A3"` → `"Berta, Sieben, Anton, Drei"`.
     *
     * Die Kommas sind Absicht: Piper macht daran eine hörbare Pause, ohne
     * die der Code als ein Wort durchrauscht. Unbekannte Zeichen werden
     * übersprungen, Leerzeichen und Bindestriche ebenso – ein Code darf
     * also als "B7-A3" übergeben werden.
     */
    fun spell(text: String): String =
        text.lowercase()
            .mapNotNull { c -> LETTERS[c] ?: DIGITS[c] }
            .joinToString(", ")

    /**
     * Ansage mit vorangestellter Buchstabenzuordnung, für den ersten
     * Durchgang: `"B"` → `"B wie Berta"`. Bei Codes, die anschließend
     * abgetippt werden, hilft das der Vertrauensperson mehr als das
     * Ansagewort allein.
     */
    fun spellExplicit(text: String): String =
        text.lowercase()
            .mapNotNull { c ->
                LETTERS[c]?.let { "${c.uppercaseChar()} wie $it" } ?: DIGITS[c]
            }
            .joinToString(", ")
}
