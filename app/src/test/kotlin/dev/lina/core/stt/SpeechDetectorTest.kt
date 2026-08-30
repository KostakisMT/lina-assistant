package dev.lina.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressionstests zum Befund vom 2026-08-30: ein einzelnes Störgeräusch
 * genügte, um den Sprachbeginn einrasten zu lassen; danach war die Notbremse
 * für "hat gar nicht gesprochen" unerreichbar und Whisper bekam Raumrauschen
 * zu hören, aus dem es Untertitel-Artefakte halluzinierte. Beide Mikrofon-
 * öffnungen des damaligen Testlaufs lieferten Geistereingaben.
 *
 * Die Frames sind hier 100 ms lang, wie in [WhisperSttEngine].
 */
class SpeechDetectorTest {

    private val frameMs = 100
    private val loud = SpeechDetector.SPEECH_AMP_THRESHOLD + 500
    private val quiet = SpeechDetector.SPEECH_AMP_THRESHOLD - 500

    private fun detector() = SpeechDetector(endSilenceMs = 1200)

    /** Der eigentliche Fehlerfall: ein Klacken, danach Stille. */
    @Test
    fun `einzelnes Stoergeraeusch loest keinen Sprachbeginn aus`() {
        val d = detector()
        assertEquals(SpeechDetector.Decision.CONTINUE, d.offer(loud, frameMs))
        assertFalse("ein Frame darf nicht einrasten", d.speechStarted)

        // Danach nur noch Stille – die Notbremse muss greifen.
        var decision = SpeechDetector.Decision.CONTINUE
        repeat(60) { if (decision == SpeechDetector.Decision.CONTINUE) decision = d.offer(quiet, frameMs) }
        assertEquals(SpeechDetector.Decision.ABORT_NO_SPEECH, decision)
    }

    /** Die Notbremse darf nicht hinter speechStarted verschwinden. */
    @Test
    fun `ohne Sprache bricht die Aufnahme nach dem Timeout ab`() {
        val d = detector()
        var decision = SpeechDetector.Decision.CONTINUE
        var elapsed = 0
        while (decision == SpeechDetector.Decision.CONTINUE && elapsed < 10_000) {
            decision = d.offer(quiet, frameMs)
            elapsed += frameMs
        }
        assertEquals(SpeechDetector.Decision.ABORT_NO_SPEECH, decision)
        assertEquals(SpeechDetector.NO_SPEECH_TIMEOUT_MS, elapsed)
    }

    /** Verstreute Knackser rasten nicht ein, weil sie nicht zusammenhängen. */
    @Test
    fun `verstreute Knackser rasten nicht ein`() {
        val d = detector()
        repeat(20) {
            d.offer(loud, frameMs)
            d.offer(quiet, frameMs)
        }
        assertFalse(d.speechStarted)
    }

    @Test
    fun `echte Sprache rastet ein und endet nach der Stillephase`() {
        val d = detector()
        repeat(8) { d.offer(loud, frameMs) } // 800ms Sprache
        assertTrue(d.speechStarted)

        // 1200ms Stille beenden die Aufnahme – vorher nicht.
        repeat(11) { assertEquals(SpeechDetector.Decision.CONTINUE, d.offer(quiet, frameMs)) }
        assertEquals(SpeechDetector.Decision.STOP_SPEECH_ENDED, d.offer(quiet, frameMs))
        assertTrue(d.hasEnoughSpeech())
    }

    /** Kurze Bestätigungen müssen durchkommen – "Ja"/"Nein" tragen den SIM-Import. */
    @Test
    fun `kurze Bestaetigung zaehlt als genug Sprache`() {
        val d = detector()
        repeat(4) { d.offer(loud, frameMs) } // 400ms
        assertTrue(d.speechStarted)
        assertTrue("400ms muessen reichen", d.hasEnoughSpeech())
    }

    /**
     * Wer eingerastet ist, gilt als gesprochen. Vorher verlangte
     * hasEnoughSpeech() 400ms und verwarf damit kurze, leise Äußerungen NACH
     * dem Einrasten – am Gerät am 2026-08-30 als "Zu wenig Sprachenergie
     * (300ms)" beobachtet, während der Nutzer vor dem Tablet stand und
     * antwortete. Kurze Bestätigungen müssen durchkommen.
     */
    @Test
    fun `Einrasten genuegt als Sprachenergie`() {
        val d = detector()
        repeat(3) { d.offer(loud, frameMs) } // exakt die Einrast-Schwelle, 300ms
        assertTrue(d.speechStarted)
        assertTrue("wer einrastet, hat gesprochen", d.hasEnoughSpeech())
    }

    /** Ohne Einrasten bleibt der Clip aussen vor, egal wie lang er ist. */
    @Test
    fun `ohne Einrasten keine Sprachenergie`() {
        val d = detector()
        repeat(2) { d.offer(loud, frameMs) } // 200ms, unter der Schwelle
        assertFalse(d.speechStarted)
        assertFalse(d.hasEnoughSpeech())
    }

    @Test
    fun `Sprechpause mitten im Satz beendet die Aufnahme nicht`() {
        val d = detector()
        repeat(5) { d.offer(loud, frameMs) }
        repeat(8) { assertEquals(SpeechDetector.Decision.CONTINUE, d.offer(quiet, frameMs)) } // 800ms Pause
        repeat(5) { assertEquals(SpeechDetector.Decision.CONTINUE, d.offer(loud, frameMs)) }
        assertTrue(d.hasEnoughSpeech())
    }
}
