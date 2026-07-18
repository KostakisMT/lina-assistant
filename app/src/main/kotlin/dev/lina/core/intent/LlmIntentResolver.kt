package dev.lina.core.intent

class LlmIntentResolver : IntentResolver {

    override fun resolve(input: String): ResolvedIntent? {
        // Phase 2: lokales LLM (Phi-3 mini / Gemma 2B GGUF)
        return null
    }
}
