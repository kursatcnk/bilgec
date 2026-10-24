package com.bilgec.app.settings

import android.content.Context
import java.time.LocalDate

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    var trackingEnabled: Boolean
        get() = preferences.getBoolean(keyTrackingEnabled, false)
        set(value) = edit { putBoolean(keyTrackingEnabled, value) }

    var elapsedMs: Long
        get() = preferences.getLong(keyElapsedMs, 0L)
        set(value) = edit { putLong(keyElapsedMs, value.coerceAtLeast(0L)) }

    var awaitingQuestion: Boolean
        get() = preferences.getBoolean(keyAwaitingQuestion, false)
        set(value) = edit { putBoolean(keyAwaitingQuestion, value) }

    var intervalMinutes: Int
        get() = preferences.getInt(keyIntervalMinutes, 30)
        set(value) = edit { putInt(keyIntervalMinutes, value.takeIf { it in intervalOptions } ?: 30) }

    var pauseUntilMs: Long
        get() = preferences.getLong(keyPauseUntil, 0L)
        set(value) = edit { putLong(keyPauseUntil, value) }

    var darkMode: DarkMode
        get() = runCatching {
            DarkMode.valueOf(preferences.getString(keyDarkMode, DarkMode.SYSTEM.name).orEmpty())
        }.getOrDefault(DarkMode.SYSTEM)
        set(value) = edit { putString(keyDarkMode, value.name) }

    var selectedPackages: Set<String>
        get() = preferences.getStringSet(keySelectedPackages, defaultPackages)?.toSet() ?: defaultPackages
        set(value) = edit { putStringSet(keySelectedPackages, value) }

    var selectedCategories: Set<String>
        get() {
            val stored = preferences.getStringSet(keySelectedCategories, questionCategories.toSet()).orEmpty()
            return stored.intersect(questionCategories.toSet()).ifEmpty { questionCategories.toSet() }
        }
        set(value) {
            val safeValue = value.intersect(questionCategories.toSet()).ifEmpty { questionCategories.toSet() }
            edit { putStringSet(keySelectedCategories, safeValue) }
        }

    var dailyGoal: Int
        get() = preferences.getInt(keyDailyGoal, 5).takeIf { it in dailyGoalOptions } ?: 5
        set(value) = edit { putInt(keyDailyGoal, value.takeIf { it in dailyGoalOptions } ?: 5) }

    var currentQuestionId: Int
        get() = preferences.getInt(keyCurrentQuestionId, noQuestion)
        set(value) = edit { putInt(keyCurrentQuestionId, value) }

    var lastCategory: String
        get() = preferences.getString(keyLastCategory, "").orEmpty()
        set(value) = edit { putString(keyLastCategory, value) }

    val intervalMs: Long
        get() = intervalMinutes * 60_000L

    val isPaused: Boolean
        get() = pauseUntilMs > System.currentTimeMillis()

    fun todayStats(): DailyStats {
        val today = LocalDate.now().toString()
        return if (preferences.getString(keyStatsDate, "") == today) {
            DailyStats(
                solved = preferences.getInt(keyTodaySolved, 0),
                correct = preferences.getInt(keyTodayCorrect, 0),
                streak = preferences.getInt(keyStreak, 0),
            )
        } else {
            DailyStats(streak = preferences.getInt(keyStreak, 0))
        }
    }

    fun recentStats(dayCount: Int = 7): List<DayProgress> {
        val history = decodeHistory(preferences.getString(keyStatsHistory, "").orEmpty())
        val today = LocalDate.now()
        return (dayCount - 1 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val values = history[date]
            DayProgress(date = date, solved = values?.first ?: 0, correct = values?.second ?: 0)
        }
    }

    fun recordAnswer(correct: Boolean) {
        val today = LocalDate.now()
        val storedDate = preferences.getString(keyStatsDate, "")
        val lastSolvedDate = preferences.getString(keyLastSolvedDate, "")
        val sameDay = storedDate == today.toString()
        val oldStreak = preferences.getInt(keyStreak, 0)
        val newStreak = when (lastSolvedDate) {
            today.toString() -> oldStreak.coerceAtLeast(1)
            today.minusDays(1).toString() -> oldStreak + 1
            else -> 1
        }

        val solved = (if (sameDay) preferences.getInt(keyTodaySolved, 0) else 0) + 1
        val correctAnswers =
            (if (sameDay) preferences.getInt(keyTodayCorrect, 0) else 0) + if (correct) 1 else 0
        val history = decodeHistory(preferences.getString(keyStatsHistory, "").orEmpty()).toMutableMap()
        history[today] = solved to correctAnswers
        val oldestDate = today.minusDays(29)
        history.keys.filter { it < oldestDate }.forEach(history::remove)

        edit {
            putString(keyStatsDate, today.toString())
            putInt(keyTodaySolved, solved)
            putInt(keyTodayCorrect, correctAnswers)
            putString(keyLastSolvedDate, today.toString())
            putInt(keyStreak, newStreak)
            putString(keyStatsHistory, encodeHistory(history))
        }
    }

    fun pauseForOneHour() {
        pauseUntilMs = System.currentTimeMillis() + 60 * 60 * 1_000L
    }

    fun pauseForToday() {
        val tomorrow = LocalDate.now().plusDays(1).atStartOfDay(systemZone).toInstant().toEpochMilli()
        pauseUntilMs = tomorrow
    }

    fun resumeNow() {
        pauseUntilMs = 0L
    }

    fun resetAfterQuestion() {
        edit {
            putLong(keyElapsedMs, 0L)
            putBoolean(keyAwaitingQuestion, false)
            putInt(keyCurrentQuestionId, noQuestion)
        }
    }

    fun resetAll() {
        preferences.edit().clear().apply()
    }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
    }

    private fun decodeHistory(raw: String): Map<LocalDate, Pair<Int, Int>> = raw
        .split(';')
        .mapNotNull { entry ->
            val parts = entry.split(',')
            if (parts.size != 3) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val solved = parts[1].toIntOrNull() ?: return@mapNotNull null
            val correct = parts[2].toIntOrNull() ?: return@mapNotNull null
            date to (solved to correct)
        }
        .toMap()

    private fun encodeHistory(history: Map<LocalDate, Pair<Int, Int>>): String = history
        .toSortedMap()
        .entries
        .joinToString(";") { (date, values) -> "$date,${values.first},${values.second}" }

    companion object {
        val intervalOptions = listOf(15, 30, 45, 60)
        val dailyGoalOptions = listOf(3, 5, 8, 10)
        val questionCategories = listOf(
            "Bayraklar",
            "Coğrafya",
            "Bilim",
            "Tarih",
            "Sanat",
            "Doğa",
            "Spor",
            "Günlük Bilgi",
        )
        const val noQuestion = -1

        private const val preferencesName = "bilgec_settings"
        private const val keyTrackingEnabled = "tracking_enabled"
        private const val keyElapsedMs = "elapsed_ms"
        private const val keyAwaitingQuestion = "awaiting_question"
        private const val keyIntervalMinutes = "interval_minutes"
        private const val keyPauseUntil = "pause_until"
        private const val keyDarkMode = "dark_mode"
        private const val keySelectedPackages = "selected_packages"
        private const val keySelectedCategories = "selected_categories"
        private const val keyDailyGoal = "daily_goal"
        private const val keyCurrentQuestionId = "current_question_id"
        private const val keyLastCategory = "last_category"
        private const val keyStatsDate = "stats_date"
        private const val keyTodaySolved = "today_solved"
        private const val keyTodayCorrect = "today_correct"
        private const val keyLastSolvedDate = "last_solved_date"
        private const val keyStreak = "streak"
        private const val keyStatsHistory = "stats_history"
        private val defaultPackages = setOf("com.instagram.android")
        private val systemZone = java.time.ZoneId.systemDefault()
    }
}

data class DailyStats(
    val solved: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
)

data class DayProgress(
    val date: LocalDate,
    val solved: Int,
    val correct: Int,
)

enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}
