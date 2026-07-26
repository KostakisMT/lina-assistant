package dev.lina.core.contacts

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Liest Kontakte direkt von der SIM-Karte (ADN – Abbreviated Dialing Numbers)
 * über den undokumentierten, aber seit langem stabilen AOSP-Provider
 * `content://icc/adn`. Manche OEMs unterstützen das nicht oder verweigern den
 * Zugriff – der komplette Methodenkörper ist deshalb defensiv: jeder Fehler
 * führt zu einer leeren Liste statt zu einem Absturz.
 */
class SimContactSource(private val context: Context) : ContactSource {

    override fun loadAll(): List<Contact> {
        return try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://icc/adn"),
                arrayOf("name", "number"),
                null, null, null,
            ) ?: return emptyList()

            val contacts = mutableListOf<Contact>()
            cursor.use {
                val nameIdx = it.getColumnIndex("name")
                val numberIdx = it.getColumnIndex("number")
                var id = 0L
                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val number = if (numberIdx >= 0) it.getString(numberIdx) else null
                    if (name.isNullOrBlank() || number.isNullOrBlank()) continue
                    contacts.add(Contact(id = id++, displayName = name, phoneNumber = number))
                }
            }
            contacts
        } catch (e: Exception) {
            Log.w(TAG, "SIM-Kontakte nicht lesbar", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "SimContactSource"
    }
}
