package com.bilgec.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bilgec.app.tracking.TrackerService

class DebugQuestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TrackerService.ACTION_DEBUG_QUESTION) return
        context.startService(
            Intent(context, TrackerService::class.java)
                .setAction(TrackerService.ACTION_DEBUG_QUESTION),
        )
    }
}
