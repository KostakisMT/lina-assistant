package dev.lina.core.intent

interface IntentResolver {
    fun resolve(input: String): ResolvedIntent?
}
