package dev.lina.core.contacts

import android.content.Context
import android.provider.ContactsContract

data class Contact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
)

/**
 * Woher die Kontakte kommen. Trennt [FuzzyContactMatcher] vom
 * `ContactsContract`-Zugriff, damit die Match-Kaskade in reinen
 * JVM-Tests gegen feste Kontaktlisten laufen kann – das Matching
 * entscheidet, wen Lina anruft, und ein Fehler dort ist teuer.
 */
interface ContactSource {
    fun loadAll(): List<Contact>
}

class ContactRepository(private val context: Context) : ContactSource {

    override fun loadAll(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        ) ?: return emptyList()

        cursor.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val id = it.getLong(idIdx)
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numberIdx) ?: continue
                contacts.add(Contact(id, name, number))
            }
        }
        return contacts
    }

    fun findByName(query: String): List<Contact> {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null,
        ) ?: return emptyList()

        val results = mutableListOf<Contact>()
        cursor.use {
            val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                results.add(Contact(
                    it.getLong(idIdx),
                    it.getString(nameIdx) ?: continue,
                    it.getString(numberIdx) ?: continue,
                ))
            }
        }
        return results
    }
}
