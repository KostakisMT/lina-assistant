package dev.lina.feature.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import dev.lina.core.contacts.Contact
import dev.lina.core.contacts.ContactMatchResult
import dev.lina.core.contacts.FuzzyContactMatcher
import dev.lina.core.contacts.PhoneNumberRisk
import dev.lina.core.tts.TtsEngine
import dev.lina.core.tts.TtsPriority

class CallHandler(
    private val context: Context,
    private val ttsEngine: TtsEngine,
    private val contactMatcher: FuzzyContactMatcher,
) {

    private val telecomManager: TelecomManager
        get() = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    fun startCall(contactQuery: String): CallResult {
        return when (val match = contactMatcher.findMatches(contactQuery)) {
            is ContactMatchResult.SingleMatch -> {
                // Sondernummern nicht ungefragt wählen. Der blinde Nutzer sieht
                // nicht, wen Lina anruft – und ein verhörter Kontaktname kann
                // per Fuzzy-Matching auf einem Anbieter-Diensteintrag landen
                // (am Gerät belegt, siehe PhoneNumberRisk).
                val risk = PhoneNumberRisk.classify(match.contact.phoneNumber)
                if (risk.needsConfirmation()) {
                    CallResult.Confirm(
                        PhoneNumberRisk.confirmationPrompt(match.contact.displayName, risk),
                        match.contact,
                    )
                } else {
                    dialContact(match.contact)
                    CallResult.Success("Ich rufe ${match.contact.displayName} an.")
                }
            }
            is ContactMatchResult.MultipleMatches -> {
                val names = match.contacts.mapIndexed { i, c -> "${i + 1}. ${c.displayName}" }
                CallResult.Disambiguation(
                    "Welchen ${match.query} meinst du? ${names.joinToString(", ")}",
                    match.contacts,
                )
            }
            is ContactMatchResult.NoMatch -> {
                CallResult.Error("Ich habe keinen Kontakt mit dem Namen ${match.query} gefunden.")
            }
        }
    }

    /**
     * Wählt tatsächlich. Einziger Ort mit `ACTION_CALL` im Projekt.
     *
     * NUR aufrufen, wenn die Nummer entweder unbedenklich ist oder der Nutzer
     * bestätigt hat – die Prüfung sitzt in [startCall] bzw. beim Aufrufer der
     * Rückfrage. Direkt aufgerufen umgeht diese Methode den Schutz.
     */
    fun dialContact(contact: Contact) {
        ttsEngine.speak("Ich rufe ${contact.displayName} an.", TtsPriority.HIGH)
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${contact.phoneNumber}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    @Suppress("MissingPermission")
    fun acceptCall() {
        telecomManager.acceptRingingCall()
        ttsEngine.speak("Anruf angenommen.", TtsPriority.INTERRUPT)
    }

    @Suppress("MissingPermission")
    fun rejectCall() {
        telecomManager.endCall()
        ttsEngine.speak("Anruf abgelehnt.", TtsPriority.INTERRUPT)
    }

    @Suppress("MissingPermission")
    fun hangUp() {
        telecomManager.endCall()
        ttsEngine.speak("Aufgelegt.", TtsPriority.HIGH)
    }
}

sealed class CallResult {
    data class Success(val message: String) : CallResult()
    data class Disambiguation(val message: String, val candidates: List<Contact>) : CallResult()

    /**
     * Sondernummer erkannt – es wurde NICHT gewählt. Der Aufrufer muss den
     * Nutzer bestätigen lassen und danach [CallHandler.dialContact] rufen.
     */
    data class Confirm(val message: String, val contact: Contact) : CallResult()
    data class Error(val message: String) : CallResult()

    val displayMessage: String
        get() = when (this) {
            is Success -> message
            is Disambiguation -> message
            is Confirm -> message
            is Error -> message
        }
}
