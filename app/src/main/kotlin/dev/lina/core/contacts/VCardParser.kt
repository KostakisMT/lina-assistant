package dev.lina.core.contacts

/**
 * Minimaler, abhängigkeitsfreier Parser für vCard 2.1/3.0-Exporte (das
 * universelle Kontakt-Exportformat, das praktisch jedes alte Telefon – Android,
 * iPhone, Feature-Phone – erzeugen kann). Bewusst rein (kein Context), damit er
 * wie DaisyParser als reine JVM-Logik testbar bleibt.
 *
 * QUOTED-PRINTABLE-Dekodierung (alte Exporte mit Umlauten) wird bewusst NICHT
 * unterstützt – Rohwert wird übernommen statt zu crashen. Kann bei Bedarf
 * als eigener Schritt nachgerüstet werden.
 */
object VCardParser {

    fun parse(text: String): List<Contact> {
        val unfolded = unfold(text)
        val blocks = splitIntoBlocks(unfolded)
        var nextId = 0L
        val result = mutableListOf<Contact>()
        for (block in blocks) {
            val contact = parseBlock(block) ?: continue
            for (number in contact.numbers) {
                result.add(Contact(id = nextId++, displayName = contact.name, phoneNumber = number))
            }
        }
        return result
    }

    private data class ParsedBlock(val name: String, val numbers: List<String>)

    /** Zeilenfortsetzungen (Zeile beginnt mit Leerzeichen/Tab) an die Vorzeile anhängen. */
    private fun unfold(text: String): List<String> {
        val rawLines = text.split(Regex("""\r\n|\r|\n"""))
        val unfolded = mutableListOf<String>()
        for (line in rawLines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.size - 1] = unfolded.last() + line.substring(1)
            } else {
                unfolded.add(line)
            }
        }
        return unfolded
    }

    private fun splitIntoBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            when {
                line.trim().equals("BEGIN:VCARD", ignoreCase = true) -> current = mutableListOf()
                line.trim().equals("END:VCARD", ignoreCase = true) -> {
                    current?.let { blocks.add(it) }
                    current = null
                }
                current != null -> current!!.add(line)
            }
        }
        return blocks
    }

    private fun parseBlock(lines: List<String>): ParsedBlock? {
        var fn: String? = null
        var n: String? = null
        val numbers = mutableListOf<String>()

        for (line in lines) {
            val colonIndex = line.indexOf(':')
            if (colonIndex <= 0) continue
            val keyPart = line.substring(0, colonIndex)
            val value = line.substring(colonIndex + 1).trim()
            if (value.isEmpty()) continue

            // Gruppen-Präfix ("item1.TEL") und Parameter (";TYPE=CELL") abtrennen.
            val propertyName = keyPart.substringAfterLast('.').substringBefore(';').trim()

            when {
                propertyName.equals("FN", ignoreCase = true) -> fn = value
                propertyName.equals("N", ignoreCase = true) -> n = value
                propertyName.equals("TEL", ignoreCase = true) -> numbers.add(value)
            }
        }

        if (numbers.isEmpty()) return null

        val displayName = fn?.takeIf { it.isNotBlank() }
            ?: n?.let { nameFromN(it) }?.takeIf { it.isNotBlank() }
            ?: "Unbekannt"

        return ParsedBlock(displayName, numbers)
    }

    /** vCard N: Nachname;Vorname;Zusatz;Titel;Suffix -> "Vorname Nachname". */
    private fun nameFromN(n: String): String {
        val parts = n.split(';')
        val familyName = parts.getOrNull(0)?.trim().orEmpty()
        val givenName = parts.getOrNull(1)?.trim().orEmpty()
        return listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
    }
}
