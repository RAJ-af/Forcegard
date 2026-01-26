// managers/ForegroundAppTracker.kt
package com.itsraj.forcegard.managers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

class ForegroundAppTracker(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var pollingRunnable: Runnable? = null
    private var currentForegroundApp: String? = null
    
    private val listeners = mutableListOf<ForegroundChangeListener>()
    
    companion object {
        private const val TAG = "ForegroundAppTracker"
        private const val POLLING_INTERVAL = 2000L
    }
    
    interface ForegroundChangeListener {
        fun onForegroundAppChanged(packageName: String, source: String)
    }
    
    fun startTracking() {
        if (pollingRunnable != null) {
            Log.w(TAG, "⚠️ Already tracking")
            return
        }
        
        pollingRunnable = object : Runnable {
            override fun run() {
                val foregroundApp = getForegroundApp()
                
                if (foregroundApp != null && foregroundApp != currentForegroundApp) {
                    currentForegroundApp = foregroundApp
                    notifyForegroundChanged(foregroundApp, "Polling")
                }
                
                handler.postDelayed(this, POLLING_INTERVAL)
            }
        }
        
        handler.post(pollingRunnable!!)
        Log.d(TAG, "✅ Tracking started")
    }
    
    fun stopTracking() {
        pollingRunnable?.let {
            handler.removeCallbacks(it)
            pollingRunnable = null
            Log.d(TAG, "🛑 Tracking stopped")
        }
    }
    
    fun getForegroundApp(): String? {
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "⚠️ Missing USAGE_STATS permission")
            return null
        }
        
        // Try UsageEvents first (more accurate)
        getForegroundAppViaEvents()?.let { return it }
        
        // Fallback to UsageStats
        return getForegroundAppViaStats()
    }
    
    private fun getForegroundAppViaEvents(): String? {
        return try {
            val currentTime = System.currentTimeMillis()
            val usageEvents = usageStatsManager.queryEvents(currentTime - 3000, currentTime)
            
            var lastApp: String? = null
            val event = UsageEvents.Event()
            
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastApp = event.packageName
                }
            }
            
            lastApp
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading usage events: ${e.message}")
            null
        }
    }
    
    private fun getForegroundAppViaStats(): String? {
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                currentTime - 3000,
                currentTime
            )
            
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading usage stats: ${e.message}")
            null
        }
    }
    
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
    
    fun getCurrentForegroundApp(): String? = currentForegroundApp
    
    fun addListener(listener: ForegroundChangeListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: ForegroundChangeListener) {
        listeners.remove(listener)
    }
    
    private fun notifyForegroundChanged(packageName: String, source: String) {
        listeners.forEach { it.onForegroundAppChanged(packageName, source) }
    }
}
