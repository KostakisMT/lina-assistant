package dev.lina.core.llm

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUseBlock
import dev.lina.core.intent.ResolvedIntent

/** Ergebnis einer Claude-Anfrage. */
sealed class LinaReply {
    /** Freie Konversationsantwort – direkt vorlesen. */
    data class Say(val text: String) : LinaReply()

    /** Claude hat einen Gerätebefehl erkannt – lokal ausführen. */
    data class Do(val intent: ResolvedIntent) : LinaReply()

    /** Fehler (offline, API nicht erreichbar …) – Meldung vorlesen. */
    data class Error(val text: String) : LinaReply()
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
    }

    /** Dialoggedächtnis: nur Textwechsel, damit die History API-gültig bleibt. */
    private val history = ArrayDeque<MessageParam>()

    fun ask(input: String): LinaReply {
        history.addLast(message(MessageParam.Role.USER, input))
        trimHistory()
        return try {
            val response = client.messages().create(buildParams())

            for (block in response.content()) {
                val toolUse = block.toolUse()
                if (toolUse.isPresent) {
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
            if (text.isEmpty()) {
                history.removeLast()
                return LinaReply.Error("Darauf habe ich gerade keine Antwort.")
            }
            history.addLast(message(MessageParam.Role.ASSISTANT, text))
            LinaReply.Say(text)
        } catch (e: Exception) {
            Log.e(TAG, "Claude-Anfrage fehlgeschlagen", e)
            history.removeLast()
            LinaReply.Error(
                "Ich kann gerade nicht nachdenken. " +
                    "Wahrscheinlich fehlt die Internetverbindung."
            )
        }
    }

    fun reset() = history.clear()

    private fun buildParams(): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(300L)
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
                "stopp",
                "Stoppt Vorlesen oder Wiedergabe.",
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
