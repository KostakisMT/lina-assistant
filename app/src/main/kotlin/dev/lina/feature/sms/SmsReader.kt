package dev.lina.feature.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val displayName: String?,
) {
    val isIncoming: Boolean get() = type == 1
}

class SmsReader(
    private val context: Context,
    private val ttsEngine: TtsEngine,
) {

    private var lastReadMessages: List<SmsMessage> = emptyList()
    private var currentIndex = -1

    val lastSender: SmsMessage?
        get() = lastReadMessages.firstOrNull { it.isIncoming }

    fun readLatest(count: Int = 5) {
        val messages = loadInbox(count)
        if (messages.isEmpty()) {
            ttsEngine.speak("Du hast keine Nachrichten.", TtsPriority.HIGH)
            return
        }

        lastReadMessages = messages
        currentIndex = 0

        val incoming = messages.filter { it.isIncoming }
        val intro = when (incoming.size) {
            0 -> "Keine neuen eingehenden Nachrichten."
            1 -> "Eine Nachricht."
            else -> "${incoming.size} Nachrichten."
        }
        ttsEngine.speak(intro, TtsPriority.HIGH)

        if (incoming.isNotEmpty()) {
            readMessage(incoming.first())
        }
    }

    fun readNext() {
        val incoming = lastReadMessages.filter { it.isIncoming }
        if (incoming.isEmpty()) {
            ttsEngine.speak("Keine weiteren Nachrichten.", TtsPriority.NORMAL)
            return
        }
        currentIndex++
        if (currentIndex >= incoming.size) {
            ttsEngine.speak("Das waren alle Nachrichten.", TtsPriority.NORMAL)
            currentIndex = incoming.size - 1
            return
        }
        readMessage(incoming[currentIndex])
    }

    private fun readMessage(msg: SmsMessage) {
        val sender = msg.displayName ?: msg.address
        ttsEngine.speak("Von $sender: ${msg.body}", TtsPriority.NORMAL)
    }

    private fun loadInbox(limit: Int): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val cursor: Cursor? = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date", "type"),
            null, null,
            "date DESC",
        )
        cursor?.use {
            val idIdx = it.getColumnIndex("_id")
            val addrIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            val typeIdx = it.getColumnIndex("type")
            var count = 0
            while (it.moveToNext() && count < limit) {
                val address = it.getString(addrIdx) ?: continue
                messages.add(SmsMessage(
                    id = it.getLong(idIdx),
                    address = address,
                    body = it.getString(bodyIdx) ?: "",
                    date = it.getLong(dateIdx),
                    type = it.getInt(typeIdx),
                    displayName = resolveContactName(address),
                ))
                count++
            }
        }
        return messages
    }

    private fun resolveContactName(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null,
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }
}
