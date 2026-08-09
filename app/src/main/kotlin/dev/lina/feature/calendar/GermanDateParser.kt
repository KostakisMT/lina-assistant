package dev.lina.feature.calendar

import dev.lina.core.text.GermanCalendarNames
import dev.lina.core.text.GermanNumbers
import java.util.Calendar

/**
 * Versteht eine reine deutsche Datumsphrase ("nächsten Montag", "am 15.
 * März", "übermorgen um 10 Uhr") – offline, ohne Cloud. Anders als
 * [dev.lina.feature.reminder.GermanTimeParser] geht es hier NICHT um relative
 * Uhrzeiten, sondern um Kalendertage; der Titel ist bereits vorher am
 * Doppelpunkt abgetrennt (siehe CalendarManager), diese Klasse bekommt nur
 * die Datumsphrase selbst – kein Freitext-Stripping nötig.
 */
object GermanDateParser {

    data class ParsedDate(val date: String, val time: String?)

    /** @return null, wenn kein Datum erkannt wurde (dann kann Claude helfen). */
    fun parse(input: String, now: Long = System.currentTimeMillis()): ParsedDate? {
        val s = input.lowercase().trim()
        val time = uhrzeit(s)

        val cal = relativerTag(s, now)
            ?: wochentagRelativ(s, now)
            ?: explizitesDatum(s, now)
            ?: return null

        return ParsedDate(isoDatum(cal), time)
    }

    /** "heute", "morgen", "übermorgen", "in 3 Tagen", "in einer Woche" */
    private fun relativerTag(s: String, now: Long): Calendar? {
        val offset = when {
            Regex("""\bin\s+(?:einer|1)\s+woche\b""").containsMatchIn(s) -> 7
            else -> Regex("""\bin\s+(\d+|${GermanNumbers.ALTERNATION})\s*tage?n?\b""").find(s)?.let { m ->
                GermanNumbers.parse(m.groupValues[1])?.toInt()
            } ?: when {
                s.contains("übermorgen") -> 2
                s.contains("morgen") && !s.contains("morgens") -> 1
                s.contains("heute") -> 0
                else -> null
            }
        } ?: return null

        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offset)
        }
    }

    /** "nächsten Montag", "diesen Freitag", "kommenden Dienstag" – immer die
     * nächste Zukunfts-Ausprägung, nie der heutige Tag selbst. */
    private fun wochentagRelativ(s: String, now: Long): Calendar? {
        val match = Regex(
            """\b(?:nächsten|naechsten|diesen|kommenden)\s+(${GermanCalendarNames.wochentage.joinToString("|") { it.lowercase() }})\b"""
        ).find(s) ?: return null

        val zielName = match.groupValues[1]
        val zielIndex = GermanCalendarNames.wochentage.indexOfFirst { it.lowercase() == zielName }
        if (zielIndex < 0) return null
        // Calendar.DAY_OF_WEEK: Sonntag=1..Samstag=7, wochentage ist 0-indiziert (Sonntag=0)
        val zielDow = zielIndex + 1

        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val heuteDow = cal.get(Calendar.DAY_OF_WEEK)
        var offset = (zielDow - heuteDow + 7) % 7
        if (offset == 0) offset = 7 // nie der heutige Tag – immer die kommende Ausprägung
        cal.add(Calendar.DAY_OF_YEAR, offset)
        return cal
    }

    /** "am 15. März", "15.3.", "15.03.2026" */
    private fun explizitesDatum(s: String, now: Long): Calendar? {
        Regex("""(\d{1,2})\.\s*(${GermanCalendarNames.monate.joinToString("|") { it.lowercase() }})""")
            .find(s)?.let { m ->
                val tag = m.groupValues[1].toIntOrNull() ?: return null
                val monatIndex = GermanCalendarNames.monate.indexOfFirst { it.lowercase() == m.groupValues[2] }
                if (monatIndex < 0) return null
                return naechstesVorkommen(tag, monatIndex, now)
            }

        // Kein abschließendes \b: bei "15.8." (mit Punkt am Ende) läge die
        // Wortgrenze nach einem Nicht-Wortzeichen und würde nie matchen.
        Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})?""").find(s)?.let { m ->
            val tag = m.groupValues[1].toIntOrNull() ?: return null
            val monat = m.groupValues[2].toIntOrNull() ?: return null
            if (monat !in 1..12 || tag !in 1..31) return null
            val jahr = m.groupValues[3].toIntOrNull()
            return if (jahr != null) {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, jahr); set(Calendar.MONTH, monat - 1); set(Calendar.DAY_OF_MONTH, tag)
                }
            } else {
                naechstesVorkommen(tag, monat - 1, now)
            }
        }
        return null
    }

    /** Nächstes Vorkommen von Tag+Monat – dieses Jahr, oder nächstes Jahr falls schon vorbei. */
    private fun naechstesVorkommen(tag: Int, monatIndex: Int, now: Long): Calendar? {
        if (tag !in 1..31 || monatIndex !in 0..11) return null
        val jetzt = Calendar.getInstance().apply { timeInMillis = now }
        val kandidat = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MONTH, monatIndex)
            set(Calendar.DAY_OF_MONTH, tag)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (kandidat.before(jetzt) && !sameDay(kandidat, jetzt)) {
            kandidat.add(Calendar.YEAR, 1)
        }
        return kandidat
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    /** "um 10 Uhr", "um 10:30", "um zehn" */
    private fun uhrzeit(s: String): String? {
        val m = Regex("""um\s+(\d{1,2}|${GermanNumbers.ALTERNATION})(?:\s*(?:uhr|:)\s*(\d{1,2}))?""").find(s)
            ?: return null
        val stunde = GermanNumbers.parse(m.groupValues[1])?.toInt() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (stunde !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(stunde, minute)
    }

    private fun isoDatum(cal: Calendar): String = "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
    )
}
