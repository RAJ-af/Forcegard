package com.itsraj.forcegard.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

class AppPackages(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val TAG = "AppPackages"

    companion object {
        private const val PREFS_NAME = "AppPackagesPrefs"
        private const val KEY_LAST_SCAN = "last_scan_time"
    }

    /**
     * NEW: Monitor ALL user apps with launcher icons
     * No hardcoded list!
     */
    fun shouldMonitor(packageName: String): Boolean {
        // Skip own app
        if (packageName == context.packageName) {
            return false
        }
        
        // Skip core system apps
        if (isCoreSystemApp(packageName)) {
            return false
        }

        // Skip system apps that are NOT updated (except if we want to explicitly allow some)
        if (isSystemApp(packageName)) {
            return false
        }
        
        // Skip launcher apps
        if (isLauncherApp(packageName)) {
            return false
        }
        
        // Check if app has launcher icon (user-visible apps only)
        // Some updated system apps like Chrome HAVE launcher icons
        if (!hasLauncherIcon(packageName)) {
            return false
        }
        
        // ✅ Monitor ALL remaining user apps
        return true
    }

    private fun isCoreSystemApp(packageName: String): Boolean {
        val corePackages = listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.vending",
            "com.google.android.gsf",
            "com.google.android.gms"
        )

        if (corePackages.any { packageName.contains(it) }) return true

        return false
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdated = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            
            // Pure system apps (not updated system apps like Chrome)
            isSystem && !isUpdated
            
        } catch (e: Exception) {
            false
        }
    }

    private fun isLauncherApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val launchers = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return launchers.any { it.activityInfo.packageName == packageName }
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
            
            Log.d(TAG, "🔍 Scanning ALL user apps...")
            
            allApps.forEach { appInfo ->
                if (shouldMonitor(appInfo.packageName)) {
                    count++
                    val name = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        appInfo.packageName
                    }
                    Log.d(TAG, "✅ Will monitor: $name (${appInfo.packageName})")
                }
            }
            
            prefs.edit().putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()
            Log.d(TAG, "✅ Total monitored apps: $count")
            return count
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}")
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
