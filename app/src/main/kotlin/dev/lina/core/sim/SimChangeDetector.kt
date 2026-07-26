package dev.lina.core.sim

import dev.lina.feature.contactimport.ContactImportStore

sealed class SimChangeResult {
    data object NoSim : SimChangeResult()
    data class FirstSeen(val identity: String) : SimChangeResult()
    data object Unchanged : SimChangeResult()
    data class Changed(val identity: String) : SimChangeResult()
}

/**
 * Vergleicht den aktuellen SIM-Fingerabdruck gegen den zuletzt gespeicherten.
 * Speichert selbst NICHT automatisch – das entscheidet der Aufrufer (z.B. erst
 * nach einer erfolgreichen Sprach-Nachfrage), s. LauncherActivity.
 */
class SimChangeDetector(
    private val reader: SimIdentityReader,
    private val store: ContactImportStore,
) {

    fun evaluate(): SimChangeResult {
        val current = reader.currentIdentity() ?: return SimChangeResult.NoSim
        val previous = store.lastSeenIdentity()
        return when {
            previous == null -> SimChangeResult.FirstSeen(current)
            SimIdentity.hasChanged(previous, current) -> SimChangeResult.Changed(current)
            else -> SimChangeResult.Unchanged
        }
    }
}
