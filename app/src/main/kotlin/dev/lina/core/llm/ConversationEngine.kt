package dev.lina.core.llm

import dev.lina.core.intent.ResolvedIntent

/** Ergebnis einer Anfrage an die Ebene-2-Konversations-Engine. */
sealed class LinaReply {
    /** Freie Konversationsantwort – direkt vorlesen. */
    data class Say(val text: String) : LinaReply()

    /** Ein Gerätebefehl wurde erkannt – lokal ausführen. */
    data class Do(val intent: ResolvedIntent) : LinaReply()

    /** Fehler (offline, Dienst nicht erreichbar …) – Meldung vorlesen. */
    data class Error(val text: String) : LinaReply()

    /** Eingabe war nicht an Lina gerichtet (Raumgespräch) – still beenden. */
    object End : LinaReply()
}

/**
 * Ebene 2 des Intent-Systems (siehe CLAUDE.md): freie Konversation mit
 * Lina-Persona und Dialoggedächtnis. Gerätebefehle, die die lokale Ebene 1
 * (`LocalCommandResolver`) nicht verstanden hat, erkennt die Engine über ihr
 * eigenes Tool-/Function-Calling-Format und reicht sie als [ResolvedIntent]
 * zur lokalen Ausführung zurück.
 *
 * Aktive Implementierung: [ClaudeConversation] (Claude API, ADR-017). Ein
 * lokaler Gemma-3n-Pfad (ADR-032) implementiert dieselbe Schnittstelle als
 * zweiter Build-Flavor, nicht als Laufzeit-Fallback.
 *
 * Blockierend – immer von einem Hintergrund-Thread aufrufen.
 */
interface ConversationEngine {
    /**
     * [freshWakeWord] = true: die App weiß mit Sicherheit, dass der Nutzer
     * gerade "Hey Lina" gesagt hat (nicht nur die Engine vermutet es).
     */
    fun ask(input: String, freshWakeWord: Boolean = false): LinaReply

    /**
     * Liest ein fotografiertes Dokument vor (Vision, einmalig und
     * zustandslos – rührt die Gesprächs-History nicht an).
     *
     * [verbatim] = false: nur das Relevante. [verbatim] = true: der
     * vollständige Text.
     */
    fun readDocument(jpegBytes: ByteArray, verbatim: Boolean = false): DocumentReadResult

    /** Setzt das Dialoggedächtnis zurück. */
    fun reset()
}
