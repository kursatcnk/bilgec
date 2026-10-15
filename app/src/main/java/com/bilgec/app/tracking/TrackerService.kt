package com.bilgec.app.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.bilgec.app.MainActivity
import com.bilgec.app.QuestionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trackedPackages = setOf("com.instagram.android")

    private var trackingJob: Job? = null
    private var foregroundPackage: String? = null
    private var lastUsageQueryAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                TrackingPreferences.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_CONTINUE -> TrackingPreferences.resetAfterQuestion(this)
            ACTION_START, null -> TrackingPreferences.setEnabled(this, true)
        }

        startInForeground()
        startTrackingLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun startTrackingLoop() {
        if (trackingJob?.isActive == true) return

        lastUsageQueryAt = System.currentTimeMillis() - initialLookbackMs
        updateForegroundPackage()

        trackingJob = scope.launch {
            var lastTick = SystemClock.elapsedRealtime()

            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastTick).coerceAtMost(pollIntervalMs * 2)
                lastTick = now

                updateForegroundPackage()
                countActiveUsage(delta)
                delay(pollIntervalMs)
            }
        }
    }

    private fun countActiveUsage(delta: Long) {
        if (!TrackingPreferences.isEnabled(this)) return
        if (TrackingPreferences.isAwaitingQuestion(this)) return

        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isInteractive) return
        if (foregroundPackage !in trackedPackages) return

        val elapsed = TrackingPreferences.elapsedMs(this) + delta
        TrackingPreferences.setElapsedMs(this, elapsed)

        if (elapsed >= TrackingPreferences.phaseZeroIntervalMs) {
            showQuestion()
        }
    }

    private fun updateForegroundPackage() {
        val usageStats = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(lastUsageQueryAt, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                -> foregroundPackage = event.packageName

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                -> if (foregroundPackage == event.packageName) foregroundPackage = null
            }
        }

        lastUsageQueryAt = now
    }

    private fun showQuestion() {
        if (!Settings.canDrawOverlays(this)) return

        TrackingPreferences.setAwaitingQuestion(this, true)
        val intent = Intent(this, QuestionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

        runCatching { startActivity(intent) }
            .onFailure { TrackingPreferences.setAwaitingQuestion(this, false) }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BilGeç çalışıyor")
            .setContentText("Instagram kullanım süresi cihazda ölçülüyor.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannelId,
            "Kullanım takibi",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.bilgec.app.action.START"
        const val ACTION_STOP = "com.bilgec.app.action.STOP"
        const val ACTION_CONTINUE = "com.bilgec.app.action.CONTINUE"

        private const val notificationChannelId = "usage_tracking"
        private const val notificationId = 1001
        private const val pollIntervalMs = 2_000L
        private const val initialLookbackMs = 12 * 60 * 60 * 1_000L
    }
}

