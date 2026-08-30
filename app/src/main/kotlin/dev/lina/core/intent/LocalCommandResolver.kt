package dev.lina.core.intent

import dev.lina.core.text.GermanNumbers

class LocalCommandResolver : IntentResolver {

    override fun resolve(input: String): ResolvedIntent? {
        val normalized = input.trim().lowercase()

        return resolveTime(normalized)
            ?: resolveDate(normalized)
            ?: resolveCalendar(normalized)
            ?: resolveReminder(normalized)
            ?: resolveContactImport(normalized)
            ?: resolveHelperCall(normalized)
            ?: resolveCall(normalized)
            ?: resolveSms(normalized)
            ?: resolveDocument(normalized)
            ?: resolveCallControl(normalized)
            ?: resolveSleepMode(normalized)
            ?: resolveAudiobook(normalized)
            ?: resolveStop(normalized)
    }

    /**
     * Dokument-Vorlesen per Kamera. Steht NACH resolveSms, damit
     * "lies meine Nachrichten" weiterhin die SMS-Funktion trifft.
     */
    private fun resolveDocument(input: String): ResolvedIntent? = when {
        input.matches(
            Regex(
                """.*(?:post|brief|briefe|zeitung|magazin|dokument|zettel|""" +
                    """schreiben|rechnung|seite)\b.*"""
            )
        ) && input.matches(Regex(""".*(?:lies|lese|vorlesen|liest|was steht).*""")) ->
            ResolvedIntent.ReadDocument
        input.matches(Regex(""".*was steht (?:da|hier|drauf|auf dem blatt).*""")) ->
            ResolvedIntent.ReadDocument
        input.matches(Regex(""".*lies (?:mir )?(?:das|es) (?:mal )?vor.*""")) ->
            ResolvedIntent.ReadDocument
        else -> null
    }

    /**
     * Erinnerungen. Steht vor resolveCall, weil "erinnere mich ... an Boris"
     * sonst als Anruf-Muster missverstanden werden könnte. "Termin(e)" gehört
     * nicht mehr hierher (siehe resolveCalendar, das in der Kette zuerst
     * geprüft wird) – die Regexe unten reagieren bewusst nur noch auf
     * "Erinnerung(en)", damit "lösche meine Termine" nicht hier landet.
     */
    private fun resolveReminder(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:lösche|loesche|entferne).*erinnerung.*""")) ->
            ResolvedIntent.ClearReminders
        input.matches(
            Regex(""".*(?:welche|meine|alle)\s+erinnerungen.*""")
        ) || input.matches(Regex(""".*woran.*erinner.*""")) ->
            ResolvedIntent.ListReminders
        input.matches(Regex("""^(?:bitte\s+)?erinner\w*\s+mich\b.*""")) ||
            input.matches(Regex(""".*(?:stell|setz)\w*\s+(?:mir\s+)?(?:einen?\s+)?(?:wecker|erinnerung|timer)\b.*""")) ->
            ResolvedIntent.SetReminder(input)
        else -> null
    }

    private fun resolveDate(input: String): ResolvedIntent? = when {
        input.matches(
            Regex(""".*(?:welches datum|welcher tag ist heute|der wievielte|was für ein tag|was fuer ein tag).*""")
        ) -> ResolvedIntent.Date
        else -> null
    }

    /**
     * Termine (Kalender) – bewusst VOR resolveReminder in der Erkennungskette,
     * damit "Termin(e)"-Wortschatz nicht vom Erinnerungs-Muster mitgerissen
     * wird (das reagierte früher ebenfalls auf "termine?").
     */
    private fun resolveCalendar(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:lösche|loesche|entferne).*termine?.*""")) ->
            ResolvedIntent.ClearCalendarEvents
        input.matches(Regex(""".*versteck\w*.*kalender.*""")) ->
            ResolvedIntent.HideCalendar
        input.matches(Regex(""".*(?:zeig|zeige)\w*.*kalender.*""")) ||
            input.matches(Regex(""".*(?:nächste|naechste|kommende)\w*\s+termine.*""")) ->
            ResolvedIntent.ShowCalendar
        input.matches(Regex(""".*\b(?:trag|eintrag|merk)\w*\s+.*\btermin.*""")) ->
            ResolvedIntent.SetCalendarEvent(input)
        else -> null
    }

    /**
     * Kontakt-Import (SIM-Karte oder vCard-Datei). Steht vor resolveCall/
     * resolveSms, damit "Kontakte importieren" nicht versehentlich als
     * Anruf-/SMS-Muster fehlinterpretiert wird (beide brauchen ohnehin einen
     * konkreten Namen/eine Nummer, "Kontakte" allein matcht dort nicht, aber
     * so bleibt die Absicht klar getrennt).
     */
    private fun resolveContactImport(input: String): ResolvedIntent? = when {
        input.matches(
            Regex(""".*kontakte.*(?:von der|von meiner)\s+sim.*(?:übernehmen|uebernehmen|importieren|holen).*""")
        ) || input.matches(Regex(""".*sim.*kontakte.*(?:übernehmen|uebernehmen|importieren|holen).*""")) ->
            ResolvedIntent.ImportSimContacts
        input.matches(Regex(""".*kontakte.*(?:aus einer|aus der)\s+datei\s+(?:importieren|laden).*""")) ||
            input.matches(Regex(""".*vcard.*(?:importieren|laden).*""")) ->
            ResolvedIntent.ImportVcardContacts
        else -> null
    }

    /**
     * Menschliche Sehhilfe per Be My Eyes (ADR-033). Steht VOR resolveCall,
     * sonst würde "ruf einen Helfer an" dessen Kontaktname-Muster treffen
     * (Kontaktsuche nach "einen Helfer"). Lina kann den Anruf nicht selbst
     * absetzen – Be My Eyes hat keine offene API dafür (nur das umgekehrte
     * "Specialized Help"-Programm für Unternehmen) – sondern öffnet nur die
     * App; der letzte Tap auf "Call a Volunteer" bleibt bei der Nutzer:in.
     */
    private fun resolveHelperCall(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*\bbe my eyes\b.*""")) ||
            input.matches(
                Regex(""".*\b(?:ruf|rufe|hol|öffne|starte)\w*\b.*\b(?:helfer\w*|freiwilligen)\b.*""")
            ) ||
            input.matches(Regex(""".*\bhilfe\s+beim\s+sehen\b.*""")) ||
            input.matches(Regex(""".*\bsehende\s+hilfe\b.*""")) ->
            ResolvedIntent.CallHelper
        else -> null
    }

    private fun resolveTime(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:wie spät|wie viel uhr|uhrzeit|wie spaet).*""")) ->
            ResolvedIntent.Time
        else -> null
    }

    private fun resolveCall(input: String): ResolvedIntent? {
        val patterns = listOf(
            // Füllwörter dürfen nicht im Namen landen ("ruf mal Boris an")
            Regex("""(?:ruf|rufe)\s+(?:${FUELLWOERTER}\s+)*(.+?)\s+an"""),
            Regex("""(?:anrufen|anruf)\s+(?:${FUELLWOERTER}\s+)*(.+)"""),
            Regex("""kannst du (?:bitte )?(.+?) anrufen"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                return ResolvedIntent.Call(match.groupValues[1].trim())
            }
        }
        return null
    }

    private fun resolveSms(input: String): ResolvedIntent? {
        val sendPatterns = listOf(
            Regex("""(?:schreib|schreibe|sende|send)\s+(.+?)[\s:]+(.+)"""),
            Regex("""(?:sms|nachricht)\s+an\s+(.+?)[\s:]+(.+)"""),
        )
        for (pattern in sendPatterns) {
            pattern.find(input)?.let { match ->
                return ResolvedIntent.SendSms(
                    match.groupValues[1].trim(),
                    match.groupValues[2].trim(),
                )
            }
        }

        if (input.matches(Regex(""".*(?:lies|lese|liest|zeig).*(?:nachricht|sms|nachrichten).*"""))) {
            return ResolvedIntent.ReadSms
        }

        Regex("""(?:antwort|antworte)[\s:]+(.+)""").find(input)?.let { match ->
            return ResolvedIntent.ReplySms(match.groupValues[1].trim())
        }

        return null
    }

    private fun resolveCallControl(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:annehmen|rangehen|abnehmen|drangehen).*""")) ->
            ResolvedIntent.AcceptCall
        input.matches(Regex(""".*(?:ablehnen|wegdrücken|nicht rangehen|ignorieren).*""")) ->
            ResolvedIntent.RejectCall
        input.matches(Regex(""".*(?:auflegen|beenden|schluss|tschüss).*""")) ->
            ResolvedIntent.HangUp
        else -> null
    }

    /**
     * Schlafmodus: dimmt den Bildschirm und senkt die Lautstärke – anders als
     * der Hörbuch-Schlaf-Timer (`resolveSleepTimer`, braucht eine Minutenzahl)
     * eine sofortige Umschaltung ohne Zeitangabe. "aus"/"beenden" zuerst
     * geprüft, damit "Schlafmodus aus" nicht als Aktivierung durchgeht.
     */
    private fun resolveSleepMode(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*schlafmodus\s+(?:aus|beenden|deaktivieren)\b.*""")) ||
            input.matches(Regex(""".*\b(?:wach\s*auf|aufwachen|licht an)\b.*""")) ->
            ResolvedIntent.SleepModeOff
        input.matches(Regex(""".*\b(?:schlafmodus|schlafenszeit)\b.*""")) ||
            input.matches(Regex(""".*\bgute\s+nacht\b.*""")) ->
            ResolvedIntent.SleepMode
        else -> null
    }

    private fun resolveAudiobook(input: String): ResolvedIntent? = when {
        // Kapitelbefehle zuerst: "weiter" und "zurück" unten würden sonst
        // "ein Kapitel weiter" als Fortsetzen bzw. Zurückspulen abfangen.
        input.contains("kapitel") -> resolveChapter(input)
        input.matches(Regex(""".*(?:spiel|starte?).*(?:hörbuch|buch|vorlesen).*""")) ->
            ResolvedIntent.PlayAudiobook
        input.matches(Regex(""".*(?:pause|anhalten|halt an).*""")) ->
            ResolvedIntent.PauseAudiobook
        // Wortgrenzen (\b) statt reinem Teilstring-Test: ".*weiter.*" traf sonst
        // auch zusammengesetzte Wörter wie "weiterreden" ("lass uns weiterreden"
        // sollte an Claude gehen, nicht versehentlich das Hörbuch fortsetzen).
        input.matches(Regex(""".*\b(?:weiter|fortsetzen|weiterspielen|weiterhören|weitermachen|resume)\b.*""")) ->
            ResolvedIntent.ResumeAudiobook
        input.matches(Regex(""".*(?:zurück|zurückspulen|\d+\s*sekunden?\s*zurück).*""")) -> {
            val seconds = Regex("""(\d+)\s*sekunden?""").find(input)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 30
            ResolvedIntent.RewindAudiobook(seconds)
        }
        // Stumm zuerst: "lautstärke 0" würde sonst vom Stufen-/Prozent-Muster
        // unten als Randfall mitgenommen – hier ist die Absicht eindeutiger.
        input.matches(Regex(""".*\b(?:ton aus|stumm(?:schalten)?)\b.*""")) ||
            input.matches(Regex(""".*lautstärke\s+(?:aus|null|0)\b.*""")) ->
            ResolvedIntent.SetVolume(0)
        // Wortgrenzen wie bei "weiter" – reiner Teilstring-Test wäre riskant
        input.matches(Regex(""".*\blauter\b.*""")) -> ResolvedIntent.VolumeUp
        input.matches(Regex(""".*\bleiser\b.*""")) -> ResolvedIntent.VolumeDown
        input.matches(Regex(""".*(?:was höre ich|welches buch|was läuft|was spielt).*""")) ->
            ResolvedIntent.AudiobookInfo
        // Offene Suchbitte OHNE konkreten Titel/Autor. Muss VOR
        // resolveAudiobookSearch greifen: dessen Muster "such\s+(.+)" würde
        // aus "such mir ein Hörbuch" den Suchbegriff "mir ein hörbuch" machen
        // und den bei LibriVox abfeuern. Der von/über-Ausschluss lässt
        // konkrete Anfragen ("such ein Hörbuch von Tolstoi") durchfallen.
        input.matches(Regex(""".*\b(?:such\w*|find\w*|empfehl\w*|empfiehl\w*)\b.*""")) &&
            input.matches(Regex(""".*\b(?:hörbuch|hörbücher|buch|bücher)\b.*""")) &&
            !input.matches(Regex(""".*\b(?:von|über|ueber|mit|titel|autor|thema|genre)\b.*""")) ->
            ResolvedIntent.AskAudiobookTopic
        // "Was kann ich heute hören?" ist dieselbe Frage wie "welche Hörbücher
        // habe ich" – nur so, wie man sie tatsächlich stellt.
        input.matches(Regex(""".*was (?:kann|könnte|koennte|gibt es|gibts).*\b(?:hören|hoeren|anhören|anhoeren)\b.*""")) ||
            input.matches(Regex(""".*(?:welche hörbücher|meine hörbücher|hörbuch(?:liste|er)|bibliothek).*""")) ->
            ResolvedIntent.ListAudiobooks
        else -> resolveVolumeLevel(input) ?: resolveSleepTimer(input) ?:
            resolveAudiobookGenreSearch(input) ?: resolveAudiobookSearch(input)
    }

    /**
     * Suche nach Thema/Genre statt Titel/Autor, z.B. "Hörbücher zum Thema
     * Politik". Muss vor [resolveAudiobookSearch] geprüft werden: dessen
     * `(?:such|suche|finde?)\s+(?:hörbuch\s+)?(.+)`-Muster würde
     * "suche hörbücher zum Thema Segeln" sonst komplett als Titel-/Autoren-
     * suche verschlucken (die Plural-Form "hörbuch**er**" passt nicht auf das
     * optionale Singular-"hörbuch" davor). Bewusst nur Plural
     * ("hörbücher"/"bücher") als Auslöser – die bestehende Singular-Form
     * ("ein hörbuch über X") bleibt normale Titel-/Autorensuche.
     */
    private fun resolveAudiobookGenreSearch(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""hörbücher?\s+zum\s+thema\s+(.+)"""),
            Regex("""(?:hörbücher|bücher)\s+(?:über|zu)\s+(?:das\s+thema\s+)?(.+)"""),
            Regex("""(?:gibt es|hast du)\s+hörbücher\s+(?:über|zu)\s+(.+?)\??$"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                val topic = match.groupValues[1].trim()
                if (topic.isNotBlank()) {
                    return ResolvedIntent.SearchAudiobookByGenre(topic)
                }
            }
        }
        return null
    }

    /**
     * Direkter Lautstärke-Sollwert: "Lautstärke (auf) fünf" = Stufe 1–10
     * (=10–100%), oder "Lautstärke (auf) 70 Prozent" = direkter Prozentwert.
     * Prozent-Muster zuerst, sonst würde "70 Prozent" die Stufen-Regex mit "70"
     * als (ungültige) Stufe treffen und leer ausgehen, statt zum Prozent-Zweig
     * durchzufallen.
     */
    private fun resolveVolumeLevel(input: String): ResolvedIntent? {
        Regex("""lautstärke\s+(?:auf\s+)?(\d{1,3})\s*(?:prozent|%)""").find(input)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return ResolvedIntent.SetVolume(it.coerceIn(0, 100)) }
        }
        Regex("""lautstärke\s+(?:auf\s+)?(\d{1,2}|${GermanNumbers.ALTERNATION})\b""").find(input)?.let { match ->
            GermanNumbers.parse(match.groupValues[1])?.toInt()?.let { level ->
                if (level in 1..10) return ResolvedIntent.SetVolume(level * 10)
            }
        }
        return null
    }

    /**
     * Kapitelnavigation in DAISY- und LibriVox-Büchern. Wird nur betreten,
     * wenn "kapitel" im Satz steht.
     */
    private fun resolveChapter(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:welche|wie viele|liste|übersicht|alle)\s*.*kapitel.*""")) ||
            input.matches(Regex(""".*kapitel(?:liste|übersicht).*""")) ->
            ResolvedIntent.ListChapters

        input.matches(Regex(""".*(?:nächste|nächstes|weiter|vorwärts|überspring\w*).*kapitel.*""")) ||
            input.matches(Regex(""".*kapitel\s+(?:weiter|vor|vorwärts).*""")) ->
            ResolvedIntent.NextChapter

        input.matches(Regex(""".*(?:vorherige?s|vorige?s|letztes|davor).*kapitel.*""")) ||
            input.matches(Regex(""".*kapitel\s+zurück.*""")) ->
            ResolvedIntent.PreviousChapter

        else -> {
            // "Kapitel 7", "spring zu Kapitel drei"
            Regex("""kapitel\s+(\d+|${GermanNumbers.ALTERNATION})\b""").find(input)
                ?.let { match ->
                    GermanNumbers.parse(match.groupValues[1])
                        ?.let { ResolvedIntent.GoToChapter(it.toInt()) }
                }
        }
    }

    private fun resolveSleepTimer(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""(?:stopp?|aufhören|ende)\s+in\s+(\d+)\s*min"""),
            Regex("""(?:schlaf|sleep)\s*(?:timer|modus)?\s*(\d+)\s*min"""),
            Regex("""in\s+(\d+)\s*min(?:uten?)?\s+(?:stopp?en?|aufhören|aus)"""),
            Regex("""timer\s+(\d+)\s*min"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: return null
                return ResolvedIntent.SleepTimer(minutes)
            }
        }
        return null
    }

    private fun resolveAudiobookSearch(input: String): ResolvedIntent? {
        val patterns = listOf(
            Regex("""(?:such|suche|finde?)\s+(?:hörbuch\s+)?(.+)"""),
            Regex("""(?:hörbuch|buch)\s+(?:von|über)\s+(.+)"""),
            // "gibt es" nur mit Hörbuch-Bezug – sonst frisst das Muster Fragen
            // wie "Was gibt es Neues aus Hannover?" (gehört zu Ebene 2/Claude)
            Regex("""(?:gibt es|hast du)\s+(?:etwas\s+|was\s+)?von\s+(.+?)(?:\s+als hörbuch)?$"""),
            Regex("""(?:gibt es|hast du)\s+(.+?)\s+als hörbuch\??$"""),
        )
        for (pattern in patterns) {
            pattern.find(input)?.let { match ->
                val query = match.groupValues[1].trim()
                if (query.isNotBlank() && query.length >= 2) {
                    return ResolvedIntent.SearchAudiobook(query)
                }
            }
        }
        return null
    }

    private fun resolveStop(input: String): ResolvedIntent? = when {
        input.matches(Regex(""".*(?:stopp|stop|sei still|ruhe|halt|aufhören|still).*""")) ->
            ResolvedIntent.Stop
        else -> null
    }

    private companion object {
        /** Höflichkeits- und Füllwörter, die vor einem Kontaktnamen stehen können. */
        const val FUELLWOERTER = "mal|bitte|doch|kurz|schnell|jetzt"
    }
}
