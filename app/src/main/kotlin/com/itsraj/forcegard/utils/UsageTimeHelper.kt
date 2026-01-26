package com.itsraj.forcegard.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object AdvancedUsageHelper {

    data class UsageResult(
        val totalTimeMillis: Long,
        val lastUsedTime: Long,
        val isCurrentlyForeground: Boolean
    )

    fun getTodayUsage(
        context: Context,
        packageName: String
    ): UsageResult {

        val usm =
            context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val startOfDay = startOfToday()
        val now = System.currentTimeMillis()

        val events = usm.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()

        var lastForegroundTime = 0L
        var totalTime = 0L
        var lastUsed = 0L
        var isForeground = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.packageName != packageName) continue

            when (event.eventType) {

                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastForegroundTime = event.timeStamp
                    lastUsed = event.timeStamp
                    isForeground = true
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (lastForegroundTime > 0) {
                        val duration =
                            event.timeStamp - lastForegroundTime

                        if (duration > 0) {
                            totalTime += duration
                        }
                    }
                    lastForegroundTime = 0L
                    isForeground = false
                }
            }
        }

        // App still running in foreground
        if (isForeground && lastForegroundTime > 0) {
            val duration = now - lastForegroundTime
            if (duration > 0) totalTime += duration
        }

        return UsageResult(
            totalTimeMillis = totalTime,
            lastUsedTime = lastUsed,
            isCurrentlyForeground = isForeground
        )
    }

    fun getTotalScreenTimeToday(context: Context): Long {

        val usm =
            context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

        val startOfDay = startOfToday()
        val now = System.currentTimeMillis()

        val events = usm.queryEvents(startOfDay, now)
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
                    val start =
                        activeApps.remove(event.packageName)

                    if (start != null) {
                        val duration =
                            event.timeStamp - start
                        if (duration > 0) totalTime += duration
                    }
                }
            }
        }

        val nowTime = System.currentTimeMillis()
        activeApps.values.forEach { start ->
            val duration = nowTime - start
            if (duration > 0) totalTime += duration
        }

        return totalTime
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}