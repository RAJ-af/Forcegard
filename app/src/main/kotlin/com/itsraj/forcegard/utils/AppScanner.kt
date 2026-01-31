package com.itsraj.forcegard.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScannedApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val category: AppCategory,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val hasLauncherIcon: Boolean,
    val installTime: Long,
    val updateTime: Long,
    val apkPath: String,
    val apkSize: Long,
    val uid: Int,
    val targetSdk: Int,
    val minSdk: Int,
    val icon: Drawable?
)

enum class AppCategory {
    SOCIAL,
    GAME,
    VIDEO_MUSIC,
    PRODUCTIVITY,
    EDUCATION,
    OTHER,
    SYSTEM,
    LAUNCHER,
    DISABLED
}

class AppScanner(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheManager = CategoryCacheManager(context)
    private val packageManager: PackageManager = context.packageManager
    private val TAG = "AppScanner"

    companion object {
        private const val PREFS_NAME = "AppScannerPrefs"
        private const val KEY_LAST_FULL_SCAN = "last_full_scan_time"
        private const val KEY_TOTAL_APPS = "total_apps_count"
        private const val KEY_USER_APPS = "user_apps_count"
        private const val KEY_SYSTEM_APPS = "system_apps_count"
    }

    /**
     * Scan ALL apps on device - Comprehensive
     */
    suspend fun scanAllApps(): List<ScannedApp> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Starting FULL device scan...")
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
            } else {
                PackageManager.GET_META_DATA or PackageManager.GET_DISABLED_COMPONENTS
            }
            
            val installedPackages = packageManager.getInstalledPackages(flags)
            val scannedApps = mutableListOf<ScannedApp>()
            
            var userCount = 0
            var systemCount = 0
            
            installedPackages.forEach { packageInfo ->
                try {
                    val scannedApp = analyzePackage(packageInfo, true)
                    scannedApps.add(scannedApp)
                    
                    when (scannedApp.category) {
                        AppCategory.SYSTEM -> systemCount++
                        AppCategory.LAUNCHER, AppCategory.DISABLED -> {}
                        else -> userCount++
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error analyzing ${packageInfo.packageName}: ${e.message}")
                }
            }
            
            // Save scan results
            prefs.edit()
                .putLong(KEY_LAST_FULL_SCAN, System.currentTimeMillis())
                .putInt(KEY_TOTAL_APPS, scannedApps.size)
                .putInt(KEY_USER_APPS, userCount)
                .putInt(KEY_SYSTEM_APPS, systemCount)
                .apply()
            
            Log.d(TAG, "✅ Scan complete:")
            Log.d(TAG, "   Total: ${scannedApps.size}")
            Log.d(TAG, "   User: $userCount")
            Log.d(TAG, "   System: $systemCount")
            
            return@withContext scannedApps.sortedBy { it.appName }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Scan failed: ${e.message}")
            return@withContext emptyList()
        }
    }

    /**
     * Analyze single package deeply
     */
    private fun analyzePackage(packageInfo: PackageInfo, allowInternet: Boolean = false): ScannedApp {
        val appInfo = packageInfo.applicationInfo
        val packageName = packageInfo.packageName
        
        // Get app name
        val appName = try {
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
        
        // Get version info
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        
        // Determine category
        val category = categorizeApp(appInfo, packageName, allowInternet)
        
        // System app checks
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        
        // Check launcher icon
        val hasLauncher = hasLauncherIcon(packageName)
        
        // Get APK info
        val apkPath = appInfo.sourceDir
        val apkSize = try {
            File(apkPath).length()
        } catch (e: Exception) {
            0L
        }
        
        // Get SDK info
        val targetSdk = appInfo.targetSdkVersion
        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            0
        }
        
        // Get icon
        val icon = try {
            packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            null
        }
        
        return ScannedApp(
            packageName = packageName,
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            category = category,
            isSystem = isSystem,
            isUpdatedSystem = isUpdatedSystem,
            hasLauncherIcon = hasLauncher,
            installTime = packageInfo.firstInstallTime,
            updateTime = packageInfo.lastUpdateTime,
            apkPath = apkPath,
            apkSize = apkSize,
            uid = appInfo.uid,
            targetSdk = targetSdk,
            minSdk = minSdk,
            icon = icon
        )
    }

    /**
     * Smart categorization based on App Manager logic
     * Hybrid Model: Cache -> System -> Internet
     */
    fun categorizeApp(appInfo: ApplicationInfo, packageName: String, allowInternet: Boolean = false): AppCategory {
        // 1. Check Local Cache
        cacheManager.getCachedCategory(packageName)?.let {
            return it.category
        }

        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isEnabled = appInfo.enabled
        
        // Check if launcher
        if (isLauncherApp(packageName)) {
            cacheManager.saveCategory(packageName, AppCategory.LAUNCHER, CategorySource.SYSTEM)
            return AppCategory.LAUNCHER
        }
        
        // Check if disabled
        if (!isEnabled) {
            return AppCategory.DISABLED
        }

        // Handle core system apps first
        if (isSystem && !isUpdatedSystem) {
            cacheManager.saveCategory(packageName, AppCategory.SYSTEM, CategorySource.SYSTEM)
            return AppCategory.SYSTEM
        }
        
        // 2. Try Android System Category
        var resolvedCategory: AppCategory = AppCategory.OTHER
        var source = CategorySource.SYSTEM

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            resolvedCategory = when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> AppCategory.VIDEO_MUSIC
                ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_NEWS -> AppCategory.PRODUCTIVITY
                ApplicationInfo.CATEGORY_MAPS -> AppCategory.PRODUCTIVITY
                else -> AppCategory.OTHER
            }
        }

        // 3. Internet Fallback (if system category is undefined/unreliable and allowed)
        if (resolvedCategory == AppCategory.OTHER && allowInternet) {
            InternetCategoryLookup.fetchCategory(packageName)?.let {
                resolvedCategory = it
                source = CategorySource.INTERNET
            }
        }
        
        // 4. Save to Cache
        if (resolvedCategory != AppCategory.OTHER || allowInternet) {
            cacheManager.saveCategory(packageName, resolvedCategory, source)
        }

        return resolvedCategory
    }

    /**
     * Check if app is a launcher
     */
    private fun isLauncherApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        
        val launchers = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        
        return launchers.any { it.activityInfo.packageName == packageName }
    }

    /**
     * Check if app has launcher icon
     */
    private fun hasLauncherIcon(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return intent != null
    }

    /**
     * Get only USER APPS
     */
    suspend fun getUserApps(): List<ScannedApp> {
        return scanAllApps().filter {
            it.category != AppCategory.SYSTEM &&
            it.category != AppCategory.LAUNCHER &&
            it.category != AppCategory.DISABLED
        }
    }

    /**
     * Get only SYSTEM APPS
     */
    suspend fun getSystemApps(): List<ScannedApp> {
        return scanAllApps().filter {
            it.category == AppCategory.SYSTEM
        }
    }

    /**
     * Get apps with launcher icons (visible apps)
     */
    suspend fun getLaunchableApps(): List<ScannedApp> {
        return scanAllApps().filter { it.hasLauncherIcon }
    }

    /**
     * Search apps by name or package
     */
    suspend fun searchApps(query: String): List<ScannedApp> {
        return scanAllApps().filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    /**
     * Get scan statistics
     */
    fun getScanStats(): Map<String, Int> {
        return mapOf(
            "total" to prefs.getInt(KEY_TOTAL_APPS, 0),
            "user" to prefs.getInt(KEY_USER_APPS, 0),
            "system" to prefs.getInt(KEY_SYSTEM_APPS, 0)
        )
    }

    fun getLastScanTime(): Long {
        return prefs.getLong(KEY_LAST_FULL_SCAN, 0)
    }
}
