package dev.lina.feature.calendar

import dev.lina.core.text.GermanCalendarNames
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Ein Kalender-Termin ("Termin am 15. März: Arzttermin"). Läuft nie ohne
 * eine verknüpfte [dev.lina.feature.reminder.Reminder] – die eigentliche
 * Erinnerung übernimmt die bestehende Reminder-Infrastruktur, [reminderId]
 * verknüpft beide nur zum gemeinsamen Löschen.
 */
data class CalendarEvent(
    val id: Int,
    val title: String,
    /** ISO-Datum, yyyy-MM-dd. */
    val date: String,
    /** HH:mm, oder null = ohne feste Uhrzeit (Standardzeit gilt für die Erinnerung). */
    val time: String?,
    /** Id der verknüpften Reminder, für gekoppeltes Löschen. */
    val reminderId: Int?,
    /** "manual" oder "document". */
    val source: String,
) {
    /** Gesprochene Datumsangabe, z.B. "heute", "morgen", "am Montag", "am 15. März". */
    fun spokenDate(now: Long = System.currentTimeMillis()): String {
        val parts = date.split("-").map { it.toInt() }
        val cal = GregorianCalendar(parts[0], parts[1] - 1, parts[2])
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val tag = tagesDifferenz(nowCal, cal)
        val datumsText = when (tag) {
            0 -> "heute"
            1 -> "morgen"
            2 -> "übermorgen"
            in 3..6 -> "am ${GermanCalendarNames.weekdayName(cal)}"
            else -> "am ${cal.get(Calendar.DAY_OF_MONTH)}. ${GermanCalendarNames.monthName(cal)}"
        }
        return if (time != null) "$datumsText um $time Uhr" else datumsText
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("date", date)
        put("time", time ?: JSONObject.NULL)
        put("reminderId", reminderId ?: JSONObject.NULL)
        put("source", source)
    }

    companion object {
        fun fromJson(o: JSONObject) = CalendarEvent(
            id = o.getInt("id"),
            title = o.getString("title"),
            date = o.getString("date"),
            time = if (o.isNull("time")) null else o.getString("time"),
            reminderId = if (o.isNull("reminderId")) null else o.getInt("reminderId"),
            source = o.optString("source", "manual"),
        )

        fun listToJson(list: List<CalendarEvent>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String): List<CalendarEvent> = try {
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
    }
}
