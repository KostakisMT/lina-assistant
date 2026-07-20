package dev.lina.feature.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Feuert, wenn eine Erinnerung fällig ist: Lina sagt sie an (Broadcast an die
 * LauncherActivity, die die TTS-Engine hält) und legt zusätzlich eine
 * Benachrichtigung mit Ton ab – als hörbarer Rückfall, falls die App gerade
 * nicht ansprechbar ist.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val store = ReminderStore(context)
        val reminder = store.find(id) ?: run {
            Log.w(TAG, "Erinnerung $id nicht mehr vorhanden")
            return
        }
        Log.d(TAG, "Erinnerung fällig: ${reminder.text}")

        notify(context, reminder)

        // Lina soll es aussprechen – die TTS-Engine lebt in der Activity
        context.sendBroadcast(
            Intent(ACTION_REMINDER_DUE)
                .setPackage(context.packageName)
                .putExtra(EXTRA_TEXT, reminder.text)
        )

        if (reminder.daily) {
            val naechste = reminder.copy(
                triggerAtMillis = ReminderScheduler.naechsterTermin(reminder.triggerAtMillis)
            )
            store.add(naechste)
            ReminderScheduler.schedule(context, naechste)
        } else {
            store.remove(id)
        }
    }

    private fun notify(context: Context, reminder: Reminder) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Lina Erinnerungen",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Erinnerungen, die Lina ansagt" }
        )
        val launcher = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val pi = launcher?.let {
            PendingIntent.getActivity(
                context, reminder.id, it, PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Erinnerung")
            .setContentText(reminder.text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .apply { pi?.let { setContentIntent(it) } }
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_BASE + reminder.id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS nicht erteilt – die gesprochene Ansage bleibt
            Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "lina_reminder"
        private const val NOTIFICATION_BASE = 100

        const val ACTION_REMIND = "dev.lina.REMINDER_FIRE"
        const val ACTION_REMINDER_DUE = "dev.lina.REMINDER_DUE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
    }
}
