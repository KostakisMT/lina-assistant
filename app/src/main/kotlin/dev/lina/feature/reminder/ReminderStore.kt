package dev.lina.feature.reminder

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistiert die Erinnerungen (EncryptedSharedPreferences, JSON-Liste).
 * Erinnerungen haben oft Gesundheitsbezug ("an die Tabletten", "zum Arzttermin") –
 * unverschlüsselte SharedPreferences waren eine dokumentierte Sicherheitslücke
 * (SICHERHEIT.md). Migriert einmalig bestehende Klartext-Einträge aus dem alten
 * Speicher und löscht ihn danach.
 */
class ReminderStore(context: Context) {

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
        // Nie ganz ohne Erinnerungen dastehen – lieber unverschlüsselt als gar nicht.
        Log.e(TAG, "Verschlüsselter Speicher fehlgeschlagen, falle auf Klartext zurück", e)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    init {
        migrateFromLegacyPlaintext(context)
    }

    private fun migrateFromLegacyPlaintext(context: Context) {
        if (prefs.contains(KEY_LIST)) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacyList = legacy.getString(KEY_LIST, null)
        if (legacyList != null) {
            prefs.edit().putString(KEY_LIST, legacyList).apply()
            Log.i(TAG, "Erinnerungen aus Klartext-Speicher migriert und verschlüsselt")
        }
        if (legacy.contains(KEY_LIST)) {
            legacy.edit().clear().apply()
        }
    }

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
        private const val TAG = "ReminderStore"
        private const val PREFS = "lina_reminders_secure"
        private const val LEGACY_PREFS = "lina_reminders"
        private const val KEY_LIST = "reminders"
    }
}
