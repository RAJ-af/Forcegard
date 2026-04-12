package com.itsraj.forcegard.managers

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class PickupManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("PickupPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PICKUPS_TODAY = "pickups_today"
        private const val KEY_LAST_PICKUP_DATE = "last_pickup_date"
        private const val KEY_TOTAL_PICKUPS = "total_pickups"
        private const val KEY_DAYS_TRACKED = "days_tracked"
    }

    fun recordPickup() {
        checkReset()
        val today = prefs.getInt(KEY_PICKUPS_TODAY, 0)
        val total = prefs.getLong(KEY_TOTAL_PICKUPS, 0)

        prefs.edit()
            .putInt(KEY_PICKUPS_TODAY, today + 1)
            .putLong(KEY_TOTAL_PICKUPS, total + 1)
            .apply()
    }

    private fun checkReset() {
        val lastDate = prefs.getLong(KEY_LAST_PICKUP_DATE, 0L)
        val now = Calendar.getInstance()
        val last = Calendar.getInstance().apply { timeInMillis = lastDate }

        if (now.get(Calendar.DAY_OF_YEAR) != last.get(Calendar.DAY_OF_YEAR) ||
            now.get(Calendar.YEAR) != last.get(Calendar.YEAR)) {

            val daysTracked = prefs.getInt(KEY_DAYS_TRACKED, 0)
            prefs.edit()
                .putInt(KEY_PICKUPS_TODAY, 0)
                .putLong(KEY_LAST_PICKUP_DATE, now.timeInMillis)
                .putInt(KEY_DAYS_TRACKED, daysTracked + 1)
                .apply()
        }
    }

    fun getTodayPickups(): Int {
        checkReset()
        return prefs.getInt(KEY_PICKUPS_TODAY, 0)
    }

    fun getDailyAverageUsage(totalUsageMillis: Long): Long {
        val days = prefs.getInt(KEY_DAYS_TRACKED, 1).coerceAtLeast(1)
        return totalUsageMillis / days
    }
}
