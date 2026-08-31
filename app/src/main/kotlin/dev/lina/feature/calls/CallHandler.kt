package dev.lina.feature.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
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
                    // Leer, damit der Aufrufer NICHT zusätzlich spricht:
                    // dialContact() macht die Ansage selbst und weiß als
                    // einzige Stelle, ob überhaupt gewählt wurde. Vorher stand
                    // hier "Ich rufe X an." – und das wurde auch dann gesagt,
                    // wenn gar kein Anruf zustande kam (am 2026-08-30 im
                    // Flugmodus reproduziert: erst die Erfolgsmeldung, dann
                    // acht Sekunden später der Fehlschlag).
                    CallResult.Success("")
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
     *
     * Die Ansage ist bewusst neutral ("Ich verbinde dich mit …"): Ob der
     * Anruf zustande kommt, steht zu diesem Zeitpunkt nicht fest. Die
     * Vorgängerfassung meldete unbedingt "Ich rufe … an", auch wenn gar nichts
     * passierte – am 2026-08-30 ohne SIM genau so beobachtet, der Dialer kam
     * nie hoch und Lina meldete trotzdem Erfolg. Dieselbe Klasse Fehler wie
     * beim SMS-Versand (siehe [dev.lina.feature.sms.SmsSender]): für einen
     * Nutzer, der das Ergebnis nicht sehen kann, ist eine falsche Rückmeldung
     * schlechter als gar keine.
     *
     * Gemeldet wird deshalb nur der FEHLSCHLAG. Kommt die Verbindung zustande,
     * hört der Nutzer das Freizeichen selbst – eine zusätzliche Ansage würde
     * ihm nur ins Gespräch reden.
     */
    fun dialContact(contact: Contact) {
        ttsEngine.speak("Ich verbinde dich mit ${contact.displayName}.", TtsPriority.HIGH)
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${contact.phoneNumber}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_CALL fehlgeschlagen", e)
            ttsEngine.speak(
                "Ich konnte den Anruf nicht starten.",
                TtsPriority.HIGH,
            )
            return
        }
        watchCallEstablished(contact.displayName)
    }

    /**
     * Beobachtet, ob der Anruf innerhalb von [CALL_ESTABLISH_TIMEOUT_MS]
     * überhaupt aus dem Ruhezustand kommt. Bleibt der Telefoniezustand
     * durchgehend `CALL_STATE_IDLE`, ist nichts passiert – etwa weil keine
     * SIM steckt, kein Netz da ist oder der Anbieter ablehnt.
     *
     * Absichtlich nur dieses eine Signal: Der genaue Trennungsgrund wäre nur
     * über einen eigenen `InCallService` zu bekommen, und die Frage "ist
     * überhaupt etwas passiert" ist die, die dem Nutzer fehlt.
     */
    private fun watchCallEstablished(name: String) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val handler = Handler(Looper.getMainLooper())
        var settled = false
        var callback: TelephonyCallback? = null

        val stop = {
            callback?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
            callback = null
        }

        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                if (settled) return
                if (state != TelephonyManager.CALL_STATE_IDLE) {
                    // Verbindung baut sich auf – der Nutzer hört es selbst.
                    settled = true
                    Log.d(TAG, "Anruf an $name aufgebaut (state=$state)")
                    handler.post { stop() }
                }
            }
        }
        callback = cb
        try {
            tm.registerTelephonyCallback(context.mainExecutor, cb)
        } catch (e: SecurityException) {
            // Ohne READ_PHONE_STATE keine Überwachung – dann lieber gar keine
            // Aussage treffen als eine falsche.
            Log.w(TAG, "Anrufüberwachung nicht möglich", e)
            return
        }

        handler.postDelayed({
            if (settled) return@postDelayed
            settled = true
            stop()
            Log.w(TAG, "Anruf an $name kam nicht zustande (Zustand blieb IDLE)")
            ttsEngine.speak(
                "Der Anruf bei $name ist nicht zustande gekommen.",
                TtsPriority.HIGH,
            )
        }, CALL_ESTABLISH_TIMEOUT_MS)
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

private const val TAG = "CallHandler"

/** So lange wird auf einen Verbindungsaufbau gewartet, bevor Lina abwinkt. */
private const val CALL_ESTABLISH_TIMEOUT_MS = 8_000L

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
