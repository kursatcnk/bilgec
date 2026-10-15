package com.bilgec.app

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.tracking.TrackingPreferences
import com.bilgec.app.ui.theme.BilGecTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var refreshCounter by mutableIntStateOf(0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshCounter++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            refreshCounter
            BilGecTheme {
                SetupScreen(
                    hasUsageAccess = hasUsageAccess(),
                    hasOverlayAccess = Settings.canDrawOverlays(this),
                    hasNotificationAccess = hasNotificationAccess(),
                    onUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onOverlayAccess = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                        startActivity(intent)
                    },
                    onNotificationAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStart = { startTracker() },
                    onStop = { stopTracker() },
                )
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshCounter++
    }

    private fun startTracker() {
        val intent = Intent(this, TrackerService::class.java)
            .setAction(TrackerService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        refreshCounter++
    }

    private fun stopTracker() {
        startService(
            Intent(this, TrackerService::class.java)
                .setAction(TrackerService.ACTION_STOP),
        )
        refreshCounter++
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_START_TRACKING) {
            startTracker()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotificationAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START_TRACKING = "com.bilgec.app.action.START_TRACKING"
    }
}

@Composable
private fun SetupScreen(
    hasUsageAccess: Boolean,
    hasOverlayAccess: Boolean,
    hasNotificationAccess: Boolean,
    onUsageAccess: () -> Unit,
    onOverlayAccess: () -> Unit,
    onNotificationAccess: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var enabled by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = TrackingPreferences.elapsedMs(context)
            enabled = TrackingPreferences.isEnabled(context)
            delay(1_000)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("BilGeç", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Aşama 0: Instagram'da 1 dakika aktif kullanım sonrasında soru gösterilir.",
                style = MaterialTheme.typography.bodyLarge,
            )

            PermissionCard("Kullanım erişimi", hasUsageAccess, onUsageAccess)
            PermissionCard("Diğer uygulamaların üzerinde gösterme", hasOverlayAccess, onOverlayAccess)
            PermissionCard("Bildirim", hasNotificationAccess, onNotificationAccess)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (enabled) "Takip açık" else "Takip kapalı")
                    val remaining = (
                        TrackingPreferences.phaseZeroIntervalMs - elapsedMs
                        ).coerceAtLeast(0L) / 1_000
                    Text("Soruya kalan: ${remaining} sn")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = hasUsageAccess && hasOverlayAccess,
                ) {
                    Text("Takibi başlat")
                }
                OutlinedButton(onClick = onStop) {
                    Text("Durdur")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (granted) "İzin verildi" else "İzin gerekli")
            if (!granted) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onClick) {
                    Text("Ayarlara git")
                }
            }
        }
    }
}

