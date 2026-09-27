package dev.lina.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Dauerhaftes Gesprächsprotokoll für die Entwicklung (WARTUNG.md, Einwilligung
 * Punkt 8): Weckwort-Treffer, Verstandenes, erkannter Intent, was Lina sagt,
 * Abbrüche und Fehler – mit Zeitstempel, eine Zeile pro Ereignis.
 *
 * Warum nicht einfach logcat: Der Puffer läuft auf dem Tablet nach wenigen
 * Minuten über (2026-09-27: nach einem Nachmittag war nur noch ein einziger
 * Eintrag da). Nächtliches Dazwischenreden oder Fehlalarme des Weckworts
 * lassen sich so nicht nachträglich untersuchen.
 *
 * Ablage: `Android/data/dev.lina/files/protokoll/JJJJ-MM-TT.log` – für andere
 * Apps unlesbar, per `./scripts/remote.sh protokoll` abholbar. Dateien älter
 * als [AUFBEWAHRUNG_TAGE] werden beim Start gelöscht.
 *
 * Jede Methode schreibt zusätzlich ins normale logcat, damit Aufrufer nur
 * eine Stelle brauchen. Schreiben läuft auf einem eigenen Thread – das
 * Protokoll darf Lina nie ausbremsen und nie abstürzen lassen.
 */
object Protokoll {

    const val AUFBEWAHRUNG_TAGE = 30

    @Volatile
    private var verzeichnis: File? = null
    private val schreiber = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Protokoll").apply { isDaemon = true }
    }

    /** Idempotent; von Activity und WakeWordService aufgerufen (wer zuerst startet). */
    fun init(context: Context) {
        if (verzeichnis != null) return
        val dir = context.getExternalFilesDir("protokoll") ?: return
        verzeichnis = dir
        schreiber.execute {
            runCatching {
                dir.mkdirs()
                val grenze = System.currentTimeMillis() - AUFBEWAHRUNG_TAGE * 24L * 60 * 60 * 1000
                dir.listFiles()?.filter { it.lastModified() < grenze }?.forEach { it.delete() }
            }
        }
    }

    fun d(tag: String, text: String) {
        Log.d(tag, text)
        schreibe(tag, text)
    }

    fun w(tag: String, text: String, fehler: Throwable? = null) {
        Log.w(tag, text, fehler)
        schreibe(tag, "WARNUNG " + mitFehler(text, fehler))
    }

    fun e(tag: String, text: String, fehler: Throwable? = null) {
        Log.e(tag, text, fehler)
        schreibe(tag, "FEHLER " + mitFehler(text, fehler))
    }

    /** Nur ins Protokoll, nicht ins logcat – für Ereignisse, die logcat fluten würden. */
    fun nurProtokoll(tag: String, text: String) = schreibe(tag, text)

    private fun schreibe(tag: String, text: String) {
        val dir = verzeichnis ?: return
        val jetzt = System.currentTimeMillis()
        schreiber.execute {
            runCatching {
                File(dir, dateiname(jetzt)).appendText(zeile(jetzt, tag, text))
            }
        }
    }

    private fun mitFehler(text: String, fehler: Throwable?): String =
        if (fehler == null) text else "$text – ${fehler::class.simpleName}: ${fehler.message}"

    private val ZEIT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    internal fun dateiname(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone).toString() + ".log"

    /** Eine Zeile pro Ereignis – Zeilenumbrüche im Text würden die Auswertung zerschneiden. */
    internal fun zeile(millis: Long, tag: String, text: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val zeit = ZEIT.format(Instant.ofEpochMilli(millis).atZone(zone))
        val einzeilig = text.replace("\r", "").replace("\n", " ⏎ ")
        return "$zeit [$tag] $einzeilig\n"
    }
}
