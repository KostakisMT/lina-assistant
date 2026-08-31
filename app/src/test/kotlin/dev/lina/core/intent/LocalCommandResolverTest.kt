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

    // ------------------------------------------------- Helfer-Anruf (ADR-033)

    @Test
    fun `Helfer-Anruf per Be My Eyes`() {
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("ruf einen helfer an"))
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("ruf einen freiwilligen an"))
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("hol be my eyes"))
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("ich brauche hilfe beim sehen"))
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("sehende hilfe"))
    }

    @Test
    fun `Helfer-Anruf schlaegt normalen Kontaktanruf`() {
        // Abgrenzung: "ruf einen Helfer an" darf nicht als Kontaktsuche nach
        // dem Namen "einen Helfer" landen (resolveHelperCall steht vor
        // resolveCall in der Kette).
        assertEquals(ResolvedIntent.CallHelper, resolver.resolve("ruf einen helfer an"))
        assertEquals(ResolvedIntent.Call("boris"), resolver.resolve("ruf boris an"))
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
    fun `SMS vorlesen`() {
        // "Nachrichten" heisst im Deutschen beides: SMS und News.
        // Hier ist die SMS-Seite gemeint - siehe ReadSms.
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
    fun `SMS schlagen Dokument`() {
        // Abgrenzung: "lies meine Nachrichten" darf nicht die Kamera auslösen.
        // Gemeint sind SMS, nicht News.
        assertEquals(ResolvedIntent.ReadSms, resolver.resolve("lies meine nachrichten"))
    }

    // ------------------------------------------- Nachrichten (News, nicht SMS)

    @Test
    fun `News gehen komplett an Ebene 2`() {
        // ACHTUNG Doppeldeutigkeit: hier geht es um NEWS ("was gibt es Neues"),
        // NICHT um SMS. "lies meine Nachrichten" trifft weiterhin ReadSms -
        // siehe `SMS vorlesen` und `SMS schlagen Dokument` weiter oben.
        //
        // News macht bewusst Claude per Websuche (relevanter Regional- und
        // Welt-Überblick mit Rückfragen) – der lokale Resolver fasst sie nicht an,
        // damit die Eingabe an Ebene 2 durchfällt. Das ist eine ENTSCHEIDUNG,
        // keine vergessene Regel: wer hier ReadNews wieder einbaut, hebelt sie aus.
        assertNull(resolver.resolve("was gibt es neues"))
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
    fun `weiter nur als eigenstaendiges Wort, nicht als Teilstring`() {
        // Bugfix 2026-07-25: ".*weiter.*" traf am Gerät auch "lass uns
        // weiterreden" (freie Konversation) und startete versehentlich das
        // Hörbuch. Wortgrenzen stellen sicher, dass nur "weiter" als eigenes
        // Wort zählt, nicht als Präfix eines zusammengesetzten Worts.
        assertEquals(ResolvedIntent.ResumeAudiobook, resolver.resolve("weiter"))
        assertEquals(ResolvedIntent.ResumeAudiobook, resolver.resolve("spiel weiter"))
        assertNull(resolver.resolve("lass uns weiterreden"))
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
    fun `Hoerbuch-Suche nach Thema Genre`() {
        assertEquals(
            ResolvedIntent.SearchAudiobookByGenre("segeln"),
            resolver.resolve("hörbücher zum thema segeln"),
        )
        assertEquals(
            ResolvedIntent.SearchAudiobookByGenre("politik"),
            resolver.resolve("suche hörbücher zum thema politik"),
        )
        assertEquals(
            ResolvedIntent.SearchAudiobookByGenre("segeln"),
            resolver.resolve("gibt es hörbücher über segeln"),
        )
    }

    @Test
    fun `Themen-Suche verwechselt sich nicht mit normaler Titel-Autoren-Suche`() {
        // Singular "hörbuch"/"buch" bleibt normale Titel-/Autorensuche
        assertEquals(ResolvedIntent.SearchAudiobook("tolstoi"), resolver.resolve("suche tolstoi"))
        val einzelbuch = resolver.resolve("hörbuch über tolstoi")
        assertTrue(einzelbuch is ResolvedIntent.SearchAudiobook)
    }

    @Test
    fun `Lautstaerke lauter und leiser`() {
        assertEquals(ResolvedIntent.VolumeUp, resolver.resolve("lauter"))
        assertEquals(ResolvedIntent.VolumeUp, resolver.resolve("mach lauter"))
        assertEquals(ResolvedIntent.VolumeDown, resolver.resolve("leiser"))
        assertEquals(ResolvedIntent.VolumeDown, resolver.resolve("etwas leiser"))
    }

    @Test
    fun `Lautstaerke als Stufe 1 bis 10`() {
        assertEquals(ResolvedIntent.SetVolume(10), resolver.resolve("lautstärke eins"))
        assertEquals(ResolvedIntent.SetVolume(50), resolver.resolve("lautstärke fünf"))
        assertEquals(ResolvedIntent.SetVolume(100), resolver.resolve("lautstärke zehn"))
        assertEquals(ResolvedIntent.SetVolume(70), resolver.resolve("lautstärke auf 7"))
    }

    @Test
    fun `Lautstaerke als Prozentwert`() {
        assertEquals(ResolvedIntent.SetVolume(70), resolver.resolve("lautstärke auf 70 prozent"))
        assertEquals(ResolvedIntent.SetVolume(35), resolver.resolve("lautstärke 35 prozent"))
    }

    @Test
    fun `Stummschalten`() {
        assertEquals(ResolvedIntent.SetVolume(0), resolver.resolve("ton aus"))
        assertEquals(ResolvedIntent.SetVolume(0), resolver.resolve("stumm"))
        assertEquals(ResolvedIntent.SetVolume(0), resolver.resolve("lautstärke aus"))
        assertEquals(ResolvedIntent.SetVolume(0), resolver.resolve("lautstärke 0"))
    }

    @Test
    fun `Schlaf-Timer`() {
        assertEquals(ResolvedIntent.SleepTimer(20), resolver.resolve("timer 20 min"))
    }

    @Test
    fun `Schlafmodus aktivieren`() {
        assertEquals(ResolvedIntent.SleepMode, resolver.resolve("schlafmodus"))
        assertEquals(ResolvedIntent.SleepMode, resolver.resolve("aktiviere den schlafmodus"))
        assertEquals(ResolvedIntent.SleepMode, resolver.resolve("gute nacht"))
        assertEquals(ResolvedIntent.SleepMode, resolver.resolve("schlafenszeit"))
    }

    @Test
    fun `Schlafmodus beenden`() {
        assertEquals(ResolvedIntent.SleepModeOff, resolver.resolve("schlafmodus aus"))
        assertEquals(ResolvedIntent.SleepModeOff, resolver.resolve("wach auf"))
        assertEquals(ResolvedIntent.SleepModeOff, resolver.resolve("licht an"))
    }

    @Test
    fun `Schlafmodus verwechselt sich nicht mit dem Schlaf-Timer`() {
        assertEquals(ResolvedIntent.SleepTimer(20), resolver.resolve("schlaf timer 20 min"))
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
    fun `Kapitel schlaegt Zurueckspulen`() {
        // "zurück" gehört sonst zum Spulen – sobald "Kapitel" fällt, gewinnt
        // die Kapitelnavigation. "nächste meldung" ohne Kapitelbezug ist kein
        // lokaler Befehl mehr (News laufen über Claude, siehe `News gehen
        // komplett an Ebene 2`).
        assertEquals(ResolvedIntent.NextChapter, resolver.resolve("nächstes kapitel"))
        assertEquals(ResolvedIntent.PreviousChapter, resolver.resolve("ein kapitel zurück"))
        assertNull(resolver.resolve("nächste meldung"))
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
    fun `Kontakte von der SIM importieren`() {
        assertEquals(ResolvedIntent.ImportSimContacts, resolver.resolve("kontakte von der sim übernehmen"))
        assertEquals(ResolvedIntent.ImportSimContacts, resolver.resolve("kontakte von meiner sim importieren"))
        assertEquals(ResolvedIntent.ImportSimContacts, resolver.resolve("sim kontakte importieren"))
    }

    @Test
    fun `Kontakte aus Datei importieren`() {
        assertEquals(ResolvedIntent.ImportVcardContacts, resolver.resolve("kontakte aus einer datei importieren"))
        assertEquals(ResolvedIntent.ImportVcardContacts, resolver.resolve("vcard importieren"))
    }

    @Test
    fun `Kontakt-Import kollidiert nicht mit Anruf oder SMS`() {
        assertEquals(ResolvedIntent.Call("boris"), resolver.resolve("ruf boris an"))
        val sms = resolver.resolve("schreib boris: bin gleich da")
        assertTrue(sms is ResolvedIntent.SendSms)
    }

    @Test
    fun `Uhrzeit abfragen`() {
        assertEquals(ResolvedIntent.Time, resolver.resolve("wie spät ist es"))
    }

    @Test
    fun `Datum abfragen`() {
        assertEquals(ResolvedIntent.Date, resolver.resolve("welches datum haben wir heute"))
        assertEquals(ResolvedIntent.Date, resolver.resolve("welcher tag ist heute"))
        assertEquals(ResolvedIntent.Date, resolver.resolve("der wievielte ist heute"))
    }

    @Test
    fun `Erinnerungen auflisten und loeschen`() {
        assertEquals(ResolvedIntent.ListReminders, resolver.resolve("welche erinnerungen habe ich"))
        assertEquals(ResolvedIntent.ClearReminders, resolver.resolve("lösche alle erinnerungen"))
    }

    @Test
    fun `Termin anlegen`() {
        val intent = resolver.resolve("trage einen termin ein für nächsten montag: zahnarzt")
        assertTrue(intent is ResolvedIntent.SetCalendarEvent)
    }

    @Test
    fun `Kalender anzeigen`() {
        assertEquals(ResolvedIntent.ShowCalendar, resolver.resolve("zeig mir den kalender"))
        assertEquals(ResolvedIntent.ShowCalendar, resolver.resolve("was sind meine nächsten termine"))
    }

    @Test
    fun `Kalender verstecken`() {
        assertEquals(ResolvedIntent.HideCalendar, resolver.resolve("verstecke den kalender"))
    }

    @Test
    fun `Termine loeschen kollidiert nicht mit Erinnerungen loeschen`() {
        assertEquals(ResolvedIntent.ClearCalendarEvents, resolver.resolve("lösche meine termine"))
        assertEquals(ResolvedIntent.ClearReminders, resolver.resolve("lösche meine erinnerungen"))
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

    /**
     * Abgrenzung zum Dokument-Vorlesen: "Zeitung" gehört zur Kamera, nicht zu
     * den Nachrichten – resolveDocument steht in der Kette vorher.
     */
    @Test
    fun `lies mir die Zeitung vor bleibt Dokument`() {
        assertEquals(ResolvedIntent.ReadDocument, resolver.resolve("lies mir die Zeitung vor"))
    }

    /**
     * "weiter" muss global das Hörbuch fortsetzen. Eine frühere Fassung von
     * resolveNews erzeugte daraus NextNews und stand vor resolveAudiobook –
     * falls Nachrichten je wieder lokal aufgelöst werden, darf das nicht
     * zurückkommen. NextNews entsteht ausschließlich im Folgefenster
     * (LauncherActivity.mapNewsFollowUp).
     */
    @Test
    fun `weiter bleibt Hoerbuch-Fortsetzen`() {
        assertEquals(ResolvedIntent.ResumeAudiobook, resolver.resolve("weiter"))
    }

    /**
     * Frei gestellte Fragen nach der Bibliothek. "Welche Hörbücher habe ich"
     * funktionierte schon, "was kann ich heute hören" fiel bis 2026-08-30 an
     * Claude durch, obwohl es dieselbe Frage ist.
     */
    @Test
    fun `freie Fragen nach der Bibliothek landen bei ListAudiobooks`() {
        listOf(
            "welche hörbücher habe ich",
            "was kann ich heute hören",
            "was kann ich hören",
            "was gibt es zu hören",
            "was könnte ich mir anhören",
        ).forEach {
            assertEquals(it, ResolvedIntent.ListAudiobooks, resolver.resolve(it))
        }
    }

    /**
     * Offene Suchbitte ohne Suchbegriff → Rückfrage statt Blindsuche.
     * Vorher machte resolveAudiobookSearch aus "such mir ein Hörbuch" die
     * LibriVox-Anfrage "mir ein hörbuch".
     */
    @Test
    fun `offene Suchbitte fragt nach dem Thema`() {
        listOf(
            "kannst du ein hörbuch für mich suchen",
            "such mir ein hörbuch",
            "suche ein hörbuch",
            "finde mir ein hörbuch",
            "kannst du mir ein buch empfehlen",
        ).forEach {
            assertEquals(it, ResolvedIntent.AskAudiobookTopic, resolver.resolve(it))
        }
    }

    /** Konkrete Suchen dürfen NICHT in der Rückfrage landen. */
    @Test
    fun `konkrete Hoerbuchsuche bleibt direkte Suche`() {
        // Entscheidend ist nur, dass die Rückfrage NICHT greift. Wie der
        // Suchbegriff genau zugeschnitten wird, ist bestehendes Verhalten
        // von resolveAudiobookSearch (siehe TODO.md).
        assertTrue(resolver.resolve("suche hörbuch von tolstoi") is ResolvedIntent.SearchAudiobook)
        assertTrue(resolver.resolve("suche tolstoi") is ResolvedIntent.SearchAudiobook)
    }

    /**
     * Regression 2026-08-30, am Gerät belegt: der SMS-Text wurde aus der
     * kleingeschriebenen Eingabe geschnitten und so auch versendet. Aus
     * "schreib mike: Testnachricht von Lina" wurde die reale SMS
     * "testnachricht von lina". Der Text geht wortwörtlich an eine andere
     * Person, und der blinde Absender kann das Ergebnis nicht prüfen.
     */
    @Test
    fun `SMS-Text behaelt Gross- und Kleinschreibung`() {
        val intent = resolver.resolve("schreib Mike: Testnachricht von Lina")
        assertEquals(ResolvedIntent.SendSms("Mike", "Testnachricht von Lina"), intent)
    }

    @Test
    fun `SMS wird auch bei grossgeschriebenem Befehl erkannt`() {
        val intent = resolver.resolve("Schreib Ulla: Bin gleich da, bis später!")
        assertEquals(ResolvedIntent.SendSms("Ulla", "Bin gleich da, bis später!"), intent)
    }

    @Test
    fun `Antwort behaelt Gross- und Kleinschreibung`() {
        assertEquals(
            ResolvedIntent.ReplySms("Ja gerne, bis Montag"),
            resolver.resolve("Antwort: Ja gerne, bis Montag"),
        )
    }

    /** Satzzeichen und Umlaute im Nachrichtentext dürfen nicht verlorengehen. */
    @Test
    fun `Satzzeichen und Umlaute im SMS-Text bleiben erhalten`() {
        val intent = resolver.resolve("schreib Dirk: Grüße an Käthe – wir sehen uns um 18:30!")
        assertEquals(
            ResolvedIntent.SendSms("Dirk", "Grüße an Käthe – wir sehen uns um 18:30!"),
            intent,
        )
    }

}
