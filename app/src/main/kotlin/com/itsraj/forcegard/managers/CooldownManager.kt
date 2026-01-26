package com.itsraj.forcegard.managers

import android.content.Context
import android.content.SharedPreferences
import com.itsraj.forcegard.models.CooldownData
import com.itsraj.forcegard.models.CooldownReason

class CooldownManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "CooldownPrefs"
        private const val KEY_PREFIX = "cooldown_"
    }

    interface CooldownEventListener {
        fun onCooldownStarted(cooldownData: CooldownData)
        fun onCooldownExpired(packageName: String)
        fun onCooldownActive(packageName: String, remainingMs: Long)
    }

    private val listeners = mutableListOf<CooldownEventListener>()

    fun addListener(listener: CooldownEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CooldownEventListener) {
        listeners.remove(listener)
    }

    fun startCooldown(packageName: String, reason: CooldownReason) {
        val duration = when(reason) {
            CooldownReason.USER_REJECTED -> 5 * 60 * 1000L // 5 mins
            CooldownReason.TIMER_EXPIRED -> 15 * 60 * 1000L // 15 mins
        }
        val endTime = System.currentTimeMillis() + duration
        prefs.edit().putLong(KEY_PREFIX + packageName, endTime).apply()

        val data = CooldownData(packageName, endTime, reason)
        listeners.forEach { it.onCooldownStarted(data) }
    }

    fun isInCooldown(packageName: String): Boolean {
        val endTime = prefs.getLong(KEY_PREFIX + packageName, 0L)
        return System.currentTimeMillis() < endTime
    }

    fun getRemainingCooldownTime(packageName: String): Long? {
        val endTime = prefs.getLong(KEY_PREFIX + packageName, 0L)
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }
}
