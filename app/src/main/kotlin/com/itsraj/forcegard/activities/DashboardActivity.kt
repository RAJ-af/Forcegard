package com.itsraj.forcegard.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.itsraj.forcegard.R
import com.itsraj.forcegard.limits.DailyLimitManager
import com.itsraj.forcegard.limits.SpendLimitManager
import com.itsraj.forcegard.managers.PickupManager
import com.itsraj.forcegard.utils.UsageTimeHelper
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvDate: TextView
    private lateinit var tvScreenTime: TextView
    private lateinit var tvLimitHelper: TextView
    private lateinit var tvLimitReset: TextView
    
    private lateinit var tvPickups: TextView
    private lateinit var tvPickupsSubtext: TextView
    private lateinit var tvDailyAvg: TextView
    private lateinit var tvDailyAvgSubtext: TextView
    
    private lateinit var cardProtection: CardView
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvServiceHelper: TextView
    private lateinit var btnStartProtection: Button

    private lateinit var tvMonitoredApps: TextView
    private lateinit var tvLockPeriod: TextView
    
    private lateinit var containerMostUsedApps: LinearLayout
    private lateinit var tvNoAppsUsed: TextView
    
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var spendLimitManager: SpendLimitManager
    private lateinit var pickupManager: PickupManager
    
    companion object {
        private const val TAG = "DashboardActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        dailyLimitManager = DailyLimitManager(this)
        spendLimitManager = SpendLimitManager(this)
        pickupManager = PickupManager(this)
        
        initViews()
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        tvDate = findViewById(R.id.tvDate)
        tvScreenTime = findViewById(R.id.tvScreenTime)
        tvLimitHelper = findViewById(R.id.tvLimitHelper)
        tvLimitReset = findViewById(R.id.tvLimitReset)
        tvPickups = findViewById(R.id.tvPickups)
        tvPickupsSubtext = findViewById(R.id.tvPickupsSubtext)
        tvDailyAvg = findViewById(R.id.tvDailyAvg)
        tvDailyAvgSubtext = findViewById(R.id.tvDailyAvgSubtext)
        
        cardProtection = findViewById(R.id.cardProtection)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvServiceHelper = findViewById(R.id.tvServiceHelper)
        btnStartProtection = findViewById(R.id.btnStartProtection)

        tvMonitoredApps = findViewById(R.id.tvMonitoredApps)
        tvLockPeriod = findViewById(R.id.tvLockPeriod)
        
        containerMostUsedApps = findViewById(R.id.containerMostUsedApps)
        tvNoAppsUsed = findViewById(R.id.tvNoAppsUsed)
        
        val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.ENGLISH)
        tvDate.text = sdf.format(Date())
        
        btnStartProtection.setOnClickListener { openAccessibilitySettings() }
        tvScreenTime.setOnClickListener { openDailyLimitActivity() }
    }

    private fun updateUI() {
        updateProtectionStatus()
        updateUsageStats()
        updatePickupsAndAverage()
        updateMostUsedApps()
        updateSystemInfo()
    }

    private fun updateProtectionStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled) {
            tvServiceStatus.text = "Protection is ACTIVE"
            tvServiceStatus.setTextColor(getColor(R.color.success_green))
            tvServiceHelper.text = "Your apps are being monitored"
            btnStartProtection.visibility = View.GONE
        } else {
            tvServiceStatus.text = "Monitoring is OFF"
            tvServiceStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            tvServiceHelper.text = "Tap below to enable tracking."
            btnStartProtection.visibility = View.VISIBLE
            btnStartProtection.text = "ENABLE PROTECTION"
        }
        cardProtection.setCardBackgroundColor(getColor(R.color.card_background))
    }

    private fun updateUsageStats() {
        val config = dailyLimitManager.getConfig()
        val resetHour = config?.resetHour ?: 0
        
        // REAL USAGE FROM USAGESTATS
        val usedMillis = UsageTimeHelper.getTodayTotalUsageMillis(this, resetHour)
        
        val usedHours = (usedMillis / 3600000).toInt()
        val usedMinutes = ((usedMillis % 3600000) / 60000).toInt()
        
        tvScreenTime.text = if (usedHours > 0) "${usedHours}h ${usedMinutes}m" else "${usedMinutes}m"
        tvLimitHelper.text = "Total screen time today"

        if (config?.enabled == true) {
            val limitMillis = config.limitMinutes * 60000L
            tvLimitReset.text = "Limit: ${config.limitMinutes}m | Resets at 12 AM"
        } else {
            tvLimitReset.text = "No spend limit set"
        }
    }

    private fun updatePickupsAndAverage() {
        // REAL PICKUPS FROM PICKUPMANAGER
        val pickups = pickupManager.getTodayPickups()
        tvPickups.text = pickups.toString()
        tvPickupsSubtext.text = "App Pickups today"

        // REAL 7-DAY AVERAGE FROM USAGESTATS
        val avgUsage = UsageTimeHelper.getSevenDayAverageMillis(this)
        val avgMins = (avgUsage / 60000).toInt()
        val avgHours = avgMins / 60
        val remainingMins = avgMins % 60
        
        tvDailyAvg.text = if (avgHours > 0) "${avgHours}h ${remainingMins}m" else "${remainingMins}m"
        tvDailyAvgSubtext.text = "7-day daily average"
    }

    private fun updateMostUsedApps() {
        val config = dailyLimitManager.getConfig()
        val resetHour = config?.resetHour ?: 0
        val mostUsedApps = UsageTimeHelper.getMostUsedAppsToday(this, resetHour, 5)
        
        if (mostUsedApps.isEmpty()) {
            tvNoAppsUsed.visibility = View.VISIBLE
            containerMostUsedApps.removeAllViews()
            return
        }
        
        tvNoAppsUsed.visibility = View.GONE
        containerMostUsedApps.removeAllViews()
        
        mostUsedApps.forEach { appInfo ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_most_used_app, containerMostUsedApps, false)
            itemView.findViewById<TextView>(R.id.tvAppName).text = appInfo.appName
            val minsTotal = (appInfo.totalTimeMillis / 60000).toInt()
            itemView.findViewById<TextView>(R.id.tvAppTime).text = if (minsTotal >= 60) "${minsTotal/60}h ${minsTotal%60}m" else "${minsTotal}m"
            try {
                itemView.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(packageManager.getApplicationIcon(appInfo.packageName))
            } catch (e: Exception) {
                itemView.findViewById<ImageView>(R.id.ivAppIcon).setImageResource(android.R.drawable.ic_menu_gallery)
            }
            containerMostUsedApps.addView(itemView)
        }
    }

    private fun updateSystemInfo() {
        tvMonitoredApps.text = "Social & Games"
        val config = dailyLimitManager.getConfig()
        tvLockPeriod.text = "Lock = Usage + 1m"
    }

    private fun openDailyLimitActivity() {
        startActivity(Intent(this, DailyLimitActivity::class.java))
    }

    private fun openAccessibilitySettings() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/com.itsraj.forcegard.services.ForcegardAccessibilityService"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(serviceName) == true
    }
}
