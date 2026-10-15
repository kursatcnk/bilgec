package com.bilgec.app.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.bilgec.app.settings.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences(context).trackingEnabled) return

        val serviceIntent = Intent(context, TrackerService::class.java)
            .setAction(TrackerService.ACTION_START)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

