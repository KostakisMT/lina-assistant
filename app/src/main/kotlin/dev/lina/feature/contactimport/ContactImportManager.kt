package dev.lina.feature.contactimport

import android.content.Context
import dev.lina.core.contacts.Contact
import dev.lina.core.contacts.ContactDedup
import dev.lina.core.contacts.ContactRepository
import dev.lina.core.contacts.ContactWriter
import dev.lina.core.contacts.SimContactSource
import dev.lina.core.contacts.VCardParser

data class ImportResult(val imported: Int, val duplicates: Int, val failed: Int)

/**
 * Orchestriert einen Kontakt-Import (SIM oder vCard-Datei): Quelle laden ->
 * gegen bestehende Kontakte entduplizieren -> neue Kontakte schreiben.
 */
class ContactImportManager(private val context: Context) {

    private val contactRepository = ContactRepository(context)
    private val contactWriter = ContactWriter(context)

    fun importFromSim(): ImportResult {
        val simContacts = SimContactSource(context).loadAll()
        return importContacts(simContacts)
    }

    fun importFromVcardText(text: String): ImportResult {
        val vcardContacts = VCardParser.parse(text)
        return importContacts(vcardContacts)
    }

    private fun importContacts(incoming: List<Contact>): ImportResult {
        val existing = contactRepository.loadAll()
        val dedup = ContactDedup.partition(existing, incoming)
        val writeResult = contactWriter.insertAll(dedup.new)
        return ImportResult(
            imported = writeResult.inserted,
            duplicates = dedup.duplicateCount,
            failed = writeResult.failed,
        )
    }
}
