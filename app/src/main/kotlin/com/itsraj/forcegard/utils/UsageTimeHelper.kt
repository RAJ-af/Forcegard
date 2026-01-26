package com.itsraj.forcegard.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageTimeHelper {

    fun getTotalScreenTime(context: Context, startTime: Long, endTime: Long): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startTime, endTime) ?: return 0L
        val event = UsageEvents.Event()

        val activeApps = mutableMapOf<String, Long>()
        var totalTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeApps[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = activeApps.remove(event.packageName)
                    if (start != null) {
                        val duration = event.timeStamp - start
                        if (duration > 0) totalTime += duration
                    }
                }
            }
        }

        activeApps.values.forEach { start ->
            val duration = endTime - start
            if (duration > 0) totalTime += duration
        }

        return totalTime
    }

    fun getTodayTotalUsageMillis(context: Context, resetHour: Int): Long {
        val start = getStartOfTodayCustom(resetHour)
        val now = System.currentTimeMillis()
        return getTotalScreenTime(context, start, now)
    }

    private fun getStartOfTodayCustom(resetHour: Int): Long {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < resetHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, resetHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfToday(): Long = getStartOfTodayCustom(0)

    fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return cal.timeInMillis
    }

    fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeMillis: Long
    )

    fun getMostUsedAppsToday(context: Context, resetHour: Int, limit: Int): List<AppUsageInfo> {
        val startTime = getStartOfTodayCustom(resetHour)
        val endTime = System.currentTimeMillis()

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startTime, endTime) ?: return emptyList()
        val event = UsageEvents.Event()

        val appUsageMap = mutableMapOf<String, Long>()
        val activeApps = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    activeApps[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = activeApps.remove(pkg)
                    if (start != null) {
                        val duration = event.timeStamp - start
                        if (duration > 0) {
                            appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
                        }
                    }
                }
            }
        }

        activeApps.forEach { (pkg, start) ->
            val duration = endTime - start
            if (duration > 0) {
                appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
            }
        }

        val pm = context.packageManager
        return appUsageMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { entry ->
                val appName = try {
                    val appInfo = pm.getApplicationInfo(entry.key, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    entry.key
                }
                AppUsageInfo(entry.key, appName, entry.value)
            }
    }
}
