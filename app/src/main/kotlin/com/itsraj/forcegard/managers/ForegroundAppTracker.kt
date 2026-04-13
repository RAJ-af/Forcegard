package com.itsraj.forcegard.managers

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ForegroundAppTracker(private val context: Context) {
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentForegroundApp: String? = null
    private val listeners = mutableListOf<ForegroundChangeListener>()
    
    companion object {
        private const val TAG = "ForegroundAppTracker"
    }
    
    interface ForegroundChangeListener {
        fun onForegroundAppChanged(packageName: String, source: String)
    }
    
    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // We only care about window changes for foreground tracking
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            
            if (packageName != currentForegroundApp) {
                Log.d(TAG, "📱 Foreground App Changed: $packageName")
                currentForegroundApp = packageName
                notifyForegroundChanged(packageName, "Accessibility")
            }
        }
    }

    fun getForegroundApp(): String? = currentForegroundApp
    
    fun getCurrentForegroundApp(): String? = currentForegroundApp
    
    fun addListener(listener: ForegroundChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: ForegroundChangeListener) {
        listeners.remove(listener)
    }
    
    private fun notifyForegroundChanged(packageName: String, source: String) {
        listeners.forEach { it.onForegroundAppChanged(packageName, source) }
    }
}
