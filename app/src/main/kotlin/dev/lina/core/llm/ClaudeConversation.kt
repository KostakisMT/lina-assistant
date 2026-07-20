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

/** Ergebnis einer Claude-Anfrage. */
sealed class LinaReply {
    /** Freie Konversationsantwort – direkt vorlesen. */
    data class Say(val text: String) : LinaReply()

    /** Claude hat einen Gerätebefehl erkannt – lokal ausführen. */
    data class Do(val intent: ResolvedIntent) : LinaReply()

    /** Fehler (offline, API nicht erreichbar …) – Meldung vorlesen. */
    data class Error(val text: String) : LinaReply()

    /** Eingabe war nicht an Lina gerichtet (Raumgespräch) – still beenden. */
    object End : LinaReply()
}

/**
 * Ebene 2 des Intent-Systems (siehe CLAUDE.md): freie Konversation über die
 * Claude API mit Lina-Persona und Dialoggedächtnis. Gerätebefehle, die die
 * lokale Ebene 1 nicht verstanden hat (z.B. verstümmelte STT-Transkripte wie
 * "Rumfe, Boris an"), erkennt Claude per Tool-Definition und reicht sie als
 * [ResolvedIntent] zur lokalen Ausführung zurück.
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
) {

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
                "Websuche. Nachrichten fasst du in zwei bis drei Meldungen mit je " +
                "ein bis zwei Sätzen zusammen – vorlesbar, ohne Quellen-URLs. " +
                "Das Werkzeug nachrichten_vorlesen ist nur für die gespeicherten " +
                "Standard-Schlagzeilen; bei regionalen oder thematischen " +
                "Nachrichtenwünschen suche stattdessen selbst."
        )
        append(
            "\n- Nutze dein Wissen über den Nutzer unaufdringlich: Es prägt Tiefe " +
                "und Tonfall deiner Antworten, aber du erwähnst seine Interessen " +
                "oder Kontakte nicht von dir aus und sagst nie Dinge wie " +
                "\"das passt zu deinem Interesse an ...\". Keine ungefragten " +
                "Zusatzangebote am Ende der Antwort."
        )
    }

    /** Dialoggedächtnis: nur Textwechsel, damit die History API-gültig bleibt. */
    private val history = ArrayDeque<MessageParam>()

    fun ask(input: String): LinaReply {
        history.addLast(message(MessageParam.Role.USER, input))
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

    fun reset() = history.clear()

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
    fun readDocument(jpegBytes: ByteArray, verbatim: Boolean = false): LinaReply {
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
            val params = MessageCreateParams.builder()
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
                .build()

            val response = client.messages().create(params)
            val text = response.content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
            Log.d(TAG, "readDocument(verbatim=$verbatim): ${text.length} Zeichen")
            if (text.isEmpty()) {
                LinaReply.Error("Ich konnte auf dem Bild nichts erkennen.")
            } else {
                LinaReply.Say(text)
            }
        } catch (e: RateLimitException) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Rate-Limit)", e)
            LinaReply.Error("Gerade ist viel los bei mir. Versuch es gleich noch einmal.")
        } catch (e: AnthropicServiceException) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Dienst)", e)
            LinaReply.Error(
                "Mein Sprachdienst meldet ein Problem. Bitte sag deinem Betreuer Bescheid."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Dokument-Auswertung fehlgeschlagen (Verbindung)", e)
            LinaReply.Error(
                "Ich kann das Dokument gerade nicht auswerten. " +
                    "Wahrscheinlich fehlt die Internetverbindung."
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
            "nachrichten_vorlesen" -> ResolvedIntent.ReadNews
            "hoerbuch_abspielen" -> ResolvedIntent.PlayAudiobook
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
            - Wichtig: Das Mikrofon hört nach deinen Antworten automatisch weiter.
              Nicht alles, was du hörst, ist an dich gerichtet! Wirkt die Eingabe
              wie ein Gespräch im Raum, eine Antwort an eine andere Person oder
              Fernsehton, nutze das Werkzeug gespraech_beenden und antworte nicht.
            - Frag bei unklaren oder fragmentarischen Eingaben NICHT nach. Nur wenn
              eine Eingabe erkennbar eine Frage oder Bitte AN DICH ist, darfst du
              um Wiederholung bitten. Alles andere: gespraech_beenden. Eine echte
              Frage kommt wieder – Hineinreden in ein Gespräch stört dagegen sehr.
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
            tool(
                "nachrichten_vorlesen",
                "Liest die aktuellen Nachrichten-Schlagzeilen vor (RSS).",
                emptyMap(), emptyList(),
            ),
            tool("hoerbuch_abspielen", "Spielt das aktuelle Hörbuch ab.", emptyMap(), emptyList()),
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
