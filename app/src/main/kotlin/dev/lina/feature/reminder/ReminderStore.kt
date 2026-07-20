package dev.lina.feature.reminder

import android.content.Context

/** Persistiert die Erinnerungen (SharedPreferences, JSON-Liste). */
class ReminderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<Reminder> =
        Reminder.listFromJson(prefs.getString(KEY_LIST, "[]") ?: "[]")

    /** Nur noch anstehende (bei täglichen immer relevant). */
    fun upcoming(now: Long = System.currentTimeMillis()): List<Reminder> =
        all().filter { it.daily || it.triggerAtMillis > now }
            .sortedBy { it.triggerAtMillis }

    fun add(reminder: Reminder) {
        save(all().filterNot { it.id == reminder.id } + reminder)
    }

    fun remove(id: Int) {
        save(all().filterNot { it.id == id })
    }

    fun removeAll() = save(emptyList())

    fun find(id: Int): Reminder? = all().firstOrNull { it.id == id }

    /** Aufräumen: abgelaufene einmalige Erinnerungen verwerfen. */
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        save(all().filter { it.daily || it.triggerAtMillis > now })
    }

    fun nextId(): Int = (all().maxOfOrNull { it.id } ?: 0) + 1

    private fun save(list: List<Reminder>) {
        prefs.edit().putString(KEY_LIST, Reminder.listToJson(list)).apply()
    }

    companion object {
        private const val PREFS = "lina_reminders"
        private const val KEY_LIST = "reminders"
    }
}
