package dev.lina.feature.reminder

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Eine gesprochene Erinnerung ("Erinnere mich morgen um zehn an den Arzt").
 * Läuft vollständig offline über den AlarmManager.
 */
data class Reminder(
    val id: Int,
    /** Was angesagt werden soll, z.B. "an den Arzt" oder "die Tabletten". */
    val text: String,
    /** Auslösezeit in Millisekunden seit Epoche. */
    val triggerAtMillis: Long,
    /** Täglich wiederholen (Medikamente o.ä.). */
    val daily: Boolean = false,
) {
    /** Gesprochene Zeitangabe, z.B. "heute um 10 Uhr 30" oder "morgen um 8 Uhr". */
    fun spokenTime(now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val uhrzeit = if (m == 0) "$h Uhr" else "$h Uhr $m"

        if (daily) return "täglich um $uhrzeit"

        val tagUnterschied = tagesDifferenz(nowCal, cal)
        val tag = when (tagUnterschied) {
            0 -> "heute"
            1 -> "morgen"
            2 -> "übermorgen"
            else -> {
                val wochentage = listOf(
                    "Sonntag", "Montag", "Dienstag", "Mittwoch",
                    "Donnerstag", "Freitag", "Samstag",
                )
                val name = wochentage[cal.get(Calendar.DAY_OF_WEEK) - 1]
                if (tagUnterschied in 3..6) "am $name" else {
                    "am ${cal.get(Calendar.DAY_OF_MONTH)}. ${monatsName(cal)}"
                }
            }
        }
        return "$tag um $uhrzeit"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("triggerAt", triggerAtMillis)
        put("daily", daily)
    }

    companion object {
        fun fromJson(o: JSONObject) = Reminder(
            id = o.getInt("id"),
            text = o.getString("text"),
            triggerAtMillis = o.getLong("triggerAt"),
            daily = o.optBoolean("daily", false),
        )

        fun listToJson(list: List<Reminder>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String): List<Reminder> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }

        private fun tagesDifferenz(von: Calendar, bis: Calendar): Int {
            val a = von.clone() as Calendar
            val b = bis.clone() as Calendar
            listOf(a, b).forEach {
                it.set(Calendar.HOUR_OF_DAY, 0)
                it.set(Calendar.MINUTE, 0)
                it.set(Calendar.SECOND, 0)
                it.set(Calendar.MILLISECOND, 0)
            }
            return ((b.timeInMillis - a.timeInMillis) / 86_400_000L).toInt()
        }

        private fun monatsName(cal: Calendar): String = listOf(
            "Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
            "August", "September", "Oktober", "November", "Dezember",
        )[cal.get(Calendar.MONTH)]
    }
}
