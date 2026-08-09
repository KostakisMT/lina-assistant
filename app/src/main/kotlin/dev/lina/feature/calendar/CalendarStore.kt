package dev.lina.feature.calendar

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistiert die Kalender-Termine (EncryptedSharedPreferences, JSON-Liste).
 * Gleiches Muster wie ReminderStore – neuer Speicher, keine Alt-Klartext-
 * Migration nötig.
 */
class CalendarStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Verschlüsselter Speicher fehlgeschlagen, falle auf Klartext zurück", e)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun all(): List<CalendarEvent> =
        CalendarEvent.listFromJson(prefs.getString(KEY_LIST, "[]") ?: "[]")

    /** Chronologisch, ab jetzt (Datum, dann Uhrzeit). */
    fun upcoming(now: Long = System.currentTimeMillis()): List<CalendarEvent> {
        val heute = todayIso(now)
        return all()
            .filter { it.date >= heute }
            .sortedWith(compareBy({ it.date }, { it.time ?: "24:00" }))
    }

    fun add(event: CalendarEvent) {
        save(all().filterNot { it.id == event.id } + event)
    }

    fun remove(id: Int) {
        save(all().filterNot { it.id == id })
    }

    fun removeAll() = save(emptyList())

    fun nextId(): Int = (all().maxOfOrNull { it.id } ?: 0) + 1

    private fun save(list: List<CalendarEvent>) {
        prefs.edit().putString(KEY_LIST, CalendarEvent.listToJson(list)).apply()
    }

    private fun todayIso(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private companion object {
        const val TAG = "CalendarStore"
        const val PREFS = "lina_calendar_secure"
        const val KEY_LIST = "events"
    }
}
