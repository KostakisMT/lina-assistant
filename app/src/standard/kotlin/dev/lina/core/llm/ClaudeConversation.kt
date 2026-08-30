package dev.lina.core.llm

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.WebSearchTool20260209
import dev.lina.core.intent.ResolvedIntent

/**
 * [ConversationEngine]-Implementierung über die Claude API (ADR-017).
 * Erkennt Gerätebefehle, die die lokale Ebene 1 nicht verstanden hat (z.B.
 * verstümmelte STT-Transkripte wie "Rumfe, Boris an"), per Tool-Definition
 * und reicht sie als [ResolvedIntent] zur lokalen Ausführung zurück.
 *
 * Blockierend – immer von einem Hintergrund-Thread aufrufen.
 */
class ClaudeConversation(
    apiKey: String,
    /** Kontaktnamen vom Gerät – helfen Claude, verstümmelte STT-Namen zuzuordnen. */
    contactNames: List<String> = emptyList(),
    /** Interessen des Nutzers für passendere Konversation (aus lokalem Profil). */
    interests: String = "",
    /** Wohnregion für Wetter/Regionalnachrichten via Websuche (aus lokalem Profil). */
    region: String = "",
) : ConversationEngine {

    private val client: AnthropicClient =
        AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    private val systemPrompt: String = buildString {
        append(BASE_PROMPT)
        if (contactNames.isNotEmpty()) {
            append("\n- Die Kontakte des Nutzers heißen: ")
            append(contactNames.joinToString(", "))
            append(". Verstümmelte Namen aus der Spracherkennung ordnest du dem ")
            append("ähnlichsten Kontakt zu.")
        }
        if (interests.isNotBlank()) {
            append("\n- Der Nutzer interessiert sich für: ").append(interests).append(".")
        }
        if (region.isNotBlank()) {
            append("\n- Der Nutzer wohnt in der Region ").append(region)
            append(". Bei Wetter oder Regionalem ohne Ortsangabe ist diese Region gemeint.")
        }
        append(
            "\n- Für Aktuelles (Wetter, Nachrichten, Ereignisse) nutzt du die " +
                "Websuche. Fragt der Nutzer nach Nachrichten oder was es Neues " +
                "gibt, gibst du einen kurzen gesprochenen Überblick: zwei bis " +
                "drei Meldungen, die für ihn wirklich relevant sind – mische " +
                "Wichtiges aus seiner Region mit Bedeutendem aus aller Welt, je " +
                "ein bis zwei Sätze, ohne Quellen-URLs. Danach hängst du genau " +
                "einen kurzen Satz an, dass er zu jedem Thema nachfragen kann, " +
                "wenn er mehr hören will. Fragt er zu einem Thema nach, erzählst " +
                "du ausführlicher."
        )
        append(
            "\n- Nutze dein Wissen über den Nutzer unaufdringlich: Es prägt Tiefe " +
                "und Tonfall deiner Antworten, aber du erwähnst seine Interessen " +
                "oder Kontakte nicht von dir aus und sagst nie Dinge wie " +
                "\"das passt zu deinem Interesse an ...\". Keine ungefragten " +
                "Zusatzangebote am Ende der Antwort – einzige Ausnahme ist der " +
                "eine Rückfrage-Hinweis beim Nachrichten-Überblick."
        )
    }

    /** Dialoggedächtnis: nur Textwechsel, damit die History API-gültig bleibt. */
    private val history = ArrayDeque<MessageParam>()

    /**
     * [freshWakeWord] = true: die App weiß mit Sicherheit, dass der Nutzer
     * gerade "Hey Lina" gesagt hat (nicht Claudes Vermutung). Verhindert, dass
     * Einladungen wie "Lass uns reden" fälschlich als Raumgespräch erkannt
     * werden (gespraech_beenden) – ein beobachtetes Fehlverhalten trotz
     * korrekter Transkription.
     */
    override fun ask(input: String, freshWakeWord: Boolean): LinaReply {
        val effectiveInput = if (freshWakeWord) "[Weckwort erkannt] $input" else input
        history.addLast(message(MessageParam.Role.USER, effectiveInput))
        trimHistory()
        return try {
            val response = client.messages().create(buildParams())
            Log.d(
                TAG,
                "stopReason=${response.stopReason()} blocks=" +
                    response.content().joinToString(",") { b ->
                        when {
                            b.text().isPresent -> "text"
                            b.toolUse().isPresent -> "toolUse(${b.toolUse().get().name()})"
                            b.serverToolUse().isPresent -> "serverToolUse"
                            b.webSearchToolResult().isPresent -> "searchResult"
                            else -> "sonstig"
                        }
                    }
            )

            for (block in response.content()) {
                val toolUse = block.toolUse()
                if (toolUse.isPresent) {
                    if (toolUse.get().name() == "gespraech_beenden") {
                        // Nicht an Lina gerichtet – aus dem Gedächtnis entfernen
                        history.removeLast()
                        return LinaReply.End
                    }
                    val intent = toIntent(toolUse.get())
                    if (intent != null) {
                        history.addLast(
                            message(MessageParam.Role.ASSISTANT, "Ich habe den Befehl ausgeführt.")
                        )
                        return LinaReply.Do(intent)
                    }
                }
            }

            val text = response.content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
            // pause_turn: Server-Tool-Lauf (Websuche) wurde unterbrochen. Voller
            // Resume bräuchte manuelle Block-Rekonstruktion (Java-SDK) – stattdessen
            // ehrlich abbrechen; Teiltext gibt es bei pause_turn praktisch nie.
            val paused = response.stopReason()
                .map { it == StopReason.PAUSE_TURN }
                .orElse(false)
            if (paused && text.isEmpty()) {
                Log.w(TAG, "pause_turn ohne Text – Suche abgebrochen")
                history.removeLast()
                return LinaReply.Error(
                    "Die Suche dauert gerade zu lange. Frag mich gleich noch einmal."
                )
            }
            if (text.isEmpty()) {
                history.removeLast()
                return LinaReply.Error("Darauf habe ich gerade keine Antwort.")
            }
            history.addLast(message(MessageParam.Role.ASSISTANT, text))
            LinaReply.Say(text)
        } catch (e: RateLimitException) {
            Log.e(TAG, "Claude-Anfrage fehlgeschlagen (Rate-Limit)", e)
            history.removeLast()
            LinaReply.Error("Gerade ist viel los bei mir. Versuch es gleich noch einmal.")
        } catch (e: AnthropicServiceException) {
            // 4xx/5xx vom Dienst (z.B. kein Guthaben, ungültiger Key) – kein Netzproblem
            Log.e(TAG, "Claude-Anfrage fehlgeschlagen (Dienst)", e)
            history.removeLast()
            LinaReply.Error(
                "Mein Sprachdienst meldet ein Problem. " +
                    "Bitte sag deinem Betreuer Bescheid."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Claude-Anfrage fehlgeschlagen (Verbindung)", e)
            history.removeLast()
            LinaReply.Error(
                "Ich kann gerade nicht nachdenken. " +
                    "Wahrscheinlich fehlt die Internetverbindung."
            )
        }
    }

    override fun reset() = history.clear()

    /**
     * Liest ein fotografiertes Dokument vor (Vision, einmalig und zustandslos –
     * rührt die Gesprächs-History nicht an, damit das große Bild nicht jede
     * Folgeanfrage aufbläht).
     *
     * [verbatim] = false: nur das Relevante (bei Briefen erst Absender + Thema,
     * dann Inhalt; Anschriften/Fußzeilen/Werbung weglassen).
     * [verbatim] = true: der vollständige Text.
     *
     * Blockierend – vom Hintergrund-Thread aufrufen.
     */
    override fun readDocument(jpegBytes: ByteArray, verbatim: Boolean): DocumentReadResult {
        fun result(reply: LinaReply, suggestedEvent: SuggestedCalendarEvent? = null) =
            DocumentReadResult(reply, suggestedEvent)

        return try {
            val base64 = java.util.Base64.getEncoder().encodeToString(jpegBytes)
            val image = ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(
                        Base64ImageSource.builder()
                            .data(base64)
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .build()
                    )
                    .build()
            )
            val instruction = ContentBlockParam.ofText(
                TextBlockParam.builder()
                    .text(if (verbatim) DOC_PROMPT_VERBATIM else DOC_PROMPT_RELEVANT)
                    .build()
            )
            val paramsBuilder = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(DOC_MAX_TOKENS)
                .thinking(ThinkingConfigDisabled.builder().build())
                .systemOfTextBlockParams(
                    listOf(TextBlockParam.builder().text(DOC_SYSTEM_PROMPT).build())
                )
                .addMessage(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(listOf(image, instruction))
                        .build()
                )
            // Isoliert von TOOLS/buildParams() – betrifft nur diesen einen Aufruf,
            // rührt den freien Konversationspfad (ask()) nicht an.
            DOC_TOOLS.forEach { paramsBuilder.addTool(it) }

            val response = client.messages().create(paramsBuilder.build())
            val text = response.content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
            val suggestedEvent = response.content()
                .mapNotNull { it.toolUse().orElse(null) }
                .firstOrNull { it.name() == "termin_erkannt" }
                ?.let { toolUse ->
                    fun arg(name: String): String? =
                        toolUse._input().asObject().orElse(null)?.get(name)?.asString()?.orElse(null)
                    val titel = arg("titel")
                    val datum = arg("datum")
                    if (titel != null && datum != null) {
                        SuggestedCalendarEvent(titel, datum, arg("zeit")?.takeIf { it.isNotBlank() })
                    } else null
                }
            Log.d(
                TAG,
                "readDocument(verbatim=$verbatim): ${text.length} Zeichen, " +
                    "termin_erkannt=${suggestedEvent != null}",
            )
            if (text.isEmpty()) {
                result(LinaReply.Error("Ich konnte auf dem Bild nichts erkennen."))
            } else {
                result(LinaReply.Say(text), suggestedEvent)
            }
        } catch (e: RateLimitException) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Rate-Limit)", e)
            result(LinaReply.Error("Gerade ist viel los bei mir. Versuch es gleich noch einmal."))
        } catch (e: AnthropicServiceException) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Dienst)", e)
            result(
                LinaReply.Error(
                    "Mein Sprachdienst meldet ein Problem. Bitte sag deinem Betreuer Bescheid."
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Verbindung)", e)
            result(
                LinaReply.Error(
                    "Ich kann das Dokument gerade nicht auswerten. " +
                        "Wahrscheinlich fehlt die Internetverbindung."
                )
            )
        }
    }

    private fun buildParams(): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(500L)
            // Sonnet 5 denkt sonst per Default adaptiv mit – kostet Sprachassistenz-
            // Latenz und Thinking-Tokens zählen gegen maxTokens
            .thinking(ThinkingConfigDisabled.builder().build())
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()
                )
            )
            .messages(history.toList())
        TOOLS.forEach { builder.addTool(it) }
        // Server-Tool: Websuche für Wetter/aktuelle Nachrichten (läuft bei Anthropic)
        builder.addTool(WebSearchTool20260209.builder().maxUses(3L).build())
        return builder.build()
    }

    private fun toIntent(toolUse: ToolUseBlock): ResolvedIntent? {
        fun arg(name: String): String? =
            toolUse._input().asObject().orElse(null)?.get(name)?.asString()?.orElse(null)

        return when (toolUse.name()) {
            "anrufen" -> arg("kontakt")?.let { ResolvedIntent.Call(it) }
            "sms_senden" -> {
                val kontakt = arg("kontakt") ?: return null
                val text = arg("text") ?: return null
                ResolvedIntent.SendSms(kontakt, text)
            }
            "sms_vorlesen" -> ResolvedIntent.ReadSms
            "hoerbuch_abspielen" -> ResolvedIntent.PlayAudiobook
            "hoerbuecher_auflisten" -> ResolvedIntent.ListAudiobooks
            "hoerbuch_suchen" -> arg("suchbegriff")?.let { ResolvedIntent.SearchAudiobook(it) }
            "hoerbuch_thema_suchen" -> arg("thema")?.let { ResolvedIntent.SearchAudiobookByGenre(it) }
            "hoerbuch_pausieren" -> ResolvedIntent.PauseAudiobook
            "hoerbuch_fortsetzen" -> ResolvedIntent.ResumeAudiobook
            "hoerbuch_info" -> ResolvedIntent.AudiobookInfo
            "hoerbuch_zurueckspulen" ->
                ResolvedIntent.RewindAudiobook(arg("sekunden")?.toIntOrNull() ?: 30)
            "kapitel_naechstes" -> ResolvedIntent.NextChapter
            "kapitel_vorheriges" -> ResolvedIntent.PreviousChapter
            "kapitel_springen" -> arg("nummer")?.toIntOrNull()?.let { ResolvedIntent.GoToChapter(it) }
            "kapitel_auflisten" -> ResolvedIntent.ListChapters
            "dokument_vorlesen" -> ResolvedIntent.ReadDocument
            "erinnerung_anlegen" -> {
                // Claude liefert die aufgelöste Zeit; der lokale Parser bleibt
                // erste Wahl, das Tool ist für verstümmelte Eingaben da
                val text = arg("text") ?: return null
                val zeit = arg("zeitpunkt") ?: return null
                ResolvedIntent.SetReminderAt(
                    text = text,
                    isoZeit = zeit,
                    daily = arg("taeglich")?.toBoolean() ?: false,
                )
            }
            "erinnerungen_vorlesen" -> ResolvedIntent.ListReminders
            "termin_anlegen" -> {
                val titel = arg("titel") ?: return null
                val datum = arg("datum") ?: return null
                ResolvedIntent.SetCalendarEventAt(
                    title = titel,
                    isoDatum = datum,
                    isoZeit = arg("zeit")?.takeIf { it.isNotBlank() },
                )
            }
            "stopp" -> ResolvedIntent.Stop
            else -> null
        }
    }

    private fun message(role: MessageParam.Role, text: String): MessageParam =
        MessageParam.builder().role(role).content(text).build()

    private fun trimHistory() {
        while (history.size > MAX_HISTORY) history.removeFirst()
        // History muss mit einer User-Nachricht beginnen
        while (history.isNotEmpty() &&
            history.first().role() == MessageParam.Role.ASSISTANT
        ) {
            history.removeFirst()
        }
    }

    companion object {
        private const val TAG = "ClaudeConversation"
        private const val MODEL = "claude-sonnet-5"
        private const val MAX_HISTORY = 20
        // Dokumente sind länger als Chat-Antworten
        private const val DOC_MAX_TOKENS = 1024L

        private val DOC_SYSTEM_PROMPT = """
            Du bist Lina und liest einem blinden Menschen vor, was auf einem Foto
            zu sehen ist – meist Post, ein Brief, eine Zeitungs- oder Magazinseite,
            die vor dem Tablet liegt. Alles, was du schreibst, wird laut vorgelesen.

            Regeln:
            - Reiner Sprechtext auf Deutsch. Keine Listen, keine Aufzählungszeichen,
              keine Sonderzeichen, kein Markdown, keine Emojis.
            - Sprich Zahlen, Daten und Beträge aus, wie man sie sagt
              (zum Beispiel "zweiundzwanzigster Mai" statt "22.05.").
            - Nenne niemals Bildkoordinaten oder Layout-Details ("oben rechts steht").
            - Ist das Bild leer, unscharf, zu dunkel oder kein Dokument: sag das
              freundlich in einem Satz und schlage vor, das Blatt neu zu legen.
            - Steht im Dokument ein eindeutiger, konkreter Termin oder eine Frist
              (z.B. ein Arzttermin, eine Zahlungsfrist, eine Einladung mit festem
              Datum), rufe zusätzlich zu deinem gesprochenen Text das Werkzeug
              termin_erkannt auf. Bei vagen oder unsicheren Zeitangaben
              ("demnächst", "bald", "in Kürze") rufe es NICHT auf.
        """.trimIndent()

        private val DOC_PROMPT_RELEVANT = """
            Lies mir vor, was hier wichtig ist.

            Bei einem Brief: Sag zuerst in einem Satz, von wem er ist und worum es
            geht. Dann das Wesentliche – Anliegen, Beträge, Fristen, was ich tun
            muss. Lass weg: Anschriftenfelder, Absenderadressen, Briefkopf,
            Betreffzeilen-Wiederholungen, Fußzeilen, Bankverbindungen,
            Registernummern, Kleingedrucktes und Werbung.

            Bei einer Zeitungs- oder Magazinseite: Sag kurz, was für eine Seite das
            ist, dann die Überschrift und den Kern des Artikels. Lass Anzeigen,
            Bildunterschriften und Randnotizen weg.

            Halte dich kurz und klar – so, wie man jemandem am Tisch vorliest.
        """.trimIndent()

        private val DOC_PROMPT_VERBATIM = """
            Lies mir jetzt den vollständigen Text vor, den du auf dem Bild erkennst –
            von oben nach unten, ohne etwas wegzulassen und ohne eigene
            Zusammenfassung. Auch Anschriften, Fußzeilen und Kleingedrucktes.
            Gib den Text als fließenden Sprechtext wieder.
        """.trimIndent()

        /**
         * Ausschließlich für readDocument() – niemals in TOOLS/buildParams()
         * mischen, damit der freie Konversationspfad (ask()) unberührt bleibt.
         */
        private val DOC_TOOLS: List<Tool> = listOf(
            tool(
                "termin_erkannt",
                "Meldet einen im Dokument gefundenen konkreten Termin oder eine Frist.",
                mapOf(
                    "titel" to "Kurze Beschreibung, z.B. \"Zahlungsfrist Stromrechnung\"",
                    "datum" to "Datum als ISO 8601, z.B. 2026-08-15",
                    "zeit" to "Uhrzeit als HH:mm, falls genannt – sonst leer lassen",
                ),
                listOf("titel", "datum"),
            ),
        )

        private val BASE_PROMPT = """
            Du bist Lina, die Sprachassistentin eines blinden Menschen in Deutschland.
            Du läufst auf einem Tablet in seinem Wohnzimmer und sprichst mit ihm über
            Sprachausgabe. Er kann dich nicht sehen und nichts lesen – alles, was du
            sagst, wird vorgelesen.

            Regeln für deine Antworten:
            - Antworte auf Deutsch, warm, freundlich und auf Augenhöhe – wie eine
              gute Bekannte, nicht wie ein Callcenter.
            - Kurz und klar: meist 1 bis 3 Sätze. Keine Listen, keine Sonderzeichen,
              keine Emojis, kein Markdown – reiner Sprechtext.
            - Die Eingaben kommen aus einer Spracherkennung und sind manchmal
              verstümmelt. Errate wohlwollend, was gemeint war. Wenn eine Eingabe
              wie ein Gerätebefehl aussieht (anrufen, SMS, Nachrichten, Hörbuch),
              nutze das passende Werkzeug statt zu antworten.
            - Wenn du etwas nicht weißt oder nicht kannst, sag es ehrlich und kurz.
            - Hörbücher gehören ausdrücklich zu dem, was du KANNST: aufzählen,
              nach Titel, Autor oder Thema suchen (lokal und bei LibriVox),
              abspielen, pausieren, fortsetzen, zurückspulen, Kapitel wechseln
              und auflisten. Sag niemals, du könntest das nicht oder hättest
              keinen Zugriff darauf – nimm das passende Werkzeug. Verstümmelte
              Titel und Namen korrigierst du dabei stillschweigend, ohne den
              Nutzer darauf hinzuweisen ("Teustol" ist Tolstoi, "Privaks" ist
              LibriVox, "führbuch" ist Hörbuch).
            - Wichtig: Das Mikrofon hört nach deinen Antworten automatisch weiter.
              Nicht alles, was du hörst, ist an dich gerichtet! Wirkt die Eingabe
              wie ein Gespräch im Raum, eine Antwort an eine andere Person oder
              Fernsehton, nutze das Werkzeug gespraech_beenden und antworte nicht.
            - Frag bei unklaren oder fragmentarischen Eingaben NICHT nach. Nur wenn
              eine Eingabe erkennbar eine Frage oder Bitte AN DICH ist, darfst du
              um Wiederholung bitten. Alles andere: gespraech_beenden. Eine echte
              Frage kommt wieder – Hineinreden in ein Gespräch stört dagegen sehr.
            - Beginnt eine Eingabe mit "[Weckwort erkannt]": Der Nutzer hat gerade
              "Hey Lina" gesagt – die App weiß das sicher, nicht nur du vermutest
              es. Diese Eingabe ist IMMER an dich gerichtet, auch wenn sie wie eine
              Einladung unter Menschen klingt ("Lass uns reden", "Erzähl mir was").
              Prüfe hier NICHT auf Raumgespräch und nutze gespraech_beenden nicht.
              Ohne diese Markierung gilt die Raumgespräch-Prüfung wie gewohnt.
        """.trimIndent()

        private val TOOLS: List<Tool> = listOf(
            tool(
                "anrufen", "Ruft einen Kontakt an.",
                mapOf("kontakt" to "Name des Kontakts, so wie verstanden"),
                listOf("kontakt"),
            ),
            tool(
                "sms_senden", "Sendet eine SMS an einen Kontakt.",
                mapOf(
                    "kontakt" to "Name des Kontakts",
                    "text" to "Der Nachrichtentext",
                ),
                listOf("kontakt", "text"),
            ),
            tool("sms_vorlesen", "Liest die neuesten SMS vor.", emptyMap(), emptyList()),
            tool("hoerbuch_abspielen", "Spielt das aktuelle Hörbuch ab.", emptyMap(), emptyList()),
            tool(
                "hoerbuecher_auflisten",
                "Zählt auf, welche Hörbücher der Nutzer hat. Nutze dies bei " +
                    "Fragen wie \"welche Hörbücher habe ich\", \"was kann ich " +
                    "heute hören\", \"was gibt es zu hören\" – auch wenn die " +
                    "Spracherkennung sie verstümmelt hat.",
                emptyMap(),
                emptyList(),
            ),
            tool(
                "hoerbuch_suchen",
                "Sucht ein Hörbuch nach Titel oder Autor, in der lokalen " +
                    "Bibliothek und bei LibriVox. Nutze dies bei \"such mir etwas " +
                    "von Tolstoi\" oder \"gibt es Herr und Knecht\". Titel und " +
                    "Namen kommen aus der Spracherkennung oft verstümmelt an – " +
                    "gib sie korrigiert weiter, in richtiger Schreibweise.",
                mapOf(
                    "suchbegriff" to "Titel oder Autor, korrekt geschrieben, " +
                        "z.B. \"Tolstoi\" oder \"Herr und Knecht\"",
                ),
                listOf("suchbegriff"),
            ),
            tool(
                "hoerbuch_thema_suchen",
                "Sucht Hörbücher zu einem Thema oder Genre bei LibriVox, etwa " +
                    "Politik, Abenteuer oder Geschichte. Nutze dies statt " +
                    "hoerbuch_suchen, wenn der Nutzer ein Thema nennt und keinen " +
                    "bestimmten Titel oder Autor.",
                mapOf("thema" to "Das gewünschte Thema, z.B. \"Politik\""),
                listOf("thema"),
            ),
            tool("hoerbuch_pausieren", "Hält das laufende Hörbuch an.", emptyMap(), emptyList()),
            tool(
                "hoerbuch_fortsetzen",
                "Setzt ein angehaltenes Hörbuch fort.",
                emptyMap(),
                emptyList(),
            ),
            tool(
                "hoerbuch_info",
                "Sagt an, welches Buch und welches Kapitel gerade läuft.",
                emptyMap(),
                emptyList(),
            ),
            tool(
                "hoerbuch_zurueckspulen",
                "Spult im laufenden Hörbuch zurück, z.B. \"dreißig Sekunden zurück\".",
                mapOf("sekunden" to "Anzahl Sekunden, Standard 30"),
                emptyList(),
            ),
            tool("kapitel_naechstes", "Springt zum nächsten Kapitel.", emptyMap(), emptyList()),
            tool("kapitel_vorheriges", "Springt zum vorherigen Kapitel.", emptyMap(), emptyList()),
            tool(
                "kapitel_springen",
                "Springt zu einem bestimmten Kapitel, z.B. \"Kapitel drei\".",
                mapOf("nummer" to "Kapitelnummer als Zahl, z.B. \"3\""),
                listOf("nummer"),
            ),
            tool(
                "kapitel_auflisten",
                "Zählt die Kapitel des gerade geladenen Buches auf.",
                emptyMap(),
                emptyList(),
            ),
            tool(
                "erinnerung_anlegen",
                "Legt eine Erinnerung an. Nutze dies bei Wünschen wie \"erinnere " +
                    "mich morgen um zehn an den Arzt\". Rechne die Zeitangabe in " +
                    "einen konkreten Zeitpunkt um.",
                mapOf(
                    "text" to "Woran erinnert werden soll, z.B. \"an den Arzt\"",
                    "zeitpunkt" to "Zeitpunkt als ISO 8601 in lokaler Zeit, " +
                        "z.B. 2026-07-21T10:00",
                    "taeglich" to "\"true\", wenn sich die Erinnerung täglich " +
                        "wiederholen soll, sonst \"false\"",
                ),
                listOf("text", "zeitpunkt"),
            ),
            tool(
                "erinnerungen_vorlesen",
                "Liest die anstehenden Erinnerungen vor.",
                emptyMap(), emptyList(),
            ),
            tool(
                "termin_anlegen",
                "Legt einen Kalender-Termin an. Nutze dies bei Wünschen wie " +
                    "\"trag einen Termin ein für nächsten Montag: Zahnarzt\". " +
                    "Rechne die Datumsangabe in ein konkretes Datum um.",
                mapOf(
                    "titel" to "Worum es geht, z.B. \"Zahnarzt\"",
                    "datum" to "Datum als ISO 8601, z.B. 2026-08-03",
                    "zeit" to "Uhrzeit als HH:mm, z.B. 14:30 – leer lassen, " +
                        "wenn keine Uhrzeit genannt wurde",
                ),
                listOf("titel", "datum"),
            ),
            tool(
                "dokument_vorlesen",
                "Fotografiert das Dokument, das vor dem Tablet im Rahmen liegt " +
                    "(Post, Brief, Zeitung, Magazinseite), und liest es vor. " +
                    "Nutze dies bei Wünschen wie \"lies mir die Post vor\", " +
                    "\"was steht auf dem Blatt\" oder \"lies das vor\".",
                emptyMap(), emptyList(),
            ),
            tool(
                "stopp",
                "Stoppt Vorlesen oder Wiedergabe.",
                emptyMap(), emptyList(),
            ),
            tool(
                "gespraech_beenden",
                "Nutze dies, wenn die letzte Eingabe offensichtlich NICHT an dich " +
                    "gerichtet war – z.B. ein Gespräch zwischen Personen im Raum, " +
                    "Antworten an jemand anderen (\"ja\", \"okay, machen wir\"), " +
                    "Fernseher oder Selbstgespräche. Du antwortest dann gar nicht.",
                emptyMap(), emptyList(),
            ),
        )

        private fun tool(
            name: String,
            description: String,
            params: Map<String, String>,
            required: List<String>,
        ): Tool {
            val props = Tool.InputSchema.Properties.builder()
            params.forEach { (key, desc) ->
                props.putAdditionalProperty(
                    key,
                    JsonValue.from(mapOf("type" to "string", "description" to desc)),
                )
            }
            val schema = Tool.InputSchema.builder().properties(props.build())
            if (required.isNotEmpty()) schema.required(required)
            return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(schema.build())
                .build()
        }
    }
}
