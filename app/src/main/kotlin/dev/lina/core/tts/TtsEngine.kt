package dev.lina.core.tts

enum class TtsPriority { LOW, NORMAL, HIGH, INTERRUPT }

interface TtsEngine {
    fun speak(text: String, priority: TtsPriority = TtsPriority.NORMAL)
    fun stop()
    fun setRate(rate: Float)
    fun shutdown()
}
