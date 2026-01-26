// managers/TimerManager.kt
package com.itsraj.forcegard.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.itsraj.forcegard.models.TimerData

class TimerManager {
    
    private val activeTimers = mutableMapOf<String, TimerData>()
    private val handler = Handler(Looper.getMainLooper())
    private var timerCheckerRunnable: Runnable? = null
    
    private val listeners = mutableListOf<TimerEventListener>()
    
    companion object {
        private const val TAG = "TimerManager"
        private const val CHECK_INTERVAL = 1000L
    }
    
    interface TimerEventListener {
        fun onTimerStarted(timerData: TimerData)
        fun onTimerTick(packageName: String, remainingMinutes: Int, remainingSeconds: Int)
        fun onTimerExpired(packageName: String)
        fun onTimerCancelled(packageName: String)
    }
    
    fun startTimer(packageName: String, durationMinutes: Int) {
        val durationMs = durationMinutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMs
        
        val timerData = TimerData(
            packageName = packageName,
            endTimeMillis = endTime,
            totalDurationMs = durationMs
        )
        
        activeTimers[packageName] = timerData
        
        Log.d(TAG, "⏱️ Timer started: $packageName → $durationMinutes min")
        notifyTimerStarted(timerData)
        
        // Start checker if not running
        if (timerCheckerRunnable == null) {
            startTimerChecker()
        }
    }
    
    fun cancelTimer(packageName: String) {
        activeTimers.remove(packageName)?.let {
            Log.d(TAG, "🛑 Timer cancelled: $packageName")
            notifyTimerCancelled(packageName)
        }
    }
    
    fun hasActiveTimer(packageName: String): Boolean {
        return activeTimers.containsKey(packageName)
    }
    
    fun getActiveTimer(packageName: String): TimerData? {
        return activeTimers[packageName]
    }
    
    fun getRemainingTime(packageName: String): Long? {
        val timerData = activeTimers[packageName] ?: return null
        return (timerData.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
    }
    
    private fun startTimerChecker() {
        timerCheckerRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val expiredTimers = mutableListOf<String>()
                
                activeTimers.forEach { (packageName, timerData) ->
                    val remainingMs = timerData.endTimeMillis - currentTime
                    
                    if (remainingMs <= 0) {
                        expiredTimers.add(packageName)
                        Log.d(TAG, "⏰ Timer expired: $packageName")
                        notifyTimerExpired(packageName)
                    } else {
                        val remainingSeconds = (remainingMs / 1000).toInt()
                        val minutes = remainingSeconds / 60
                        val seconds = remainingSeconds % 60
                        notifyTimerTick(packageName, minutes, seconds)
                    }
                }
                
                expiredTimers.forEach { activeTimers.remove(it) }
                
                if (activeTimers.isNotEmpty()) {
                    handler.postDelayed(this, CHECK_INTERVAL)
                } else {
                    timerCheckerRunnable = null
                }
            }
        }
        handler.post(timerCheckerRunnable!!)
    }
    
    fun addListener(listener: TimerEventListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: TimerEventListener) {
        listeners.remove(listener)
    }
    
    private fun notifyTimerStarted(timerData: TimerData) {
        listeners.forEach { it.onTimerStarted(timerData) }
    }
    
    private fun notifyTimerTick(packageName: String, minutes: Int, seconds: Int) {
        listeners.forEach { it.onTimerTick(packageName, minutes, seconds) }
    }
    
    private fun notifyTimerExpired(packageName: String) {
        listeners.forEach { it.onTimerExpired(packageName) }
    }
    
    private fun notifyTimerCancelled(packageName: String) {
        listeners.forEach { it.onTimerCancelled(packageName) }
    }
    
    fun stopAll() {
        timerCheckerRunnable?.let { handler.removeCallbacks(it) }
        timerCheckerRunnable = null
        activeTimers.clear()
    }
}
