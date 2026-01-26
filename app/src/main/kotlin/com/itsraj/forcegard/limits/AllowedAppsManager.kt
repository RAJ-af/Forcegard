package com.itsraj.forcegard.limits

object AllowedAppsManager {
    
    // Essential apps that work even when limit is exceeded
    private val ALWAYS_ALLOWED = setOf(
        "com.android.dialer",           // Phone
        "com.google.android.dialer",    // Google Phone
        "com.android.phone",
        "com.android.contacts",
        "com.android.mms",              // Messages
        "com.google.android.apps.messaging", // Google Messages
        "com.whatsapp",                 // WhatsApp
        "com.google.android.apps.maps", // Maps
        "com.android.settings",         // Settings
        "com.android.systemui"          // System UI
    )
    
    fun isAllowedWhenLimited(packageName: String): Boolean {
        // Check exact match
        if (ALWAYS_ALLOWED.contains(packageName)) return true
        
        // Allow Forcegard itself
        if (packageName.startsWith("com.itsraj.forcegard")) return true
        
        // Allow system launcher
        if (packageName.contains("launcher")) return true
        
        return false
    }
}
