package com.itsraj.forcegard.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.itsraj.forcegard.utils.AppPackages

class AppInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageName = intent.data?.schemeSpecificPart
                Log.d("AppInstallReceiver", "📦 New app installed: $packageName")
                
                // Re-scan packages to update monitored list
                AppPackages(context).scanAndSavePackages()
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                Log.d("AppInstallReceiver", "🗑️ App uninstalled: $packageName")

                // Re-scan packages to update monitored list
                AppPackages(context).scanAndSavePackages()
            }
        }
    }
}
