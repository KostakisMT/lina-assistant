package dev.lina.core.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import dev.lina.ui.launcher.LauncherActivity

class WakeWordService : Service() {

    private var engine: WakeWordEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundOk = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundOk = true
        } catch (e: Exception) {
            // Mikrofon-FGS darf aus dem Hintergrund nicht starten (Android 14+) –
            // nicht crashen, LauncherActivity startet uns bei onResume neu
            android.util.Log.w("WakeWordService", "startForeground abgelehnt, Service beendet sich", e)
            stopSelf()
            return
        }
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundOk) return START_NOT_STICKY
        startEngine()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine?.destroy()
        engine = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startEngine() {
        if (engine != null) return
        engine = OpenWakeWordEngine(applicationContext).also {
            it.start {
                // setPackage nötig: RECEIVER_NOT_EXPORTED bekommt sonst nichts (Android 14+)
                sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED).setPackage(packageName))
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lina:wakeword").apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lina Sprachassistenz",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Lina hört auf dein Weckwort"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Lina hört zu")
            .setContentText("Sag \"Hey Lina\" um zu starten")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "lina_wakeword"
        const val ACTION_WAKE_WORD_DETECTED = "dev.lina.WAKE_WORD_DETECTED"

        fun start(context: Context) {
            // FGS-Typ "microphone" wirft SecurityException ohne RECORD_AUDIO (Android 14+)
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return
            context.startForegroundService(
                Intent(context, WakeWordService::class.java)
            )
        }
    }
}
