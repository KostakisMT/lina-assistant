package dev.lina.feature.sms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.util.Log
import dev.lina.core.contacts.Contact
import dev.lina.core.contacts.ContactMatchResult
import dev.lina.core.contacts.FuzzyContactMatcher
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority

/**
 * SMS-Versand mit **echter** Erfolgsmeldung.
 *
 * `SmsManager.sendTextMessage()` arbeitet asynchron und wirft bei Netzfehlern
 * keine Exception – es kehrt sofort zurück. Die Vorgängerfassung übergab für
 * `sentIntent` und `deliveryIntent` jeweils `null` und sagte direkt danach
 * bedingungslos „SMS gesendet." Lina konnte also gar nicht wissen, ob etwas
 * rausging, und meldete in jedem Fall Erfolg.
 *
 * Am 2026-08-30 am Gerät belegt: vier Testnachrichten scheiterten sämtlich mit
 * `RESULT_ERROR_GENERIC_FAILURE` (Android protokollierte „Persist SMS into
 * FAILED"), Lina meldete viermal „SMS gesendet", und beim Empfänger kam nichts
 * an. Für einen blinden Nutzer ist das der schlimmste Fall: Er glaubt, seine
 * Tochter benachrichtigt zu haben, und hat keine Möglichkeit, den Irrtum zu
 * bemerken – Leitprinzip 6 verlangt Rückmeldung für jede Aktion, und eine
 * falsche Rückmeldung ist schlechter als gar keine.
 */
class SmsSender(
    private val context: Context,
    private val ttsEngine: TtsEngine,
    private val contactMatcher: FuzzyContactMatcher,
    private val smsReader: SmsReader,
) {

    private val smsManager: SmsManager = SmsManager.getDefault()
    private var nextRequestCode = 0

    fun sendTo(contactQuery: String, message: String): SmsResult {
        return when (val match = contactMatcher.findMatches(contactQuery)) {
            is ContactMatchResult.SingleMatch -> {
                sendSms(match.contact, message)
                // Bewusst neutral formuliert: ob es geklappt hat, sagt erst der
                // Rückruf. Die alte Fassung meldete hier schon "gesendet".
                SmsResult.Sent("Ich schicke die Nachricht an ${match.contact.displayName}.")
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
            ?: return SmsResult.Error("Keine Nachricht zum Antworten vorhanden.")
        val name = lastMsg.displayName ?: lastMsg.address
        return try {
            sendRawSms(lastMsg.address, message, name)
            SmsResult.Sent("Ich schicke die Antwort an $name.")
        } catch (e: Exception) {
            Log.e(TAG, "Antwort konnte nicht gesendet werden", e)
            SmsResult.Error("Die Antwort konnte nicht gesendet werden.")
        }
    }

    private fun sendSms(contact: Contact, message: String) {
        sendRawSms(contact.phoneNumber, message, contact.displayName)
    }

    private fun sendRawSms(number: String, message: String, empfaenger: String) {
        val sentIntent = registerSentCallback(empfaenger)
        val parts = smsManager.divideMessage(message)
        try {
            if (parts.size == 1) {
                smsManager.sendTextMessage(number, null, message, sentIntent, null)
            } else {
                // Bei mehrteiligen Nachrichten meldet nur der ERSTE Teil an
                // unseren Rückruf – sonst spräche Lina die Bestätigung mehrfach.
                val intents = ArrayList<PendingIntent?>(parts.size).apply {
                    add(sentIntent)
                    repeat(parts.size - 1) { add(null) }
                }
                smsManager.sendMultipartTextMessage(number, null, parts, intents, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendTextMessage fehlgeschlagen", e)
            ttsEngine.speak(
                "Die Nachricht an $empfaenger konnte nicht gesendet werden.",
                TtsPriority.HIGH,
            )
        }
    }

    /**
     * Registriert einen einmaligen Empfänger für das Sendeergebnis und liefert
     * den passenden [PendingIntent]. Der Empfänger meldet sich nach dem ersten
     * Ergebnis selbst wieder ab.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSentCallback(empfaenger: String): PendingIntent {
        val requestCode = nextRequestCode++
        val action = "$ACTION_SMS_SENT.$requestCode"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                try {
                    context.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                    // war schon abgemeldet – unkritisch
                }
                val code = resultCode
                Log.d(TAG, "Sendeergebnis für $empfaenger: resultCode=$code")
                if (code == android.app.Activity.RESULT_OK) {
                    ttsEngine.speak("Die Nachricht an $empfaenger ist raus.", TtsPriority.HIGH)
                } else {
                    ttsEngine.speak(
                        "Die Nachricht an $empfaenger konnte nicht gesendet werden. " +
                            grundText(code),
                        TtsPriority.HIGH,
                    )
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Gesprochener Grund. Bewusst alltagssprachlich statt mit Fehlercodes. */
    private fun grundText(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_NO_SERVICE ->
            "Es besteht gerade keine Netzverbindung."
        SmsManager.RESULT_ERROR_RADIO_OFF ->
            "Das Funkmodul ist ausgeschaltet."
        SmsManager.RESULT_ERROR_NULL_PDU ->
            "Die Nachricht konnte nicht aufbereitet werden."
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED ->
            "Es wurden zu viele Nachrichten hintereinander verschickt."
        SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
            "Das Mobilfunknetz hat den Versand abgelehnt. " +
                "Möglicherweise ist die Karte nicht für SMS freigeschaltet."
        else -> "Der Grund ist unklar."
    }

    companion object {
        private const val TAG = "SmsSender"
        private const val ACTION_SMS_SENT = "dev.lina.SMS_SENT"
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
