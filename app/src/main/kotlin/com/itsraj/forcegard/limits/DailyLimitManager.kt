package com.itsraj.forcegard.limits

import android.content.Context
import com.forcegard.usage.AdvancedUsageHelper
import java.time.LocalDate

object AdvancedDailyLimitManager {

    private const val PREF = "adv_daily_limit"
    private const val KEY_DATE = "current_date"
    private const val KEY_PREFIX_USAGE = "usage_"

    data class LimitResult(
        val usedMillis: Long,
        val limitMillis: Long,
        val remainingMillis: Long,
        val isExceeded: Boolean
    )

    fun checkLimit(
        context: Context,
        packageName: String,
        limitMillis: Long
    ): LimitResult {

        resetIfNewDay(context)

        val used =
            AdvancedUsageHelper
                .getTodayUsage(context, packageName)
                .totalTimeMillis

        saveUsage(context, packageName, used)

        val exceeded = used >= limitMillis
        val remaining =
            (limitMillis - used).coerceAtLeast(0L)

        return LimitResult(
            usedMillis = used,
            limitMillis = limitMillis,
            remainingMillis = remaining,
            isExceeded = exceeded
        )
    }

    private fun saveUsage(
        context: Context,
        packageName: String,
        usedMillis: Long
    ) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PREFIX_USAGE + packageName, usedMillis)
            .apply()
    }

    fun getSavedUsage(
        context: Context,
        packageName: String
    ): Long {
        return context
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_PREFIX_USAGE + packageName, 0L)
    }

    private fun resetIfNewDay(context: Context) {
        val prefs =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_DATE, null)

        if (savedDate != today) {
            prefs.edit()
                .clear()
                .putString(KEY_DATE, today)
                .apply()
        }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}