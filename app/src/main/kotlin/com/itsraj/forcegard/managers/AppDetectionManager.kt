// managers/AppDetectionManager.kt
package com.itsraj.forcegard.managers

import android.content.Context
import android.util.Log
import com.itsraj.forcegard.models.AppSessionState
import com.itsraj.forcegard.utils.AppPackages

class AppDetectionManager(
    private val context: Context
) {
    private val appPackages = AppPackages(context)
    private var currentForegroundApp: String? = null
    
    private val listeners = mutableListOf<AppStateListener>()
    
    companion object {
        private const val TAG = "AppDetectionManager"
    }
    
    interface AppStateListener {
        fun onAppOpened(packageName: String)
        fun onAppClosed(packageName: String)
        fun onForegroundChanged(from: String?, to: String)
    }
    
    fun initialize() {
        Thread {
            try {
                val count = appPackages.scanAndSavePackages()
                Log.d(TAG, "✅ Loaded $count monitored apps")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to scan apps: ${e.message}")
            }
        }.start()
    }
    
    fun shouldMonitor(packageName: String): Boolean {
        return appPackages.shouldMonitor(packageName)
    }
    
    fun handleAppDetection(packageName: String): AppSessionState {
        val isMonitored = shouldMonitor(packageName)
        
        // Notify foreground change
        if (currentForegroundApp != packageName) {
            notifyForegroundChanged(currentForegroundApp, packageName)
            currentForegroundApp = packageName
            
            if (isMonitored) {
                notifyAppOpened(packageName)
            }
        }
        
        return AppSessionState(
            packageName = packageName,
            isMonitored = isMonitored,
            hasActiveTimer = false, // Will be updated by TimerManager
            isInCooldown = false    // Will be updated by CooldownManager
        )
    }
    
    fun addListener(listener: AppStateListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: AppStateListener) {
        listeners.remove(listener)
    }
    
    private fun notifyAppOpened(packageName: String) {
        listeners.forEach { it.onAppOpened(packageName) }
    }
    
    private fun notifyAppClosed(packageName: String) {
        listeners.forEach { it.onAppClosed(packageName) }
    }
    
    private fun notifyForegroundChanged(from: String?, to: String) {
        listeners.forEach { it.onForegroundChanged(from, to) }
    }
    
    fun getCurrentForegroundApp(): String? = currentForegroundApp
}

    // managers/AppDetectionManager.kt (ADD THIS)

interface AppStateListener {
    fun onAppOpened(packageName: String)
    fun onAppClosed(packageName: String)
    fun onForegroundChanged(from: String?, to: String)
    fun onAppExitAttempt(packageName: String) // NEW!
}