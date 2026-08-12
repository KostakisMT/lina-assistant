package dev.lina.core.llm

/**
 * Baut die Ebene-2-Engine für den **ngo**-Flavor (ADR-032/034). Phase D ist
 * reine Gradle-Scaffolding, kein lokaler Konversations-Pfad – es gibt hier
 * absichtlich noch kein `GemmaConversation`. Ebene 2 (freie Konversation,
 * Dokument-Vision) bleibt deshalb im NGO-Flavor immer aus; das ist exakt der
 * bereits gehärtete `claude == null`-Pfad in `LauncherActivity`
 * (z.B. "Zum Vorlesen von Dokumenten brauche ich eine Internetverbindung
 * und den Zugang zu meinem Sprachdienst."), kein neuer Sonderfall.
 *
 * Genau dieselbe Funktion existiert nochmal, mit anderem Rumpf, in
 * `src/standard/.../ConversationEngineProvider.kt` – welche Version
 * kompiliert wird, entscheidet allein der aktive Build-Flavor.
 */
object ConversationEngineProvider {
    fun create(
        apiKey: String,
        contactNames: List<String>,
        interests: String,
        region: String,
    ): ConversationEngine? = null
}
