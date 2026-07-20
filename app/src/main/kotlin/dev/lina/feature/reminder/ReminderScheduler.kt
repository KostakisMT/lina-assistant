package dev.lina.feature.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Plant Erinnerungen über den AlarmManager – funktioniert offline und weckt
 * das Gerät auch aus dem Doze-Modus.
 */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, reminder)

        try {
            if (canScheduleExact(am)) {
                // setAlarmClock ist die zuverlässigste Variante: läuft auch im
                // Doze-Modus und wird von Batterie-Optimierungen nicht verschoben
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(reminder.triggerAtMillis, pi),
                    pi,
                )
            } else {
                // Ohne Exact-Recht: ungefähr ist besser als gar nicht
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pi,
                )
                Log.w(TAG, "Kein Exact-Alarm-Recht – Erinnerung ggf. verzögert")
            }
            Log.d(TAG, "Erinnerung ${reminder.id} geplant: ${reminder.spokenTime()}")
        } catch (e: Exception) {
            Log.e(TAG, "Erinnerung konnte nicht geplant werden", e)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, reminder))
    }

    /** Nach Neustart alle gespeicherten Erinnerungen neu setzen. */
    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        store.purgeExpired()
        store.upcoming().forEach { reminder ->
            val faellig = if (reminder.daily && reminder.triggerAtMillis <= System.currentTimeMillis()) {
                reminder.copy(triggerAtMillis = naechsterTermin(reminder.triggerAtMillis))
            } else {
                reminder
            }
            if (faellig.triggerAtMillis != reminder.triggerAtMillis) store.add(faellig)
            schedule(context, faellig)
        }
        Log.d(TAG, "${store.upcoming().size} Erinnerungen nach Neustart gesetzt")
    }

    /** Nächster Termin für tägliche Erinnerungen (gleiche Uhrzeit, morgen). */
    fun naechsterTermin(letzter: Long): Long {
        var t = letzter
        val jetzt = System.currentTimeMillis()
        while (t <= jetzt) t += 86_400_000L
        return t
    }

    fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            // Daten eindeutig machen, damit PendingIntents sich nicht überschreiben
            data = android.net.Uri.parse("lina://reminder/${reminder.id}")
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "ReminderScheduler"
}
