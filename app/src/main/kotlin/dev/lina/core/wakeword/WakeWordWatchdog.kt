package dev.lina.core.wakeword

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class WakeWordWatchdog(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (!isWakeWordServiceRunning()) {
                WakeWordService.start(context)
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(checkRunnable)
    }

    @Suppress("DEPRECATION")
    private fun isWakeWordServiceRunning(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == WakeWordService::class.java.name
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L
    }
}
