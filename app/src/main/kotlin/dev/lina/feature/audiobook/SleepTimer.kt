package dev.lina.feature.audiobook

import android.os.CountDownTimer

class SleepTimer(
    private val onFadeStep: (volume: Float) -> Unit,
    private val onFinished: () -> Unit,
) {

    private var timer: CountDownTimer? = null
    private var totalMinutes: Int = 0

    val isActive: Boolean get() = timer != null

    fun start(minutes: Int) {
        cancel()
        totalMinutes = minutes
        val totalMs = minutes * 60_000L
        val fadeStartMs = FADE_DURATION_MS

        timer = object : CountDownTimer(totalMs, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished <= fadeStartMs) {
                    val fraction = millisUntilFinished.toFloat() / fadeStartMs
                    onFadeStep(fraction)
                }
            }

            override fun onFinish() {
                onFadeStep(0f)
                onFinished()
                timer = null
            }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        onFadeStep(1f)
    }

    fun remainingDescription(): String {
        return if (isActive) {
            "Schlaf-Timer läuft: $totalMinutes Minuten."
        } else {
            "Kein Schlaf-Timer aktiv."
        }
    }

    companion object {
        private const val FADE_DURATION_MS = 30_000L
    }
}
