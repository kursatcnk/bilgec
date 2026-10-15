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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.bilgec.app.ui.theme.BilGecInk
import com.bilgec.app.ui.theme.BilGecRed
import com.bilgec.app.ui.theme.BilGecSuccess
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
        enableEdgeToEdge()
        preferences = AppPreferences(this)
        loadQuestionCount()

        setContent {
            refreshCounter
            BilGecTheme(preferences.darkMode) {
                BilGecApp(
                    preferences = preferences,
                    permissions = permissionState(),
                    questionCount = questionCount,
                    onOpenUsageAccess = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
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
    val allGranted: Boolean get() = usage && overlay && notification
}

private enum class AppPage(val label: String, val icon: ImageVector) {
    HOME("Bugün", Icons.Default.Home),
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { PremiumBottomBar(page = page, onSelect = { page = it }) },
    ) { padding ->
        when (page) {
            AppPage.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                preferences = preferences,
                permissions = permissions,
                elapsedMs = elapsedMs,
                stats = stats,
                questionCount = questionCount,
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
private fun PremiumBottomBar(page: AppPage, onSelect: (AppPage) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppPage.entries.forEach { item ->
                NavigationBarItem(
                    selected = page == item,
                    onClick = { onSelect(item) },
                    icon = { Icon(item.icon, item.label) },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = BilGecRed,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
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
    questionCount: Int,
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BrandHeader()
        Text(
            "Kaydırmadan önce\nbir şey öğren.",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            "Seçtiğin uygulamalar açılmadan önce kısa bir bilgi molası. $questionCount çevrimdışı soru, sıfır hesap.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ProtectionCard(
            enabled = preferences.trackingEnabled,
            canEnable = permissions.allGranted && preferences.selectedPackages.isNotEmpty(),
            elapsedMs = elapsedMs,
            intervalMs = preferences.intervalMs,
            selectedApps = preferences.selectedPackages.size,
            pausedUntilMs = preferences.pauseUntilMs.takeIf { preferences.isPaused },
            onToggle = { if (it) onStart() else onStop() },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(Icons.Default.School, stats.solved.toString(), "Bugün", Modifier.weight(1f))
            MetricCard(Icons.Default.CheckCircle, stats.correct.toString(), "Doğru", Modifier.weight(1f))
            MetricCard(Icons.Default.LocalFireDepartment, stats.streak.toString(), "Gün seri", Modifier.weight(1f))
        }

        if (!permissions.allGranted) {
            SectionTitle("KURULUM", "Üç küçük adım kaldı")
            PermissionCard(
                icon = Icons.Default.Timer,
                title = "Kullanım erişimi",
                explanation = "Seçtiğin uygulamalarda geçen aktif süreyi yalnızca cihazında ölçer.",
                granted = permissions.usage,
                onClick = onOpenUsageAccess,
            )
            PermissionCard(
                icon = Icons.Default.Smartphone,
                title = "Üstte gösterme",
                explanation = "Süre dolduğunda bilgi kartını açık uygulamanın üzerinde gösterir.",
                granted = permissions.overlay,
                onClick = onOpenOverlayAccess,
            )
            PermissionCard(
                icon = Icons.Default.Notifications,
                title = "Bildirim",
                explanation = "Android'in arka planda sayacı çalıştırabilmesi için gereklidir.",
                granted = permissions.notification,
                onClick = onNotificationAccess,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Security, null, tint = BilGecSuccess)
                    Column {
                        Text("Tamamen cihazında", fontWeight = FontWeight.Bold)
                        Text(
                            "Soru geçmişin ve kullanım verin hiçbir sunucuya gönderilmez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(42.dp)
            Text("BilGeç", style = MaterialTheme.typography.titleLarge)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(20.dp),
                tint = BilGecRed,
            )
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(BilGecRed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Key, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun ProtectionCard(
    enabled: Boolean,
    canEnable: Boolean,
    elapsedMs: Long,
    intervalMs: Long,
    selectedApps: Int,
    pausedUntilMs: Long?,
    onToggle: (Boolean) -> Unit,
) {
    val cardColor = if (enabled) BilGecRed else MaterialTheme.colorScheme.surface
    val contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface
    val remaining = (intervalMs - elapsedMs).coerceAtLeast(0L)
    val progress = if (intervalMs > 0) (elapsedMs.toFloat() / intervalMs).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = cardColor,
        contentColor = contentColor,
        shadowElevation = if (enabled) 8.dp else 2.dp,
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (enabled) Icons.Default.Shield else Icons.Default.Pause, null)
                    Column {
                        Text("BİLGEÇ KORUMASI", style = MaterialTheme.typography.labelMedium)
                        Text(if (enabled) "Aktif" else "Kapalı", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = canEnable || enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BilGecRed,
                        checkedTrackColor = Color.White,
                        checkedBorderColor = Color.Transparent,
                    ),
                )
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.22f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pausedUntilMs != null) "DURAKLATILDI" else "SIRADAKİ MİNİ MOLA",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.74f),
                    )
                    Text(
                        if (pausedUntilMs != null) {
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntilMs))
                        } else {
                            formatDuration(remaining)
                        },
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        "$selectedApps uygulama izleniyor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(78.dp),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.18f),
                        strokeWidth = 7.dp,
                    )
                    Icon(
                        if (enabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = BilGecRed, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = BilGecRed)
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = CircleShape, color = if (granted) Color(0xFFDDF5E9) else MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    if (granted) Icons.Default.Check else icon,
                    null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = if (granted) BilGecSuccess else MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!granted) {
                    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text("İzin ver →") }
                } else {
                    Text("Hazır", style = MaterialTheme.typography.labelLarge, color = BilGecSuccess)
                }
            }
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandHeader()
        ScreenHeader(
            kicker = "ODAK ALANI",
            title = "Nerede mola\nvereceğini seç.",
            description = "Seçilen uygulamaların süreleri ortak sayaçta toplanır.",
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = BilGecInk,
            contentColor = Color.White,
        ) {
            Row(
                Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("AKTİF SEÇİM", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
                    Text("${preferences.selectedPackages.size} uygulama", style = MaterialTheme.typography.headlineSmall)
                }
                Icon(Icons.Default.Apps, null, tint = BilGecRed, modifier = Modifier.size(34.dp))
            }
        }
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
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ScreenHeader(kicker: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(kicker, style = MaterialTheme.typography.labelMedium, color = BilGecRed)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SocialAppRow(app: SocialApp, selected: Boolean, onSelected: (Boolean) -> Unit) {
    val context = LocalContext.current
    val appInfo = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationInfo(app.packageName, 0) }.getOrNull()
    }
    val icon: ImageBitmap? = remember(appInfo) {
        appInfo?.let { context.packageManager.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) BilGecRed else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (icon != null) {
                Image(icon, app.displayName, Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Smartphone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f)) {
                Text(app.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (appInfo == null) "Yüklü değil · yine de seçilebilir" else "Cihazda yüklü",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = onSelected,
                colors = CheckboxDefaults.colors(checkedColor = BilGecRed),
            )
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
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader()
        ScreenHeader(
            kicker = "KONTROL SENDE",
            title = "BilGeç'i sana\ngöre ayarla.",
            description = "Mola sıklığını, görünümü ve sessiz zamanı belirle.",
        )

        SettingsCard(title = "Soru aralığı", icon = Icons.Default.Timer) {
            Text("Seçili uygulamalardaki toplam aktif süre.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.intervalOptions.forEach { minutes ->
                    FilterChip(
                        selected = preferences.intervalMinutes == minutes,
                        onClick = { preferences.intervalMinutes = minutes; onChanged() },
                        label = { Text("$minutes dk") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BilGecRed,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        SettingsCard(title = "Sessiz zaman", icon = Icons.Default.Pause) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { preferences.pauseForOneHour(); onChanged() },
                    modifier = Modifier.weight(1f),
                ) { Text("1 saat") }
                OutlinedButton(
                    onClick = { preferences.pauseForToday(); onChanged() },
                    modifier = Modifier.weight(1f),
                ) { Text("Bugün") }
            }
            if (preferences.isPaused) {
                Button(
                    onClick = { preferences.resumeNow(); onChanged() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Şimdi devam et") }
            }
        }

        SettingsCard(title = "Görünüm", icon = Icons.Default.AutoAwesome) {
            DarkMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = preferences.darkMode == mode,
                        onClick = { preferences.darkMode = mode; onChanged() },
                    )
                    Text(
                        when (mode) {
                            DarkMode.SYSTEM -> "Sistem ayarı"
                            DarkMode.LIGHT -> "Açık tema"
                            DarkMode.DARK -> "Koyu tema"
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = BilGecInk,
            contentColor = Color.White,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SORU KÜTÜPHANESİ", style = MaterialTheme.typography.labelMedium, color = BilGecRed)
                Text(questionCount.toString(), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("Çevrimdışı, hesap gerektirmeyen Türkçe soru.", color = Color.White.copy(alpha = 0.72f))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Text("BilGeç 1.1", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Ülke verileri mledoze/countries, element verileri Periodic-Table-JSON, bayraklar flag-icons kaynaklarından paketlenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                ) { Text("Tüm verileri sıfırla") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Veriler sıfırlansın mı?") },
            text = { Text("Sayaç, istatistikler, ayarlar ve görülen soru geçmişi silinir.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }) { Text("Sıfırla") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = BilGecRed)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
