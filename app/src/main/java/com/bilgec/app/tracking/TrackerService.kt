package com.bilgec.app.tracking

import android.app.AppOpsManager
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
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.bilgec.app.MainActivity
import com.bilgec.app.QuestionActivity
import com.bilgec.app.data.BilGecDatabase
import com.bilgec.app.data.QuestionRepository
import com.bilgec.app.settings.AppPreferences
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
    private lateinit var preferences: AppPreferences
    private lateinit var repository: QuestionRepository

    private var trackingJob: Job? = null
    private var foregroundPackage: String? = null
    private var lastUsageQueryAt = 0L
    private var openingQuestion = false
    private var questionActivityLaunched = false
    private var lastNotificationText = ""

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        repository = QuestionRepository(BilGecDatabase.get(this).questionDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggerDebugQuestion = intent?.action == ACTION_DEBUG_QUESTION
        when (intent?.action) {
            ACTION_STOP -> {
                preferences.trackingEnabled = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_CONTINUE -> {
                preferences.resetAfterQuestion()
                questionActivityLaunched = false
            }
            ACTION_START, ACTION_DEBUG_QUESTION, null -> preferences.trackingEnabled = true
        }

        startInForeground()
        startTrackingLoop()
        if (triggerDebugQuestion) prepareQuestion()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val notification = buildNotification(normalNotificationText)
        lastNotificationText = normalNotificationText
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
        if (!preferences.trackingEnabled || preferences.isPaused) return
        if (preferences.awaitingQuestion) {
            launchSavedQuestionIfNeeded()
            return
        }
        if (!hasUsageAccess()) {
            updateNotification("Kullanım erişimi kapalı. BilGeç'i açarak izni yenileyin.")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            updateNotification("Üstte gösterme izni kapalı. BilGeç'i açarak izni yenileyin.")
            return
        }
        if (preferences.selectedPackages.isEmpty()) {
            updateNotification("Takip edilecek en az bir uygulama seçin.")
            return
        }
        updateNotification(normalNotificationText)
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        if (foregroundPackage !in preferences.selectedPackages) return

        preferences.elapsedMs += delta
        if (preferences.elapsedMs >= preferences.intervalMs) prepareQuestion()
    }

    private fun prepareQuestion() {
        if (openingQuestion || preferences.awaitingQuestion) return
        openingQuestion = true
        preferences.awaitingQuestion = true

        scope.launch(Dispatchers.IO) {
            runCatching {
                val question = repository.next(preferences.lastCategory)
                preferences.currentQuestionId = question.id
                openQuestionActivity()
            }.onFailure {
                preferences.awaitingQuestion = false
            }
            openingQuestion = false
        }
    }

    private fun launchSavedQuestionIfNeeded() {
        if (openingQuestion || questionActivityLaunched || preferences.currentQuestionId == AppPreferences.noQuestion) return
        openingQuestion = true
        scope.launch {
            openQuestionActivity()
            openingQuestion = false
        }
    }

    private fun openQuestionActivity() {
        val intent = Intent(this, QuestionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { startActivity(intent) }
            .onSuccess { questionActivityLaunched = true }
            .onFailure { preferences.awaitingQuestion = false }
    }

    private fun updateForegroundPackage() {
        if (!hasUsageAccess()) {
            foregroundPackage = null
            return
        }

        val now = System.currentTimeMillis()
        val events = getSystemService(UsageStatsManager::class.java)
            .queryEvents(lastUsageQueryAt, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foregroundPackage = event.packageName

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> if (foregroundPackage == event.packageName) foregroundPackage = null
            }
        }
        lastUsageQueryAt = now
    }

    private fun hasUsageAccess(): Boolean = getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BilGeç çalışıyor")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java).notify(
            notificationId,
            buildNotification(text),
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannelId,
            "Kullanım takibi",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "BilGeç aktif kullanım sayacını çalıştırırken gösterilir."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.bilgec.app.action.START"
        const val ACTION_STOP = "com.bilgec.app.action.STOP"
        const val ACTION_CONTINUE = "com.bilgec.app.action.CONTINUE"
        const val ACTION_DEBUG_QUESTION = "com.bilgec.app.debug.TRIGGER_QUESTION"

        private const val notificationChannelId = "usage_tracking"
        private const val notificationId = 1001
        private const val pollIntervalMs = 2_000L
        private const val initialLookbackMs = 10_000L
        private const val normalNotificationText =
            "Seçtiğiniz uygulamalardaki aktif süre cihazda ölçülüyor."
    }
}
