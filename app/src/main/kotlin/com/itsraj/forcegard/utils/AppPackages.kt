package com.itsraj.forcegard.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.itsraj.forcegard.limits.AllowedAppsManager

class AppPackages(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val TAG = "AppPackages"

    companion object {
        private const val PREFS_NAME = "AppPackagesPrefs"
        private const val KEY_LAST_SCAN = "last_scan_time"
    }

    /**
     * Check if an app should be monitored based on its package name and type
     */
    fun shouldMonitor(packageName: String): Boolean {
        // 1. Skip core system apps, launchers, and Forcegard itself
        if (AllowedAppsManager.isAllowedWhenLimited(packageName) ||
            AllowedAppsManager.isLauncherApp(context, packageName)) {
            return false
        }
        
        // 2. Check if it's a pure system app (not updated)
        if (isPureSystemApp(packageName)) {
            return false
        }
        
        // 3. Only monitor apps with launcher icons (user-facing apps)
        if (!hasLauncherIcon(packageName)) {
            return false
        }
        
        // ✅ Monitor all other user apps
        return true
    }

    private fun isPureSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            
            isSystem && !isUpdated
        } catch (e: Exception) {
            false
        }
    }

    private fun hasLauncherIcon(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return intent != null
    }

    fun scanAndSavePackages(): Int {
        try {
            val pm = context.packageManager
            val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            var count = 0
            
            Log.d(TAG, "🔍 Scanning apps for monitoring...")
            
            allApps.forEach { appInfo ->
                if (shouldMonitor(appInfo.packageName)) {
                    count++
                    val name = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        appInfo.packageName
                    }
                    Log.d(TAG, "✅ Monitoring: $name (${appInfo.packageName})")
                }
            }
            
            prefs.edit().putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()
            Log.d(TAG, "✅ Total monitored apps: $count")
            return count
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning packages: ${e.message}")
            return 0
        }
    }

    fun getLastScanTime(): Long = prefs.getLong(KEY_LAST_SCAN, 0)
    
    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
