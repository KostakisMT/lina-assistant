package dev.lina.feature.reminder

import java.util.Calendar

/**
 * Versteht gesprochene deutsche Zeitangaben – offline, ohne Cloud.
 *
 * Beispiele:
 *   "erinnere mich in zwanzig Minuten an die Suppe"
 *   "erinnere mich morgen um zehn an den Arzt"
 *   "erinnere mich jeden Tag um acht an die Tabletten"
 *   "erinnere mich um Viertel nach sieben ans Radio"
 */
object GermanTimeParser {

    data class Result(val triggerAtMillis: Long, val text: String, val daily: Boolean)

    /** @return null, wenn keine Zeit erkannt wurde (dann kann Claude helfen). */
    fun parse(input: String, now: Long = System.currentTimeMillis()): Result? {
        val s = input.lowercase().trim()
        val daily = DAILY_MARKERS.any { s.contains(it) }

        relativeZeit(s, now)?.let { millis ->
            return Result(millis, sachtext(s), daily = false)
        }
        uhrzeit(s, now, daily)?.let { millis ->
            return Result(millis, sachtext(s), daily)
        }
        return null
    }

    /** "in 20 Minuten", "in einer halben Stunde", "in zwei Stunden" */
    private fun relativeZeit(s: String, now: Long): Long? {
        Regex("""in\s+(?:einer\s+)?halben\s+stunde""").find(s)?.let {
            return now + 30 * 60_000L
        }
        Regex("""in\s+(?:einer|1)\s+stunde""").find(s)?.let { return now + 3_600_000L }

        Regex("""in\s+(\d+|${ZAHLWORT_ALTERNATIVEN})\s*(minute|minuten|stunde|stunden)""")
            .find(s)?.let { m ->
                val menge = zahl(m.groupValues[1]) ?: return null
                val faktor = if (m.groupValues[2].startsWith("stunde")) 3_600_000L else 60_000L
                return now + menge * faktor
            }
        return null
    }

    /** "um 10", "um halb acht", "um Viertel nach sieben", "morgen um 8 Uhr 30" */
    private fun uhrzeit(s: String, now: Long, daily: Boolean): Long? {
        var stunde: Int? = null
        var minute = 0

        Regex("""viertel\s+vor\s+(\d{1,2}|${ZAHLWORT_ALTERNATIVEN})""").find(s)?.let { m ->
            stunde = zahl(m.groupValues[1])?.toInt()?.minus(1); minute = 45
        }
        if (stunde == null) {
            Regex("""viertel\s+nach\s+(\d{1,2}|${ZAHLWORT_ALTERNATIVEN})""").find(s)?.let { m ->
                stunde = zahl(m.groupValues[1])?.toInt(); minute = 15
            }
        }
        if (stunde == null) {
            Regex("""halb\s+(\d{1,2}|${ZAHLWORT_ALTERNATIVEN})""").find(s)?.let { m ->
                // "halb acht" = 7:30
                stunde = zahl(m.groupValues[1])?.toInt()?.minus(1); minute = 30
            }
        }
        if (stunde == null) {
            // "um 8 Uhr 30", "um 8:30", "um acht"
            Regex("""um\s+(\d{1,2}|${ZAHLWORT_ALTERNATIVEN})(?:\s*(?:uhr|:)\s*(\d{1,2}))?""")
                .find(s)?.let { m ->
                    stunde = zahl(m.groupValues[1])?.toInt()
                    minute = m.groupValues[2].toIntOrNull() ?: 0
                }
        }
        val h = stunde ?: return null
        if (h !in 0..23 || minute !in 0..59) return null

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var zielStunde = h
        // Tageszeit-Hinweise: "um acht abends" → 20 Uhr
        if (h in 1..11 && ABEND_MARKERS.any { s.contains(it) }) zielStunde = h + 12
        cal.set(Calendar.HOUR_OF_DAY, zielStunde)
        cal.set(Calendar.MINUTE, minute)

        when {
            s.contains("übermorgen") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            s.contains("morgen") && !s.contains("morgens") -> cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        // Vergangene Zeit heute → morgen (außer explizit "heute")
        if (cal.timeInMillis <= now && !daily && !s.contains("heute")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (daily && cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * Zieht den Sach-Teil heraus: "erinnere mich morgen um zehn an den Arzt"
     * → "an den Arzt". Fällt auf einen neutralen Text zurück.
     */
    private fun sachtext(s: String): String {
        Regex("""\b(an\s+.+|dass\s+.+|zu\s+.+)$""").find(s)?.let { m ->
            val t = m.groupValues[1].trim().trim('.', '!', '?')
            if (t.length > 2) return t
        }
        // "erinnere mich an X um Y" – Sachteil steht vor der Zeitangabe
        Regex("""erinner\w*\s+mich\s+(?:bitte\s+)?an\s+(.+?)(?:\s+(?:um|in|morgen|heute|jeden)\b.*)?$""")
            .find(s)?.let { m ->
                val t = m.groupValues[1].trim().trim('.', '!', '?')
                if (t.length > 2) return "an $t"
            }
        return "deine Erinnerung"
    }

    private fun zahl(w: String): Long? {
        w.toLongOrNull()?.let { return it }
        return ZAHLWORTE[w.trim()]
    }

    private val ZAHLWORTE = mapOf(
        "eins" to 1L, "eine" to 1L, "einer" to 1L, "ein" to 1L, "zwei" to 2L,
        "drei" to 3L, "vier" to 4L, "fünf" to 5L, "sechs" to 6L, "sieben" to 7L,
        "acht" to 8L, "neun" to 9L, "zehn" to 10L, "elf" to 11L, "zwölf" to 12L,
        "dreizehn" to 13L, "vierzehn" to 14L, "fünfzehn" to 15L, "sechzehn" to 16L,
        "siebzehn" to 17L, "achtzehn" to 18L, "neunzehn" to 19L, "zwanzig" to 20L,
        "dreißig" to 30L, "vierzig" to 40L, "fünfzig" to 50L, "sechzig" to 60L,
    )

    private val ZAHLWORT_ALTERNATIVEN = ZAHLWORTE.keys.joinToString("|")

    private val DAILY_MARKERS = listOf(
        "jeden tag", "täglich", "taeglich", "jeden morgen", "jeden abend", "immer um",
    )

    private val ABEND_MARKERS = listOf("abends", "am abend", "nachmittags", "am nachmittag")
}
