package com.itsraj.forcegard.managers

import android.content.Context
import java.time.LocalDate

object AdvancedCooldownManager {

    private const val PREF = "adv_cooldown_pref"
    private const val KEY_DATE = "cooldown_date"
    private const val KEY_PREFIX = "cooldown_"

    fun startCooldown(
        context: Context,
        packageName: String,
        cooldownMillis: Long
    ) {
        resetIfNewDay(context)

        val endTime = System.currentTimeMillis() + cooldownMillis

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PREFIX + packageName, endTime)
            .apply()
    }

    fun isInCooldown(
        context: Context,
        packageName: String
    ): Boolean {

        resetIfNewDay(context)

        val endTime =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getLong(KEY_PREFIX + packageName, 0L)

        return System.currentTimeMillis() < endTime
    }

    fun getRemainingCooldown(
        context: Context,
        packageName: String
    ): Long {

        val endTime =
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getLong(KEY_PREFIX + packageName, 0L)

        return (endTime - System.currentTimeMillis())
            .coerceAtLeast(0L)
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