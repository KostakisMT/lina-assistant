package dev.lina.core.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.util.Log

data class WriteResult(val inserted: Int, val failed: Int)

/**
 * Schreibt Kontakte als lokale (kontenlose) Kontakte in die System-Kontakte.
 * Chunkt in Batches, um die Binder-Transaktionsgrenze
 * (TransactionTooLargeException) nicht zu reißen; ein fehlgeschlagener Batch
 * bricht nicht den gesamten Import ab, sondern wird nur mitgezählt.
 */
class ContactWriter(private val context: Context) {

    fun insertAll(contacts: List<Contact>): WriteResult {
        if (contacts.isEmpty()) return WriteResult(inserted = 0, failed = 0)

        var inserted = 0
        var failed = 0
        contacts.chunked(BATCH_SIZE).forEach { chunk ->
            val ops = ArrayList<ContentProviderOperation>()
            for (contact in chunk) {
                val rawContactIndex = ops.size
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build(),
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            contact.displayName,
                        )
                        .build(),
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                        )
                        .build(),
                )
            }
            try {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                inserted += chunk.size
            } catch (e: Exception) {
                Log.w(TAG, "Kontakt-Batch-Import fehlgeschlagen (${chunk.size} Kontakte)", e)
                failed += chunk.size
            }
        }
        return WriteResult(inserted = inserted, failed = failed)
    }

    private companion object {
        const val TAG = "ContactWriter"
        const val BATCH_SIZE = 150
    }
}
