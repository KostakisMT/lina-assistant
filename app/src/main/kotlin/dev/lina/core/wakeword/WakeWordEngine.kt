package dev.lina.core.wakeword

interface WakeWordEngine {
    fun start(onDetected: () -> Unit)
    fun stop()
    fun destroy()
}
