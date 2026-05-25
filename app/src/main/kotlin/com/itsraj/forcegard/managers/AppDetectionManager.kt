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
    
    companion object {
        private const val TAG = "AppDetectionManager"
    }
    
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Perform a full scan to populate category cache
                Log.d(TAG, "🔍 Initializing app list and resolving categories...")
                appScanner.scanAllApps()
                val count = appPackages.scanAndSavePackages()
                Log.d(TAG, "✅ Initialized monitored apps: $count")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize apps: ${e.message}")
            }
        }
    }
    
    fun shouldMonitor(packageName: String): Boolean {
        return appPackages.shouldMonitor(packageName)
    }
    
    fun getAppCategory(packageName: String): AppCategory {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            appScanner.categorizeApp(appInfo, packageName, false)
        } catch (e: Exception) {
            AppCategory.OTHER
        }
    }
}
