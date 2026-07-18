package dev.lina.feature.sms

import android.telephony.SmsManager
import dev.lina.core.contacts.Contact
import dev.lina.core.contacts.ContactMatchResult
import dev.lina.core.contacts.FuzzyContactMatcher
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority

class SmsSender(
    private val ttsEngine: TtsEngine,
    private val contactMatcher: FuzzyContactMatcher,
    private val smsReader: SmsReader,
) {

    private val smsManager: SmsManager = SmsManager.getDefault()

    fun sendTo(contactQuery: String, message: String): SmsResult {
        return when (val match = contactMatcher.findMatches(contactQuery)) {
            is ContactMatchResult.SingleMatch -> {
                sendSms(match.contact, message)
                SmsResult.Sent("SMS an ${match.contact.displayName} gesendet: \"$message\"")
            }
            is ContactMatchResult.MultipleMatches -> {
                val names = match.contacts.mapIndexed { i, c -> "${i + 1}. ${c.displayName}" }
                SmsResult.Disambiguation(
                    "Welchen ${match.query} meinst du? ${names.joinToString(", ")}",
                    match.contacts,
                )
            }
            is ContactMatchResult.NoMatch -> {
                SmsResult.Error("Ich habe keinen Kontakt mit dem Namen ${match.query} gefunden.")
            }
        }
    }

    fun replyToLast(message: String): SmsResult {
        val lastMsg = smsReader.lastSender
        if (lastMsg == null) {
            return SmsResult.Error("Keine Nachricht zum Antworten vorhanden.")
        }
        val name = lastMsg.displayName ?: lastMsg.address
        try {
            sendRawSms(lastMsg.address, message)
            ttsEngine.speak("Antwort an $name gesendet: \"$message\"", TtsPriority.HIGH)
            return SmsResult.Sent("Antwort an $name gesendet.")
        } catch (e: Exception) {
            return SmsResult.Error("SMS konnte nicht gesendet werden.")
        }
    }

    private fun sendSms(contact: Contact, message: String) {
        ttsEngine.speak("Sende SMS an ${contact.displayName}.", TtsPriority.HIGH)
        sendRawSms(contact.phoneNumber, message)
        ttsEngine.speak("SMS gesendet.", TtsPriority.NORMAL)
    }

    private fun sendRawSms(number: String, message: String) {
        val parts = smsManager.divideMessage(message)
        if (parts.size == 1) {
            smsManager.sendTextMessage(number, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
        }
    }
}

sealed class SmsResult {
    data class Sent(val message: String) : SmsResult()
    data class Disambiguation(val message: String, val candidates: List<Contact>) : SmsResult()
    data class Error(val message: String) : SmsResult()

    val displayMessage: String
        get() = when (this) {
            is Sent -> message
            is Disambiguation -> message
            is Error -> message
        }
}
