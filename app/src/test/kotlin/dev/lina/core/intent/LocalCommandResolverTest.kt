package dev.lina.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ebene 1 der Intent-Erkennung. Wichtiger als die einzelnen Muster ist hier
 * ihre **Reihenfolge**: mehrere Muster greifen auf dieselben Wörter zu
 * ("weiter", "zurück", "lies"), und wer zuerst prüft, gewinnt. Die
 * Abgrenzungs-Tests unten halten genau das fest.
 */
class LocalCommandResolverTest {

    private val resolver = LocalCommandResolver()

    // ------------------------------------------------------------- Anrufe

    @Test
    fun `ruf Kontakt an`() {
        val intent = resolver.resolve("ruf boris an")
        assertEquals(ResolvedIntent.Call("boris"), intent)
    }

    @Test
    fun `ruf mal Kontakt an`() {
        assertEquals(ResolvedIntent.Call("arundhati"), resolver.resolve("ruf mal arundhati an"))
    }

    @Test
    fun `Anrufsteuerung`() {
        assertEquals(ResolvedIntent.AcceptCall, resolver.resolve("annehmen"))
        assertEquals(ResolvedIntent.RejectCall, resolver.resolve("ablehnen"))
        assertEquals(ResolvedIntent.HangUp, resolver.resolve("auflegen"))
    }

    // ---------------------------------------------------------------- SMS

    @Test
    fun `SMS mit Doppelpunkt`() {
        assertEquals(
            ResolvedIntent.SendSms("boris", "ich komme später"),
            resolver.resolve("schreib boris: ich komme später"),
        )
    }

    @Test
    fun `Nachrichten vorlesen`() {
        assertEquals(ResolvedIntent.ReadSms, resolver.resolve("lies meine nachrichten"))
    }

    @Test
    fun `Antwort auf letzte SMS`() {
        assertEquals(ResolvedIntent.ReplySms("bin unterwegs"), resolver.resolve("antwort: bin unterwegs"))
    }

    // ---------------------------------------------------------- Dokumente

    @Test
    fun `Post vorlesen`() {
        assertEquals(ResolvedIntent.ReadDocument, resolver.resolve("lies mir die post vor"))
        assertEquals(ResolvedIntent.ReadDocument, resolver.resolve("was steht da"))
    }

    @Test
    fun `Nachrichten schlagen Dokument`() {
        // Abgrenzung: "lies meine Nachrichten" darf nicht die Kamera auslösen
        assertEquals(ResolvedIntent.ReadSms, resolver.resolve("lies meine nachrichten"))
    }

    // -------------------------------------------------------- Nachrichten

    @Test
    fun `Nachrichtenlage abfragen`() {
        assertEquals(ResolvedIntent.ReadNews, resolver.resolve("was gibt es neues"))
    }

    @Test
    fun `regionale Nachfrage geht an Ebene 2`() {
        // "aus Hannover" kann der RSS-Reader nicht – Claude soll übernehmen
        assertNull(resolver.resolve("was gibt es neues aus hannover"))
    }

    // ----------------------------------------------------------- Hörbücher

    @Test
    fun `Hoerbuch starten und steuern`() {
        assertEquals(ResolvedIntent.PlayAudiobook, resolver.resolve("spiel hörbuch ab"))
        assertEquals(ResolvedIntent.PauseAudiobook, resolver.resolve("pause"))
        assertEquals(ResolvedIntent.ResumeAudiobook, resolver.resolve("fortsetzen"))
        assertEquals(ResolvedIntent.AudiobookInfo, resolver.resolve("was höre ich gerade"))
    }

    @Test
    fun `zurueckspulen mit und ohne Sekundenangabe`() {
        assertEquals(ResolvedIntent.RewindAudiobook(30), resolver.resolve("30 sekunden zurück"))
        assertEquals(ResolvedIntent.RewindAudiobook(15), resolver.resolve("15 sekunden zurück"))
        // ohne Zahl gilt der Standardwert
        assertEquals(ResolvedIntent.RewindAudiobook(30), resolver.resolve("zurückspulen"))
    }

    @Test
    fun `Hoerbuch suchen`() {
        assertEquals(ResolvedIntent.SearchAudiobook("tolstoi"), resolver.resolve("suche tolstoi"))
    }

    @Test
    fun `Schlaf-Timer`() {
        assertEquals(ResolvedIntent.SleepTimer(20), resolver.resolve("timer 20 min"))
    }

    // ------------------------------------------------------------- Kapitel

    @Test
    fun `naechstes Kapitel`() {
        assertEquals(ResolvedIntent.NextChapter, resolver.resolve("nächstes kapitel"))
        assertEquals(ResolvedIntent.NextChapter, resolver.resolve("ein kapitel weiter"))
    }

    @Test
    fun `vorheriges Kapitel`() {
        assertEquals(ResolvedIntent.PreviousChapter, resolver.resolve("vorheriges kapitel"))
        assertEquals(ResolvedIntent.PreviousChapter, resolver.resolve("ein kapitel zurück"))
    }

    @Test
    fun `Kapitel per Nummer`() {
        assertEquals(ResolvedIntent.GoToChapter(3), resolver.resolve("kapitel 3"))
        assertEquals(ResolvedIntent.GoToChapter(12), resolver.resolve("kapitel 12"))
    }

    @Test
    fun `Kapitel per Zahlwort`() {
        assertEquals(ResolvedIntent.GoToChapter(3), resolver.resolve("kapitel drei"))
        assertEquals(ResolvedIntent.GoToChapter(5), resolver.resolve("spring zu kapitel fünf"))
        assertEquals(ResolvedIntent.GoToChapter(13), resolver.resolve("kapitel dreizehn"))
    }

    @Test
    fun `Kapitel auflisten`() {
        assertEquals(ResolvedIntent.ListChapters, resolver.resolve("welche kapitel gibt es"))
        assertEquals(ResolvedIntent.ListChapters, resolver.resolve("wie viele kapitel hat das buch"))
    }

    @Test
    fun `Kapitel schlaegt Meldung und Zurueckspulen`() {
        // "nächste" gehört sonst zu den Nachrichten, "zurück" zum Spulen –
        // sobald "Kapitel" fällt, gewinnt die Kapitelnavigation
        assertEquals(ResolvedIntent.NextChapter, resolver.resolve("nächstes kapitel"))
        assertEquals(ResolvedIntent.PreviousChapter, resolver.resolve("ein kapitel zurück"))
        assertEquals(ResolvedIntent.NextNews, resolver.resolve("nächste meldung"))
    }

    // -------------------------------------------------- Erinnerungen / Zeit

    @Test
    fun `Erinnerung wird als Rohtext weitergereicht`() {
        val intent = resolver.resolve("erinnere mich morgen um zehn an den arzt")
        assertTrue(intent is ResolvedIntent.SetReminder)
    }

    @Test
    fun `Erinnerung schlaegt Anruf`() {
        // "erinnere mich ... an Boris" darf keinen Anruf auslösen
        val intent = resolver.resolve("erinnere mich morgen an boris")
        assertTrue(intent is ResolvedIntent.SetReminder)
    }

    @Test
    fun `Uhrzeit abfragen`() {
        assertEquals(ResolvedIntent.Time, resolver.resolve("wie spät ist es"))
    }

    @Test
    fun `Erinnerungen auflisten und loeschen`() {
        assertEquals(ResolvedIntent.ListReminders, resolver.resolve("welche erinnerungen habe ich"))
        assertEquals(ResolvedIntent.ClearReminders, resolver.resolve("lösche alle erinnerungen"))
    }

    // --------------------------------------------------------------- Stopp

    @Test
    fun `Stopp beendet die Ausgabe`() {
        assertEquals(ResolvedIntent.Stop, resolver.resolve("stopp"))
        assertEquals(ResolvedIntent.Stop, resolver.resolve("sei still"))
    }

    @Test
    fun `Grossschreibung und Leerzeichen stoeren nicht`() {
        assertEquals(ResolvedIntent.Call("boris"), resolver.resolve("  Ruf Boris an  "))
    }

    @Test
    fun `unbekannter Satz liefert null`() {
        assertNull(resolver.resolve("erzähl mir etwas über die nordsee"))
    }
}
