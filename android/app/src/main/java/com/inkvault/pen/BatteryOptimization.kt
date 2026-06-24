package com.inkvault.pen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers for the battery-optimization exemption the user must grant so OneUI (an Android 14+ phone /
 * an Android tablet) doesn't sleep the pen foreground service. We deliberately open the system settings
 * list (no special permission, Play-safe) rather than firing ACTION_REQUEST_IGNORE_BATTERY_
 * OPTIMIZATIONS. Document the manual step in the app/onboarding.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the OS battery-optimization list so the user can mark InkVault as "Don't optimize". */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
