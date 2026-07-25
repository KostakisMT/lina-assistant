package dev.lina.core.tts

enum class TtsPriority { LOW, NORMAL, HIGH, INTERRUPT }

interface TtsEngine {
    /** [onDone] feuert, sobald diese Äußerung fertig ist (gesprochen, fehlgeschlagen oder unterbrochen). */
    fun speak(text: String, priority: TtsPriority = TtsPriority.NORMAL, onDone: (() -> Unit)? = null)
    fun stop()
    fun setRate(rate: Float)
    fun shutdown()
}
