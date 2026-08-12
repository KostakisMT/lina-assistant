package dev.lina.feature.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Öffnet die Be My Eyes App, damit die Nutzer:in dort per Videoanruf einen
 * sehenden Freiwilligen erreicht (ADR-033).
 *
 * Lina kann den Anruf **nicht selbst absetzen**: Be My Eyes bietet keine
 * offene API, um einen Anruf ins Freiwilligennetzwerk zu initiieren – nur
 * das umgekehrte "Specialized Help"-Partnerprogramm, bei dem Unternehmen
 * von BME-Nutzer:innen angerufen werden. Diese Klasse startet also nur die
 * App; der letzte Tap auf "Call a Volunteer" bleibt bei der Nutzer:in. Das
 * ist bewusst kein Rückschritt gegenüber Leitprinzip 1 ("Voice-First, nicht
 * Voice-Only – Touch ist optionaler Fallback"): Be My Eyes ist selbst für
 * blinde Nutzer:innen bedienbar (TalkBack-optimiert).
 *
 * Anders als Whisper/Piper/OpenWakeWord ist Be My Eyes eine **externe
 * Abhängigkeit**, die nicht gebündelt werden kann – sie muss separat aus dem
 * Play Store installiert sein (siehe ONBOARDING.md/WARTUNG.md).
 */
class HelperCallLauncher(private val context: Context) {

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Öffnet Be My Eyes, falls installiert – sonst die Play-Store-Seite,
     * damit ein sehender Angehöriger die App installieren kann (die
     * Nutzer:in selbst kann den Installationsdialog nicht bedienen). Kein
     * automatischer Download: Lina zeigt nur die Seite, installiert nie
     * selbst.
     */
    fun open(): HelperCallResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            HelperCallResult.Opened
        } else {
            openPlayStoreListing()
            HelperCallResult.NotInstalled
        }
    }

    private fun openPlayStoreListing() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME"),
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }

    companion object {
        const val PACKAGE_NAME = "com.bemyeyes.bemyeyes"
    }
}

sealed class HelperCallResult {
    data object Opened : HelperCallResult()
    data object NotInstalled : HelperCallResult()

    val spokenMessage: String
        get() = when (this) {
            Opened -> "Ich öffne Be My Eyes. Tipp auf \"Freiwilligen anrufen\", um Hilfe zu bekommen."
            NotInstalled ->
                "Be My Eyes ist nicht installiert. Ich zeige die Play-Store-Seite, " +
                    "damit jemand sie für dich installieren kann."
        }
}
