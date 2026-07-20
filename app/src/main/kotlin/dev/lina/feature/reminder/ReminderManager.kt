package dev.lina.feature.reminder

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority

/**
 * Erinnerungen anlegen, ansagen und verwalten – vollständig offline.
 * Ansagen macht der Manager selbst (wie AudiobookManager/NewsReader).
 */
class ReminderManager(
    private val context: Context,
    private val tts: TtsEngine,
) {
    private val store = ReminderStore(context)

    init {
        store.purgeExpired()
    }

    /**
     * Legt eine Erinnerung aus gesprochenem Text an.
     * @return true, wenn eine Zeit erkannt wurde.
     */
    fun createFromSpeech(input: String): Boolean {
        val parsed = GermanTimeParser.parse(input) ?: run {
            tts.speak(
                "Wann soll ich dich erinnern? Sag zum Beispiel: " +
                    "Erinnere mich morgen um zehn an den Arzt.",
                TtsPriority.HIGH,
            )
            return false
        }
        create(parsed.text, parsed.triggerAtMillis, parsed.daily)
        return true
    }

    /** Legt eine Erinnerung mit bekannten Werten an (auch vom Claude-Tool genutzt). */
    fun create(text: String, triggerAtMillis: Long, daily: Boolean) {
        val reminder = Reminder(
            id = store.nextId(),
            text = text,
            triggerAtMillis = triggerAtMillis,
            daily = daily,
        )
        store.add(reminder)
        ReminderScheduler.schedule(context, reminder)
        Log.d(TAG, "Erinnerung angelegt: ${reminder.text} – ${reminder.spokenTime()}")

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val hinweis = if (!ReminderScheduler.canScheduleExact(am)) {
            " Sie kann sich um einige Minuten verschieben – dein Betreuer kann das " +
                "in den Einstellungen erlauben."
        } else {
            ""
        }
        tts.speak(
            "Alles klar, ich erinnere dich ${reminder.spokenTime()} ${reminder.text}.$hinweis",
            TtsPriority.HIGH,
        )
    }

    /** Sagt die fällige Erinnerung an (vom ReminderReceiver ausgelöst). */
    fun announce(text: String) {
        tts.speak("Erinnerung: $text.", TtsPriority.INTERRUPT)
    }

    fun list() {
        val alle = store.upcoming()
        if (alle.isEmpty()) {
            tts.speak(
                "Du hast keine Erinnerungen. Sag zum Beispiel: " +
                    "Erinnere mich morgen um zehn an den Arzt.",
                TtsPriority.HIGH,
            )
            return
        }
        val einleitung = if (alle.size == 1) {
            "Du hast eine Erinnerung."
        } else {
            "Du hast ${alle.size} Erinnerungen."
        }
        tts.speak(einleitung, TtsPriority.HIGH)
        alle.take(MAX_ANSAGE).forEach { r ->
            tts.speak("${r.spokenTime()} ${r.text}.", TtsPriority.NORMAL)
        }
    }

    /** Löscht alle Erinnerungen (einfachster verständlicher Weg per Sprache). */
    fun clearAll() {
        val alle = store.all()
        if (alle.isEmpty()) {
            tts.speak("Du hast keine Erinnerungen.", TtsPriority.HIGH)
            return
        }
        alle.forEach { ReminderScheduler.cancel(context, it) }
        store.removeAll()
        tts.speak(
            if (alle.size == 1) "Die Erinnerung ist gelöscht."
            else "Alle ${alle.size} Erinnerungen sind gelöscht.",
            TtsPriority.HIGH,
        )
    }

    companion object {
        private const val TAG = "ReminderManager"
        private const val MAX_ANSAGE = 5
    }
}
