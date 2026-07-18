package dev.lina.core.stt

interface SttEngine {
    fun startListening(onResult: (String) -> Unit)
    fun stopListening()
    fun destroy()
}
