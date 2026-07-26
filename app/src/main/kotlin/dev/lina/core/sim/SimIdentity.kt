package dev.lina.core.sim

/**
 * Best-Effort-Fingerabdruck einer SIM-Karte statt einer echten eindeutigen ID:
 * Android 10+/API 33 schwärzt die echte ICCID für Apps ohne Trägerrechte meist
 * zu null/Platzhalter. Der Fingerabdruck kombiniert, was tatsächlich lesbar
 * ist – bewusst kein garantiert eindeutiger Schlüssel, nur ein "hat sich seit
 * letztem Mal etwas geändert"-Signal. Lieber gelegentlich unnötig nachfragen
 * als eine echte SIM-Änderung zu verpassen.
 */
object SimIdentity {

    fun composite(
        subscriptionId: Int,
        carrierName: String?,
        countryIso: String?,
        iccIdSuffix: String?,
    ): String = listOf(
        subscriptionId.toString(),
        carrierName ?: "",
        countryIso ?: "",
        iccIdSuffix ?: "",
    ).joinToString("|")

    /** `previous == null` ist "noch keine Baseline", nicht "geändert". */
    fun hasChanged(previous: String?, current: String): Boolean =
        previous != null && previous != current
}
