package dev.lina.feature.audiobook

/**
 * Ein Kapitel eines Hörbuchs.
 *
 * Zwei Bauformen kommen vor:
 *  - **eine Datei je Kapitel** (LibriVox, lose MP3-Ordner): [clipStartMs] = 0,
 *    [clipEndMs] = 0 – die ganze Datei ist das Kapitel.
 *  - **eine Datei, viele Kapitel** (DAISY 2.02): mehrere Kapitel zeigen mit
 *    unterschiedlichen Zeitbereichen auf dieselbe Audiodatei. Dann grenzen
 *    [clipStartMs]/[clipEndMs] den Ausschnitt ab.
 */
data class Chapter(
    val title: String,
    val uri: String,
    val clipStartMs: Long = 0L,
    /** 0 = bis Dateiende. */
    val clipEndMs: Long = 0L,
    val durationSecs: Int = 0,
) {
    val isClipped: Boolean get() = clipStartMs > 0L || clipEndMs > 0L

    /** Länge des Kapitels, sofern aus den Clip-Grenzen ableitbar. */
    val clipDurationMs: Long
        get() = if (clipEndMs > clipStartMs) clipEndMs - clipStartMs else 0L
}
