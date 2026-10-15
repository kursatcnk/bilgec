package com.bilgec.app

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bilgec.app.data.BilGecDatabase
import com.bilgec.app.data.QuestionRepository
import com.bilgec.app.settings.AppPreferences
import com.bilgec.app.settings.DarkMode
import com.bilgec.app.settings.DailyStats
import com.bilgec.app.settings.SocialApp
import com.bilgec.app.settings.supportedSocialApps
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.ui.theme.BilGecTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private var refreshCounter by mutableIntStateOf(0)
    private var questionCount by mutableIntStateOf(0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshCounter++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        loadQuestionCount()

        setContent {
            refreshCounter
            BilGecTheme(preferences.darkMode) {
                BilGecApp(
                    preferences = preferences,
                    permissions = permissionState(),
                    questionCount = questionCount,
                    onOpenUsageAccess = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onOpenOverlayAccess = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:$packageName".toUri(),
                            ),
                        )
                    },
                    onNotificationAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onStart = ::startTracker,
                    onStop = ::stopTracker,
                    onChanged = { refreshCounter++ },
                    onReset = ::resetData,
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
        ContextCompat.startForegroundService(
            this,
            Intent(this, TrackerService::class.java).setAction(TrackerService.ACTION_START),
        )
        preferences.trackingEnabled = true
        refreshCounter++
    }

    private fun stopTracker() {
        startService(Intent(this, TrackerService::class.java).setAction(TrackerService.ACTION_STOP))
        preferences.trackingEnabled = false
        refreshCounter++
    }

    private fun resetData() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                QuestionRepository(BilGecDatabase.get(this@MainActivity).questionDao()).clearProgress()
                preferences.resetAll()
            }
            stopService(Intent(this@MainActivity, TrackerService::class.java))
            refreshCounter++
        }
    }

    private fun loadQuestionCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            questionCount = runCatching {
                QuestionRepository(BilGecDatabase.get(this@MainActivity).questionDao()).questionCount()
            }.getOrDefault(0)
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_START_TRACKING) startTracker()
    }

    private fun permissionState(): PermissionState {
        val usage = getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
        val notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return PermissionState(usage, Settings.canDrawOverlays(this), notification)
    }

    companion object {
        const val ACTION_START_TRACKING = "com.bilgec.app.action.START_TRACKING"
    }
}

private data class PermissionState(
    val usage: Boolean,
    val overlay: Boolean,
    val notification: Boolean,
) {
    val requiredGranted: Boolean get() = usage && overlay
    val allGranted: Boolean get() = requiredGranted && notification
}

private enum class AppPage(val label: String, val icon: ImageVector) {
    HOME("Ana sayfa", Icons.Default.Home),
    APPS("Uygulamalar", Icons.Default.Apps),
    SETTINGS("Ayarlar", Icons.Default.Settings),
}

@Composable
private fun BilGecApp(
    preferences: AppPreferences,
    permissions: PermissionState,
    questionCount: Int,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onNotificationAccess: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onChanged: () -> Unit,
    onReset: () -> Unit,
) {
    var page by remember { mutableStateOf(AppPage.HOME) }
    var elapsedMs by remember { mutableLongStateOf(preferences.elapsedMs) }
    var stats by remember { mutableStateOf(preferences.todayStats()) }

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = preferences.elapsedMs
            stats = preferences.todayStats()
            delay(1_000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppPage.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { androidx.compose.material3.Icon(item.icon, item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (page) {
            AppPage.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                preferences = preferences,
                permissions = permissions,
                elapsedMs = elapsedMs,
                stats = stats,
                onStart = onStart,
                onStop = onStop,
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenOverlayAccess = onOpenOverlayAccess,
                onNotificationAccess = onNotificationAccess,
            )
            AppPage.APPS -> AppsScreen(
                modifier = Modifier.padding(padding),
                preferences = preferences,
                onChanged = onChanged,
            )
            AppPage.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                preferences = preferences,
                questionCount = questionCount,
                onChanged = onChanged,
                onReset = onReset,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    preferences: AppPreferences,
    permissions: PermissionState,
    elapsedMs: Long,
    stats: DailyStats,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onNotificationAccess: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("BilGeç", style = MaterialTheme.typography.headlineLarge)
        Text("Sosyal medyaya kısa bir bilgi molası.")

        if (!permissions.allGranted) {
            Text("Kurulumu tamamla", style = MaterialTheme.typography.titleLarge)
            PermissionCard(
                "Kullanım erişimi",
                "Yalnızca seçtiğin uygulamalarda geçen aktif süreyi cihazda ölçer.",
                permissions.usage,
                onOpenUsageAccess,
            )
            PermissionCard(
                "Üstte gösterme",
                "Süre dolduğunda soru kartını açık uygulamanın üzerinde gösterir.",
                permissions.overlay,
                onOpenOverlayAccess,
            )
            PermissionCard(
                "Bildirim",
                "Android'in arka planda kullanım takibine izin vermesi için gereklidir.",
                permissions.notification,
                onNotificationAccess,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Koruma", style = MaterialTheme.typography.titleLarge)
                        Text(if (preferences.trackingEnabled) "Açık" else "Kapalı")
                    }
                    Switch(
                        checked = preferences.trackingEnabled,
                        onCheckedChange = { enabled -> if (enabled) onStart() else onStop() },
                        enabled = permissions.allGranted && preferences.selectedPackages.isNotEmpty(),
                    )
                }
                HorizontalDivider()
                val remaining = (preferences.intervalMs - elapsedMs).coerceAtLeast(0L)
                Text("Sıradaki soruya ${formatDuration(remaining)}")
                Text("${preferences.selectedPackages.size} uygulama seçili")
                if (preferences.isPaused) {
                    Text(
                        "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(preferences.pauseUntilMs))} saatine kadar duraklatıldı",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Bugün", stats.solved.toString(), Modifier.weight(1f))
            StatCard("Doğru", stats.correct.toString(), Modifier.weight(1f))
            StatCard("Seri", "${stats.streak} gün", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    explanation: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            Text(if (granted) "İzin verildi" else "İzin gerekli")
            if (!granted) OutlinedButton(onClick = onClick) { Text("Ayarlara git") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppsScreen(
    modifier: Modifier,
    preferences: AppPreferences,
    onChanged: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Uygulamalar", style = MaterialTheme.typography.headlineMedium)
        Text("Bu uygulamalarda geçirilen aktif süre ortak sayaçta toplanır.")
        supportedSocialApps.forEach { app ->
            SocialAppRow(
                app = app,
                selected = app.packageName in preferences.selectedPackages,
                onSelected = { selected ->
                    preferences.selectedPackages = preferences.selectedPackages.toMutableSet().apply {
                        if (selected) add(app.packageName) else remove(app.packageName)
                    }
                    onChanged()
                },
            )
        }
    }
}

@Composable
private fun SocialAppRow(app: SocialApp, selected: Boolean, onSelected: (Boolean) -> Unit) {
    val context = LocalContext.current
    val appInfo = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationInfo(app.packageName, 0) }.getOrNull()
    }
    val icon: ImageBitmap? = remember(appInfo) {
        appInfo?.let {
            context.packageManager.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap()
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Image(icon, app.displayName, Modifier.size(42.dp))
            else Spacer(Modifier.size(42.dp))
            Column(Modifier.weight(1f)) {
                Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                Text(if (appInfo == null) "Yüklü değil" else "Yüklü")
            }
            Checkbox(checked = selected, onCheckedChange = onSelected)
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    preferences: AppPreferences,
    questionCount: Int,
    onChanged: () -> Unit,
    onReset: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
        Text("Soru aralığı", style = MaterialTheme.typography.titleMedium)
        AppPreferences.intervalOptions.forEach { minutes ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = preferences.intervalMinutes == minutes,
                    onClick = {
                        preferences.intervalMinutes = minutes
                        onChanged()
                    },
                )
                Text("$minutes dakika")
            }
        }

        HorizontalDivider()
        Text("Duraklat", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { preferences.pauseForOneHour(); onChanged() }) {
                Text("1 saat sorma")
            }
            OutlinedButton(onClick = { preferences.pauseForToday(); onChanged() }) {
                Text("Bugün sorma")
            }
        }
        if (preferences.isPaused) {
            TextButton(onClick = { preferences.resumeNow(); onChanged() }) { Text("Şimdi devam et") }
        }

        HorizontalDivider()
        Text("Görünüm", style = MaterialTheme.typography.titleMedium)
        DarkMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = preferences.darkMode == mode,
                    onClick = { preferences.darkMode = mode; onChanged() },
                )
                Text(
                    when (mode) {
                        DarkMode.SYSTEM -> "Sistem ayarı"
                        DarkMode.LIGHT -> "Açık"
                        DarkMode.DARK -> "Koyu"
                    },
                )
            }
        }

        HorizontalDivider()
        Text("Hakkında", style = MaterialTheme.typography.titleMedium)
        Text("BilGeç 1.0 • $questionCount çevrimdışı soru")
        Text("Hesap, reklam veya sunucu bağlantısı kullanmaz. Tüm ilerleme bu cihazda kalır.")
        Text("Bayrak görselleri: flag-icons, MIT Lisansı. Ülke verileri: mledoze/countries, ODbL. Element verileri: Periodic-Table-JSON, CC0. Diğer yapılandırılmış bilgiler: Wikidata, CC0.")
        OutlinedButton(onClick = { showResetDialog = true }) { Text("Verileri sıfırla") }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Veriler sıfırlansın mı?") },
            text = { Text("Sayaç, istatistikler, ayarlar ve görülen soru geçmişi silinir.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    },
                ) { Text("Sıfırla") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Vazgeç") }
            },
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
