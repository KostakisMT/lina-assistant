package dev.lina.feature.onboarding

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

object BatteryWhitelistGuide {

    fun isWhitelisted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestWhitelist(context: Context) {
        if (isWhitelisted(context)) return
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
