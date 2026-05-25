package com.itsraj.forcegard.limits

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AllowedAppsManager {
    
    // Essential apps that work even when limit is exceeded
    private val ALWAYS_ALLOWED = setOf(
        "com.android.dialer",           // Phone
        "com.google.android.dialer",    // Google Phone
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",        // Emergency info
        "com.android.settings",         // Settings
        "com.android.systemui",          // System UI
        "com.android.packageinstaller", // Package installer
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )
    
    fun isAllowedWhenLimited(packageName: String): Boolean {
        // Check exact match
        if (ALWAYS_ALLOWED.contains(packageName)) return true
        
        // Allow Forcegard itself
        if (packageName == "com.itsraj.forcegard") return true
        
        return false
    }

    fun isLauncherApp(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfos.any { it.activityInfo.packageName == packageName }
    }
}
