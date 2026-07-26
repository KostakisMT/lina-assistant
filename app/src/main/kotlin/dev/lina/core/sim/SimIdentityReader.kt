package dev.lina.core.sim

import android.content.Context
import android.telephony.SubscriptionManager
import android.util.Log

/**
 * Liest die verfügbaren SIM-Felder für [SimIdentity.composite]. Jedes Feld
 * einzeln in try/catch – ein fehlendes/verweigertes Feld (Android 10+ schwärzt
 * z.B. die ICCID für Apps ohne Trägerrechte oft zu null oder wirft
 * SecurityException) darf den restlichen Fingerabdruck nicht leeren.
 */
class SimIdentityReader(private val context: Context) {

    /** `null` nur, wenn wirklich keine SIM/Subscription vorhanden ist. */
    fun currentIdentity(): String? {
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return null
        val activeSubscriptions = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            Log.w(TAG, "Kein Zugriff auf aktive Subscriptions", e)
            null
        }
        val subscription = activeSubscriptions?.firstOrNull() ?: return null

        val subscriptionId = subscription.subscriptionId
        val carrierName = safeRead { subscription.carrierName?.toString() }
        val countryIso = safeRead { subscription.countryIso }
        val iccIdSuffix = safeRead { subscription.iccId }?.takeLast(ICC_ID_SUFFIX_LENGTH)

        return SimIdentity.composite(subscriptionId, carrierName, countryIso, iccIdSuffix)
    }

    private fun safeRead(read: () -> String?): String? = try {
        read()
    } catch (e: SecurityException) {
        Log.w(TAG, "SIM-Feld nicht lesbar", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "SIM-Feld nicht lesbar", e)
        null
    }

    private companion object {
        const val TAG = "SimIdentityReader"
        const val ICC_ID_SUFFIX_LENGTH = 6
    }
}
