package dev.lina.ui.launcher

/**
 * Was Lina gerade tut – treibt die animierte Statuskugel für Besucher/
 * Angehörige (LinaOrb). Rein additiv neben [statusText]: jede bestehende
 * `statusText = "..."`-Zuweisung in LauncherActivity bekommt eine passende
 * `linaActivity = LinaActivity.X`-Zeile, ohne bestehendes Verhalten zu ändern.
 */
sealed class LinaActivity {
    /** Start/Modelle laden, gesprochene Ersteinrichtung läuft. */
    object Loading : LinaActivity()

    /** Wartet auf das Weckwort. */
    object Idle : LinaActivity()

    /** Spracherkennung aktiv – jede Variante des Zuhörfensters. */
    object Listening : LinaActivity()

    /** Claude-Anfrage läuft, Foto-/Vision-Auswertung läuft. */
    object Thinking : LinaActivity()

    /**
     * TTS gibt gerade Ton aus. Wird nicht über [statusText]-Zuweisungen
     * gesetzt, sondern separat aus `ttsEngine?.isSpeaking()` abgeleitet und
     * hat Vorrang vor jedem anderen Zustand, solange es zutrifft.
     */
    object Speaking : LinaActivity()

    /** Transienter Fehler – springt nach kurzer Zeit von selbst zu [Idle] zurück. */
    object Error : LinaActivity()
}
