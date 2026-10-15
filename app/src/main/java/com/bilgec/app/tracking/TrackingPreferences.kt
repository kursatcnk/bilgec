package com.bilgec.app.tracking

import android.content.Context

object TrackingPreferences {
    const val phaseZeroIntervalMs = 60_000L

    private const val preferencesName = "tracking"
    private const val keyEnabled = "enabled"
    private const val keyElapsedMs = "elapsed_ms"
    private const val keyAwaitingQuestion = "awaiting_question"

    fun isEnabled(context: Context): Boolean = preferences(context)
        .getBoolean(keyEnabled, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(keyEnabled, enabled).apply()
    }

    fun elapsedMs(context: Context): Long = preferences(context)
        .getLong(keyElapsedMs, 0L)

    fun setElapsedMs(context: Context, elapsedMs: Long) {
        preferences(context).edit().putLong(keyElapsedMs, elapsedMs).apply()
    }

    fun isAwaitingQuestion(context: Context): Boolean = preferences(context)
        .getBoolean(keyAwaitingQuestion, false)

    fun setAwaitingQuestion(context: Context, awaiting: Boolean) {
        preferences(context).edit().putBoolean(keyAwaitingQuestion, awaiting).apply()
    }

    fun resetAfterQuestion(context: Context) {
        preferences(context).edit()
            .putLong(keyElapsedMs, 0L)
            .putBoolean(keyAwaitingQuestion, false)
            .apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
}

