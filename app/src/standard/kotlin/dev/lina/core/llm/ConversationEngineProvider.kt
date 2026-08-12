package dev.lina.core.llm

/**
 * Baut die Ebene-2-Engine für den **standard**-Flavor (ADR-032/034): Claude
 * API, ADR-017. Genau dieselbe Funktion existiert nochmal, mit anderem
 * Rumpf, in `src/ngo/.../ConversationEngineProvider.kt` – welche Version
 * kompiliert wird, entscheidet allein der aktive Build-Flavor. Das ist der
 * einzige Ort, an dem `LauncherActivity` (geteilter Code in `src/main`)
 * `ClaudeConversation` überhaupt berührt; ohne diese Indirektion würde der
 * NGO-Flavor nicht kompilieren, weil `ClaudeConversation` dort gar nicht auf
 * dem Klassenpfad liegt (kein Anthropic-SDK, siehe app/build.gradle.kts).
 */
object ConversationEngineProvider {
    fun create(
        apiKey: String,
        contactNames: List<String>,
        interests: String,
        region: String,
    ): ConversationEngine? = ClaudeConversation(apiKey, contactNames, interests, region)
}
