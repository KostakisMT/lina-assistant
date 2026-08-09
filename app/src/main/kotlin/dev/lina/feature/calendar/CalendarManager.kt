package dev.lina.feature.calendar

import android.content.Context
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority
import dev.lina.feature.reminder.Reminder
import dev.lina.feature.reminder.ReminderScheduler
import dev.lina.feature.reminder.ReminderStore
import java.util.Calendar

/**
 * Termine anlegen, ansagen und verwalten. Die eigentliche Erinnerung läuft
 * immer über eine ganz normale, bestehende [Reminder] – kein zweites
 * Scheduling-System, siehe ADR-031.
 */
class CalendarManager(
    private val context: Context,
    private val tts: TtsEngine,
) {
    private val store = CalendarStore(context)
    private val reminderStore = ReminderStore(context)

    /**
     * Legt einen Termin aus gesprochenem Text an. Erwartet das Muster
     * "... Termin ... für/am <Datumsphrase>: <Titel>" (Doppelpunkt trennt
     * Datum von Titel, wie beim SMS-Diktat "schreib Boris: Text").
     * @return true, wenn Datum und Titel erkannt wurden.
     */
    fun createFromSpeech(input: String): Boolean {
        val colonIndex = input.indexOf(':')
        if (colonIndex < 0 || colonIndex == input.length - 1) {
            speakRephraseHint()
            return false
        }
        val datePart = input.substring(0, colonIndex)
        val title = input.substring(colonIndex + 1).trim()
        if (title.isBlank()) {
            speakRephraseHint()
            return false
        }
        val parsed = GermanDateParser.parse(datePart) ?: run {
            speakRephraseHint()
            return false
        }
        create(title, parsed.date, parsed.time, source = "manual")
        return true
    }

    private fun speakRephraseHint() {
        tts.speak(
            "Für wann und was? Sag zum Beispiel: Termin am 15. März: Arzttermin.",
            TtsPriority.HIGH,
        )
    }

    /** Legt einen Termin mit bekannten Werten an (auch vom Claude-Tool und Dokument-Vorschlag genutzt). */
    fun create(title: String, date: String, time: String?, source: String) {
        val triggerAtMillis = triggerMillis(date, time ?: DEFAULT_TIME)
        val reminder = Reminder(
            id = reminderStore.nextId(),
            text = "Termin: $title",
            triggerAtMillis = triggerAtMillis,
            daily = false,
        )
        reminderStore.add(reminder)
        ReminderScheduler.schedule(context, reminder)

        val event = CalendarEvent(
            id = store.nextId(),
            title = title,
            date = date,
            time = time,
            reminderId = reminder.id,
            source = source,
        )
        store.add(event)

        tts.speak(
            "Termin eingetragen: $title, ${event.spokenDate()}.",
            TtsPriority.HIGH,
        )
    }

    /** Spricht die anstehenden Termine (Sprachbefehl "zeig mir den Kalender"/"was sind meine nächsten Termine"). */
    fun list() {
        val alle = store.upcoming()
        if (alle.isEmpty()) {
            tts.speak(
                "Du hast keine Termine. Sag zum Beispiel: Termin am 15. März: Arzttermin.",
                TtsPriority.HIGH,
            )
            return
        }
        val einleitung = if (alle.size == 1) "Du hast einen Termin." else "Du hast ${alle.size} Termine."
        tts.speak(einleitung, TtsPriority.HIGH)
        alle.take(MAX_ANSAGE).forEach { e ->
            tts.speak("${e.title}, ${e.spokenDate()}.", TtsPriority.NORMAL)
        }
    }

    /** Für die Wochenansicht (CalendarPanel): heute bis +6 Tage. */
    fun currentWeek(): List<CalendarEvent> {
        val heute = todayIso()
        val ende = isoPlusDays(6)
        return store.all().filter { it.date in heute..ende }.sortedWith(
            compareBy({ it.date }, { it.time ?: "24:00" })
        )
    }

    /** Löscht alle Termine samt verknüpften Erinnerungen. */
    fun clearAll() {
        val alle = store.all()
        if (alle.isEmpty()) {
            tts.speak("Du hast keine Termine.", TtsPriority.HIGH)
            return
        }
        alle.forEach { event ->
            event.reminderId?.let { rid ->
                reminderStore.find(rid)?.let { ReminderScheduler.cancel(context, it) }
                reminderStore.remove(rid)
            }
        }
        store.removeAll()
        tts.speak(
            if (alle.size == 1) "Der Termin ist gelöscht." else "Alle ${alle.size} Termine sind gelöscht.",
            TtsPriority.HIGH,
        )
    }

    private fun triggerMillis(date: String, time: String): Long {
        val dateParts = date.split("-").map { it.toInt() }
        val timeParts = time.split(":").map { it.toInt() }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, dateParts[0])
            set(Calendar.MONTH, dateParts[1] - 1)
            set(Calendar.DAY_OF_MONTH, dateParts[2])
            set(Calendar.HOUR_OF_DAY, timeParts[0])
            set(Calendar.MINUTE, timeParts.getOrElse(1) { 0 })
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun todayIso(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun isoPlusDays(days: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private companion object {
        const val MAX_ANSAGE = 5
        const val DEFAULT_TIME = "09:00"
    }
}
