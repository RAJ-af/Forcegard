package com.itsraj.forcegard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageName = intent.data?.schemeSpecificPart
                Log.d("AppInstallReceiver", "📦 New app installed: $packageName")
                
                // Trigger re-scan or categorization
                packageName?.let {
                    categorizeNewApp(context, it)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                Log.d("AppInstallReceiver", "🗑️ App uninstalled: $packageName")
            }
        }
    }

    private fun categorizeNewApp(context: Context?, packageName: String) {
        context ?: return
        
        val category = when {
            packageName.contains("whatsapp") || packageName.contains("instagram") -> "Social"
            packageName.contains("chrome") -> "Browser"
            packageName.contains("youtube") || packageName.contains("netflix") -> "Entertainment"
            else -> "Other"
        }
        
        Log.d("AppInstallReceiver", "🏷️ $packageName categorized as: $category")
    }
}