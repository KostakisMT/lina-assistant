package dev.lina.feature.contactimport

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Merkt sich den zuletzt gesehenen SIM-Fingerabdruck und einen ggf. vom Nutzer
 * abgelehnten Fingerabdruck, damit ein "Nein" nicht bei jedem Neustart erneut
 * nervt, eine tatsächlich andere SIM aber wieder nachfragt. Gleiches
 * EncryptedSharedPreferences-Muster wie ReminderStore.
 */
class ContactImportStore(context: Context) {

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

    fun lastSeenIdentity(): String? = prefs.getString(KEY_LAST_SEEN, null)

    fun recordSeen(identity: String) {
        prefs.edit().putString(KEY_LAST_SEEN, identity).apply()
    }

    fun declinedIdentity(): String? = prefs.getString(KEY_DECLINED, null)

    fun recordDeclined(identity: String) {
        prefs.edit().putString(KEY_DECLINED, identity).apply()
    }

    private companion object {
        const val TAG = "ContactImportStore"
        const val PREFS = "lina_contact_import_secure"
        const val KEY_LAST_SEEN = "last_seen_sim_identity"
        const val KEY_DECLINED = "declined_sim_identity"
    }
}
