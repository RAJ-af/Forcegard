// managers/AppDetectionManager.kt
package com.itsraj.forcegard.managers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.itsraj.forcegard.models.AppSessionState
import com.itsraj.forcegard.utils.AppCategory
import com.itsraj.forcegard.utils.AppPackages
import com.itsraj.forcegard.utils.AppScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppDetectionManager(
    private val context: Context
) {
    private val appPackages = AppPackages(context)
    private val appScanner = AppScanner(context)
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Perform a full scan to populate category cache
                Log.d(TAG, "🔍 Initializing app list and resolving categories...")
                val apps = appScanner.scanAllApps()
                Log.d(TAG, "✅ Initialized ${apps.size} apps with categories")

                val count = appPackages.scanAndSavePackages()
                Log.d(TAG, "✅ Loaded $count monitored apps")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize apps: ${e.message}")
            }
        }
    }
    
    fun shouldMonitor(packageName: String): Boolean {
        return appPackages.shouldMonitor(packageName)
    }
    
    fun handleAppDetection(packageName: String): AppSessionState {
        val isMonitored = shouldMonitor(packageName)
        
        // Determine category (from cache or system)
        var category = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appScanner.categorizeApp(appInfo, packageName, false)
        } catch (e: Exception) {
            AppCategory.OTHER
        }

        // If still OTHER, trigger background lookup for next time
        if (isMonitored && category == AppCategory.OTHER) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    appScanner.categorizeApp(appInfo, packageName, true)
                    Log.d(TAG, "🌐 Background category lookup completed for $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Background lookup failed for $packageName: ${e.message}")
                }
            }
        }

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
            isInCooldown = false,    // Will be updated by CooldownManager
            category = category
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

    fun getAppCategory(packageName: String): AppCategory {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appScanner.categorizeApp(appInfo, packageName, false)
        } catch (e: Exception) {
            AppCategory.OTHER
        }
    }
}