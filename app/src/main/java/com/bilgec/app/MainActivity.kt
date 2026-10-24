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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
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
import com.bilgec.app.quiz.QuizQuestion
import com.bilgec.app.settings.AppPreferences
import com.bilgec.app.settings.DarkMode
import com.bilgec.app.settings.DailyStats
import com.bilgec.app.settings.DayProgress
import com.bilgec.app.settings.SocialApp
import com.bilgec.app.settings.supportedSocialApps
import com.bilgec.app.tracking.TrackerService
import com.bilgec.app.ui.theme.BilGecInk
import com.bilgec.app.ui.theme.BilGecMidnight
import com.bilgec.app.ui.theme.BilGecPeach
import com.bilgec.app.ui.theme.BilGecRed
import com.bilgec.app.ui.theme.BilGecSuccess
import com.bilgec.app.ui.theme.BilGecTheme
import java.text.DateFormat
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    // İzin ekranından geri dönünce Compose tarafını yenilemek için kullanılıyor.
    private var refreshCounter by mutableIntStateOf(0)
    private var questionCount by mutableIntStateOf(0)
    private var weakQuestions by mutableStateOf<List<QuizQuestion>>(emptyList())
    private var favoriteQuestions by mutableStateOf<List<QuizQuestion>>(emptyList())

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshCounter++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = AppPreferences(this)
        loadQuestionCount()
        loadReviewLibrary()

        setContent {
            refreshCounter
            BilGecTheme(preferences.darkMode) {
                BilGecApp(
                    preferences = preferences,
                    permissions = permissionState(),
                    questionCount = questionCount,
                    weakQuestions = weakQuestions,
                    favoriteQuestions = favoriteQuestions,
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
                    onReviewQuestion = ::openReviewQuestion,
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
        loadReviewLibrary()
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
            weakQuestions = emptyList()
            favoriteQuestions = emptyList()
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

    private fun loadReviewLibrary() {
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = QuestionRepository(BilGecDatabase.get(this@MainActivity).questionDao())
            weakQuestions = runCatching { repository.weakQuestions() }.getOrDefault(emptyList())
            favoriteQuestions = runCatching { repository.favoriteQuestions() }.getOrDefault(emptyList())
        }
    }

    private fun openReviewQuestion(question: QuizQuestion) {
        startActivity(
            Intent(this, QuestionActivity::class.java)
                .putExtra(QuestionActivity.extraQuestionId, question.id)
                .putExtra(QuestionActivity.extraReviewMode, true),
        )
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
    REVIEW("Tekrar", Icons.Default.Replay),
    APPS("Takip", Icons.Default.Apps),
    SETTINGS("Ayarlar", Icons.Default.Settings),
}

@Composable
private fun BilGecApp(
    preferences: AppPreferences,
    permissions: PermissionState,
    questionCount: Int,
    weakQuestions: List<QuizQuestion>,
    favoriteQuestions: List<QuizQuestion>,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onNotificationAccess: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onChanged: () -> Unit,
    onReset: () -> Unit,
    onReviewQuestion: (QuizQuestion) -> Unit,
) {
    var page by remember { mutableStateOf(AppPage.HOME) }
    var elapsedMs by remember { mutableLongStateOf(preferences.elapsedMs) }
    var stats by remember { mutableStateOf(preferences.todayStats()) }

    LaunchedEffect(Unit) {
        // Sayaç ana ekranda geriden gelmesin diye saniyede bir güncelliyoruz.
        while (true) {
            elapsedMs = preferences.elapsedMs
            stats = preferences.todayStats()
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomMenu(page = page, onSelect = { page = it }) },
    ) { padding ->
        when (page) {
            AppPage.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                preferences = preferences,
                permissions = permissions,
                elapsedMs = elapsedMs,
                stats = stats,
                weeklyStats = preferences.recentStats(),
                dailyGoal = preferences.dailyGoal,
                questionCount = questionCount,
                onStart = onStart,
                onStop = onStop,
                onOpenUsageAccess = onOpenUsageAccess,
                onOpenOverlayAccess = onOpenOverlayAccess,
                onNotificationAccess = onNotificationAccess,
            )
            AppPage.REVIEW -> ReviewScreen(
                modifier = Modifier.padding(padding),
                weakQuestions = weakQuestions,
                favoriteQuestions = favoriteQuestions,
                onQuestion = onReviewQuestion,
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
private fun BottomMenu(page: AppPage, onSelect: (AppPage) -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    weeklyStats: List<DayProgress>,
    dailyGoal: Int,
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AppHeader()
        Text(
            "Kaydırmadan önce\nbir şey keşfet.",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            if (questionCount > 0) {
                "Seçtiğin uygulamalarda vakit geçirirken kısa bir bilgi molası. $questionCount çevrimdışı soru, sıfır hesap."
            } else {
                "Seçtiğin uygulamalarda vakit geçirirken kısa bir bilgi molası. Tamamen çevrimdışı, sıfır hesap."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TimerCard(
            enabled = preferences.trackingEnabled,
            canEnable = permissions.allGranted && preferences.selectedPackages.isNotEmpty(),
            elapsedMs = elapsedMs,
            intervalMs = preferences.intervalMs,
            selectedApps = preferences.selectedPackages.size,
            pausedUntilMs = preferences.pauseUntilMs.takeIf { preferences.isPaused },
            onToggle = { if (it) onStart() else onStop() },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallStatCard(Icons.Default.School, stats.solved.toString(), "Bugün", Modifier.weight(1f))
            SmallStatCard(Icons.Default.CheckCircle, stats.correct.toString(), "Doğru", Modifier.weight(1f))
            SmallStatCard(Icons.Default.LocalFireDepartment, stats.streak.toString(), "Gün seri", Modifier.weight(1f))
        }

        DailyGoalCard(solved = stats.solved, goal = dailyGoal)
        WeeklyProgressCard(days = weeklyStats)

        if (!permissions.allGranted) {
            SectionHeading("KURULUM", "Üç küçük adım kaldı")
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
                color = BilGecSuccess.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, BilGecSuccess.copy(alpha = 0.25f)),
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
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppLogo(42.dp)
            Text("BilGeç", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = BilGecRed)
                Text("GÜNLÜK MERAK", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AppLogo(size: androidx.compose.ui.unit.Dp) {
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
private fun TimerCard(
    enabled: Boolean,
    canEnable: Boolean,
    elapsedMs: Long,
    intervalMs: Long,
    selectedApps: Int,
    pausedUntilMs: Long?,
    onToggle: (Boolean) -> Unit,
) {
    val cardColor = Color.Transparent
    val contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface
    val remaining = (intervalMs - elapsedMs).coerceAtLeast(0L)
    val progress = if (intervalMs > 0) (elapsedMs.toFloat() / intervalMs).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = cardColor,
        contentColor = contentColor,
        shadowElevation = if (enabled) 14.dp else 4.dp,
        border = BorderStroke(
            1.dp,
            if (enabled) Color.White.copy(alpha = 0.13f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier
                .background(
                    if (enabled) {
                        Brush.linearGradient(listOf(BilGecMidnight, Color(0xFF67243B), BilGecRed))
                    } else {
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant),
                        )
                    },
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
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
private fun SmallStatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = BilGecPeach.copy(alpha = 0.65f)) {
                Icon(icon, null, tint = BilGecRed, modifier = Modifier.padding(7.dp).size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyGoalCard(solved: Int, goal: Int) {
    val progress = (solved.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val completed = solved >= goal
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (completed) BilGecSuccess.copy(alpha = 0.11f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (completed) BilGecSuccess.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (completed) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        tint = if (completed) BilGecSuccess else BilGecRed,
                    )
                    Column {
                        Text("Günlük hedef", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (completed) "Bugünün hedefi tamamlandı" else "$goal bilgi molasına ulaş",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("$solved / $goal", style = MaterialTheme.typography.titleLarge)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (completed) BilGecSuccess else BilGecRed,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyProgressCard(days: List<DayProgress>) {
    val maxSolved = days.maxOfOrNull { it.solved }?.coerceAtLeast(1) ?: 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = BilGecMidnight,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("SON 7 GÜN", style = MaterialTheme.typography.labelMedium, color = BilGecPeach)
                    Text("Merak ritmin", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "${days.sumOf { it.solved }} soru",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val barHeight = if (day.solved == 0) 5.dp else (12 + 50 * day.solved / maxSolved).dp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(day.solved.toString(), style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(BilGecPeach, BilGecRed))),
                            )
                        }
                        Text(
                            day.date.dayOfWeek
                                .getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("tr-TR"))
                                .take(2),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.66f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    modifier: Modifier,
    weakQuestions: List<QuizQuestion>,
    favoriteQuestions: List<QuizQuestion>,
    onQuestion: (QuizQuestion) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppHeader()
        ScreenHeader(
            kicker = "AKILLI TEKRAR",
            title = "Bildiklerin kalsın,\nzorlandıkların kolaylaşsın.",
            description = "Yanlış cevaplarını yeniden çöz veya sevdiğin bilgileri favorilerden aç.",
        )

        ReviewSection(
            icon = Icons.Default.Replay,
            title = "Tekrar bekleyenler",
            questions = weakQuestions,
            emptyText = "Henüz yanlış cevap yok. Böyle devam!",
            onQuestion = onQuestion,
        )
        ReviewSection(
            icon = Icons.Default.Star,
            title = "Favoriler",
            questions = favoriteQuestions,
            emptyText = "Soru ekranındaki yıldızla bilgileri buraya ekleyebilirsin.",
            onQuestion = onQuestion,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReviewSection(
    icon: ImageVector,
    title: String,
    questions: List<QuizQuestion>,
    emptyText: String,
    onQuestion: (QuizQuestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = BilGecRed)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    questions.size.toString(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (questions.isEmpty()) {
                Text(
                    emptyText,
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                questions.take(10).forEach { question ->
                    ReviewQuestionRow(question = question, onClick = { onQuestion(question) })
                }
            }
        }
    }
}

@Composable
private fun ReviewQuestionRow(question: QuizQuestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(question.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = BilGecRed)
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Soruyu yeniden çöz", tint = BilGecRed)
        }
    }
}

@Composable
private fun SectionHeading(kicker: String, title: String) {
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
        AppHeader()
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
        AppHeader()
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

        SettingsCard(title = "Günlük hedef", icon = Icons.AutoMirrored.Filled.TrendingUp) {
            Text(
                "Günde kaç bilgi molasını tamamlamak istediğini seç.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppPreferences.dailyGoalOptions.forEach { goal ->
                    FilterChip(
                        selected = preferences.dailyGoal == goal,
                        onClick = { preferences.dailyGoal = goal; onChanged() },
                        label = { Text(goal.toString()) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BilGecRed,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }

        SettingsCard(title = "Soru kategorileri", icon = Icons.Default.School) {
            Text(
                "BilGeç yalnızca açık kategorilerden soru seçer. En az biri açık kalır.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppPreferences.questionCategories.forEach { category ->
                val selected = category in preferences.selectedCategories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(category, style = MaterialTheme.typography.titleMedium)
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { checked ->
                            val updated = preferences.selectedCategories.toMutableSet().apply {
                                if (checked) add(category) else remove(category)
                            }
                            if (updated.isNotEmpty()) {
                                preferences.selectedCategories = updated
                                onChanged()
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = BilGecRed),
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
                Text(
                    questionCount.takeIf { it > 0 }?.toString() ?: "—",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("Çevrimdışı, hesap gerektirmeyen Türkçe soru.", color = Color.White.copy(alpha = 0.72f))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Text("BilGeç 1.3", style = MaterialTheme.typography.labelLarge)
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
