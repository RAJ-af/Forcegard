package com.itsraj.forcegard.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageTimeHelper {

    fun getTodayTotalUsageMillis(context: Context, resetHour: Int = 0): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()

        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, resetHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    fun getSevenDayAverageMillis(context: Context): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()

        // End of yesterday
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endTime = calendar.timeInMillis

        // 7 days ago
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        val totalTime = stats?.sumOf { it.totalTimeInForeground } ?: 0L

        return totalTime / 7
    }

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeMillis: Long
    )

    fun getMostUsedAppsToday(context: Context, resetHour: Int, limit: Int): List<AppUsageInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, resetHour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val pm = context.packageManager

        return stats?.filter { it.totalTimeInForeground > 0 }
            ?.sortedByDescending { it.totalTimeInForeground }
            ?.take(limit)
            ?.map {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(it.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    it.packageName
                }
                AppUsageInfo(it.packageName, appName, it.totalTimeInForeground)
            } ?: emptyList()
    }
}
