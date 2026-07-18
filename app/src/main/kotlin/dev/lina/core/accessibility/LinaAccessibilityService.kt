package dev.lina.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent

class LinaAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val evt = event ?: return

        when (evt.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(evt)
            }
            else -> {}
        }
    }

    override fun onInterrupt() {}

    private fun handleNotification(event: AccessibilityEvent) {
        val parcelable = event.parcelableData
        if (parcelable !is Notification) return

        val packageName = event.packageName?.toString() ?: return
        val text = event.text?.joinToString(" ") ?: return

        when {
            packageName == "com.android.dialer" ||
            packageName == "com.google.android.dialer" ||
            packageName == "com.samsung.android.incallui" ||
            packageName == "com.android.server.telecom" -> {
                broadcastEvent(EVENT_INCOMING_CALL, text)
            }
            packageName == "com.google.android.apps.messaging" ||
            packageName == "com.samsung.android.messaging" ||
            packageName == "com.android.mms" -> {
                broadcastEvent(EVENT_INCOMING_SMS, text)
            }
        }
    }

    private fun broadcastEvent(action: String, text: String) {
        sendBroadcast(Intent(action).apply {
            putExtra(EXTRA_TEXT, text)
            setPackage(packageName)
        })
    }

    companion object {
        const val EVENT_INCOMING_CALL = "dev.lina.INCOMING_CALL"
        const val EVENT_INCOMING_SMS = "dev.lina.INCOMING_SMS"
        const val EXTRA_TEXT = "text"
    }
}
