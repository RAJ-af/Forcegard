package com.itsraj.forcegard.limits

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import java.util.*

class SpendLimitManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SpendLimitPrefs", Context.MODE_PRIVATE)
    private val dailyLimitManager = DailyLimitManager(context)

    private var currentAppStartTime: Long = 0
    private var currentAppPackage: String? = null
    private var isScreenOn: Boolean = true // Assume on initially

    companion object {
        private const val TAG = "SpendLimitManager"
        private const val KEY_USAGE_DAILY = "usage_daily"
        private const val KEY_USAGE_WEEKLY = "usage_weekly"
        private const val KEY_USAGE_MONTHLY = "usage_monthly"
        private const val KEY_LAST_RESET_DAILY = "last_reset_daily"
        private const val KEY_LAST_RESET_WEEKLY = "last_reset_weekly"
        private const val KEY_LAST_RESET_MONTHLY = "last_reset_monthly"
    }

    init {
        checkResets()
    }

    fun onScreenOn() {
        Log.d(TAG, "Screen ON")
        isScreenOn = true
        startSession(currentAppPackage)
    }

    fun onScreenOff() {
        Log.d(TAG, "Screen OFF")
        stopSession()
        isScreenOn = false
    }

    fun updateForegroundApp(packageName: String) {
        if (packageName == currentAppPackage) return
        Log.d(TAG, "App changed: $packageName")
        stopSession()
        startSession(packageName)
    }

    private fun startSession(packageName: String?) {
        currentAppPackage = packageName
        if (isScreenOn && packageName != null && !isIgnoredApp(packageName)) {
            currentAppStartTime = System.currentTimeMillis()
            Log.d(TAG, "Starting usage session for $packageName")
        } else {
            currentAppStartTime = 0
        }
    }

    private fun stopSession() {
        if (currentAppStartTime > 0) {
            val duration = System.currentTimeMillis() - currentAppStartTime
            if (duration > 0) {
                addUsage(duration)
                Log.d(TAG, "Added usage: ${duration / 1000}s for $currentAppPackage")
            }
        }
        currentAppStartTime = 0
    }

    private fun isIgnoredApp(packageName: String): Boolean {
        // Launcher (home screen) time must ALWAYS be ignored.
        if (isLauncherApp(packageName)) return true
        // Only non-system apps are counted
        if (AllowedAppsManager.isAllowedWhenLimited(packageName)) return true
        return false
    }

    private fun isLauncherApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos.any { it.activityInfo.packageName == packageName }
    }

    private fun addUsage(durationMs: Long) {
        checkResets()
        val daily = prefs.getLong(KEY_USAGE_DAILY, 0) + durationMs
        val weekly = prefs.getLong(KEY_USAGE_WEEKLY, 0) + durationMs
        val monthly = prefs.getLong(KEY_USAGE_MONTHLY, 0) + durationMs

        prefs.edit()
            .putLong(KEY_USAGE_DAILY, daily)
            .putLong(KEY_USAGE_WEEKLY, weekly)
            .putLong(KEY_USAGE_MONTHLY, monthly)
            .apply()
    }

    private fun checkResets() {
        val now = Calendar.getInstance()

        // Daily reset (Midnight)
        val lastDaily = prefs.getLong(KEY_LAST_RESET_DAILY, 0)
        if (isNewDay(lastDaily, now)) {
            prefs.edit()
                .putLong(KEY_USAGE_DAILY, 0)
                .putLong(KEY_LAST_RESET_DAILY, now.timeInMillis)
                .apply()
        }

        // Weekly reset (Monday)
        val lastWeekly = prefs.getLong(KEY_LAST_RESET_WEEKLY, 0)
        if (isNewWeek(lastWeekly, now)) {
            prefs.edit()
                .putLong(KEY_USAGE_WEEKLY, 0)
                .putLong(KEY_LAST_RESET_WEEKLY, now.timeInMillis)
                .apply()
        }

        // Monthly reset (1st)
        val lastMonthly = prefs.getLong(KEY_LAST_RESET_MONTHLY, 0)
        if (isNewMonth(lastMonthly, now)) {
            prefs.edit()
                .putLong(KEY_USAGE_MONTHLY, 0)
                .putLong(KEY_LAST_RESET_MONTHLY, now.timeInMillis)
                .apply()
        }
    }

    private fun isNewDay(lastReset: Long, now: Calendar): Boolean {
        if (lastReset == 0L) return true
        val last = Calendar.getInstance().apply { timeInMillis = lastReset }
        return now.get(Calendar.DAY_OF_YEAR) != last.get(Calendar.DAY_OF_YEAR) ||
                now.get(Calendar.YEAR) != last.get(Calendar.YEAR)
    }

    private fun isNewWeek(lastReset: Long, now: Calendar): Boolean {
        if (lastReset == 0L) return true
        val last = Calendar.getInstance().apply { timeInMillis = lastReset }
        // If last reset was before this week's Monday
        val thisMonday = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (now.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                add(Calendar.DAY_OF_YEAR, -6)
            }
        }
        return last.before(thisMonday)
    }

    private fun isNewMonth(lastReset: Long, now: Calendar): Boolean {
        if (lastReset == 0L) return true
        val last = Calendar.getInstance().apply { timeInMillis = lastReset }
        return now.get(Calendar.MONTH) != last.get(Calendar.MONTH) ||
                now.get(Calendar.YEAR) != last.get(Calendar.YEAR)
    }

    fun isLimitReached(): Boolean {
        val config = dailyLimitManager.getConfig() ?: return false
        if (!config.enabled) return false

        val currentUsage = when (config.planDays) {
            1 -> prefs.getLong(KEY_USAGE_DAILY, 0)
            7 -> prefs.getLong(KEY_USAGE_WEEKLY, 0)
            30 -> prefs.getLong(KEY_USAGE_MONTHLY, 0)
            else -> prefs.getLong(KEY_USAGE_DAILY, 0)
        }

        // Include current session
        var sessionDuration = 0L
        if (currentAppStartTime > 0) {
            sessionDuration = System.currentTimeMillis() - currentAppStartTime
        }

        return (currentUsage + sessionDuration) >= (config.limitMinutes * 60 * 1000L)
    }

    fun getUsageMillis(): Long {
        val config = dailyLimitManager.getConfig() ?: return 0L
        val baseUsage = when (config.planDays) {
            1 -> prefs.getLong(KEY_USAGE_DAILY, 0)
            7 -> prefs.getLong(KEY_USAGE_WEEKLY, 0)
            30 -> prefs.getLong(KEY_USAGE_MONTHLY, 0)
            else -> prefs.getLong(KEY_USAGE_DAILY, 0)
        }
        var sessionDuration = 0L
        if (currentAppStartTime > 0) {
            sessionDuration = System.currentTimeMillis() - currentAppStartTime
        }
        return baseUsage + sessionDuration
    }

    fun getLimitMillis(): Long {
        val config = dailyLimitManager.getConfig() ?: return 0L
        return config.limitMinutes * 60 * 1000L
    }

    fun getNextResetDate(): Date {
        val config = dailyLimitManager.getConfig() ?: return Date()
        val now = Calendar.getInstance()
        val reset = now.clone() as Calendar

        when (config.planDays) {
            1 -> {
                reset.add(Calendar.DAY_OF_YEAR, 1)
                reset.set(Calendar.HOUR_OF_DAY, 0)
                reset.set(Calendar.MINUTE, 0)
                reset.set(Calendar.SECOND, 0)
            }
            7 -> {
                reset.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                if (now.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || now.after(reset)) {
                    reset.add(Calendar.WEEK_OF_YEAR, 1)
                }
                reset.set(Calendar.HOUR_OF_DAY, 0)
                reset.set(Calendar.MINUTE, 0)
            }
            30 -> {
                reset.add(Calendar.MONTH, 1)
                reset.set(Calendar.DAY_OF_MONTH, 1)
                reset.set(Calendar.HOUR_OF_DAY, 0)
                reset.set(Calendar.MINUTE, 0)
            }
        }
        return reset.time
    }
}
