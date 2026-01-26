package com.itsraj.forcegard.limits

import android.content.Context
import android.content.SharedPreferences
import com.itsraj.forcegard.utils.UsageTimeHelper

class DailyLimitManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "DailyLimitPrefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LIMIT_MINUTES = "limit_minutes"
        private const val KEY_PLAN_DAYS = "plan_days"
        private const val KEY_RESET_HOUR = "reset_hour"
        private const val KEY_START_EPOCH = "start_epoch"
    }

    fun saveConfig(config: DailyLimitConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_LIMIT_MINUTES, config.limitMinutes)
            .putInt(KEY_PLAN_DAYS, config.planDays)
            .putInt(KEY_RESET_HOUR, config.resetHour)
            .putLong(KEY_START_EPOCH, config.startDayEpoch)
            .apply()
    }

    fun getConfig(): DailyLimitConfig? {
        if (!prefs.contains(KEY_LIMIT_MINUTES)) return null

        return DailyLimitConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            limitMinutes = prefs.getInt(KEY_LIMIT_MINUTES, 0),
            planDays = prefs.getInt(KEY_PLAN_DAYS, 1),
            resetHour = prefs.getInt(KEY_RESET_HOUR, 0),
            startDayEpoch = prefs.getLong(KEY_START_EPOCH, 0L)
        )
    }

    fun isPlanActive(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun getTodayWindow(): Pair<Long, Long> {
        val config = getConfig() ?: return Pair(0L, 0L)
        val now = System.currentTimeMillis()

        var startTime = when (config.planDays) {
            1 -> UsageTimeHelper.getStartOfToday()
            7 -> UsageTimeHelper.getStartOfWeek()
            30 -> UsageTimeHelper.getStartOfMonth()
            else -> UsageTimeHelper.getStartOfToday()
        }

        if (config.planDays == 1 && config.resetHour > 0) {
            startTime += config.resetHour.toLong() * 60 * 60 * 1000L
            if (startTime > now) {
                startTime -= 24 * 60 * 60 * 1000L
            }
        }

        return Pair(startTime, now)
    }
}
