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
    
    private lateinit var cardStreak: CardView
    private lateinit var tvStreakDays: TextView
    private lateinit var tvStreakText: TextView
    private lateinit var tvStreakHelper: TextView
    
    private lateinit var containerMostUsedApps: LinearLayout
    private lateinit var tvNoAppsUsed: TextView
    
    private lateinit var dailyLimitManager: DailyLimitManager
    
    companion object {
        private const val TAG = "DashboardActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        dailyLimitManager = DailyLimitManager(this)
        
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
        
        cardStreak = findViewById(R.id.cardStreak)
        tvStreakDays = findViewById(R.id.tvStreakDays)
        tvStreakText = findViewById(R.id.tvStreakText)
        tvStreakHelper = findViewById(R.id.tvStreakHelper)
        
        containerMostUsedApps = findViewById(R.id.containerMostUsedApps)
        tvNoAppsUsed = findViewById(R.id.tvNoAppsUsed)
        
        // Set date
        val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.ENGLISH)
        tvDate.text = sdf.format(Date())
        
        // Protection button
        btnStartProtection.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // Daily limit card - make entire card clickable
        tvScreenTime.setOnClickListener {
            openDailyLimitActivity()
        }
        
        tvLimitHelper.setOnClickListener {
            openDailyLimitActivity()
        }
        
        tvLimitReset.setOnClickListener {
            openDailyLimitActivity()
        }
    }

    private fun updateUI() {
        updateProtectionStatus()
        updateDailyLimitUI()
        updatePickupsAndAverage()
        updateStreakUI()
        updateMostUsedApps()
    }

    private fun updateProtectionStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        if (isEnabled) {
            tvServiceStatus.text = "Protection is ACTIVE"
            tvServiceHelper.text = "Your apps are being monitored"
            btnStartProtection.text = "MANAGE PROTECTION"
            cardProtection.setCardBackgroundColor(getColor(R.color.success_green))
        } else {
            tvServiceStatus.text = "Accessibility monitoring is OFF"
            tvServiceHelper.text = "Tap below to enable tracking and enforcement."
            btnStartProtection.text = "ENABLE PROTECTION"
            cardProtection.setCardBackgroundColor(getColor(R.color.card_background))
        }
    }

    private fun updateDailyLimitUI() {
        val config = dailyLimitManager.getConfig()
        
        if (config == null || !dailyLimitManager.isPlanActive()) {
            // No limit set
            tvScreenTime.text = "No limit"
            tvLimitHelper.text = "Set a daily limit to control usage"
            tvLimitReset.text = "Tap here to set limit"
            return
        }
        
        // Get today's actual usage using new helper
        val usedMillis = UsageTimeHelper.getTodayTotalUsageMillis(this, config.resetHour)
        val limitMillis = config.dailyLimitMinutes * 60000L
        
        val usedHours = (usedMillis / 3600000).toInt()
        val usedMinutes = ((usedMillis % 3600000) / 60000).toInt()
        
        val limitHours = config.dailyLimitMinutes / 60
        val limitMinutes = config.dailyLimitMinutes % 60
        
        if (usedMillis >= limitMillis) {
            // Limit reached
            tvScreenTime.text = "Limit reached"
            tvLimitHelper.text = "You've used all your daily time (${limitHours}h ${limitMinutes}m)"
        } else {
            // Still time left
            val remainingMillis = limitMillis - usedMillis
            val remainingHours = (remainingMillis / 3600000).toInt()
            val remainingMinutes = ((remainingMillis % 3600000) / 60000).toInt()
            
            tvScreenTime.text = "${remainingHours}h ${remainingMinutes}m left"
            tvLimitHelper.text = "Used: ${usedHours}h ${usedMinutes}m of ${limitHours}h ${limitMinutes}m"
        }
        
        val resetText = if (config.resetHour == 0) "12:00 AM" else "5:00 AM"
        tvLimitReset.text = "Limit resets at $resetText"
    }

    private fun updatePickupsAndAverage() {
        // Pickups placeholder
        tvPickups.text = "—"
        tvPickupsSubtext.text = "Enable protection to track"
        
        // Daily average placeholder
        tvDailyAvg.text = "11h 30m"
        tvDailyAvgSubtext.text = "Appears after few days"
    }

    private fun updateStreakUI() {
        val config = dailyLimitManager.getConfig()
        
        if (config == null || !dailyLimitManager.isPlanActive()) {
            tvStreakDays.text = "0"
            tvStreakText.text = "No active streak"
            tvStreakHelper.text = "Set a daily limit and enable protection to build streaks"
            return
        }
        
        // Get today's usage
        val usedMillis = UsageTimeHelper.getTodayTotalUsageMillis(this, config.resetHour)
        val limitMillis = config.dailyLimitMinutes * 60000L
        
        if (usedMillis >= limitMillis) {
            tvStreakDays.text = "0"
            tvStreakText.text = "Limit exceeded today"
            tvStreakHelper.text = "Stay within limit to maintain streak"
        } else {
            tvStreakDays.text = "0"
            tvStreakText.text = "No active streak"
            tvStreakHelper.text = "Set a daily limit and enable protection to build streaks"
        }
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
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_most_used_app, containerMostUsedApps, false)
            
            val tvAppName = itemView.findViewById<TextView>(R.id.tvAppName)
            val tvAppTime = itemView.findViewById<TextView>(R.id.tvAppTime)
            val ivAppIcon = itemView.findViewById<ImageView>(R.id.ivAppIcon)
            
            tvAppName.text = appInfo.appName
            
            // Format time
            val minutes = (appInfo.totalTimeMillis / 60000).toInt()
            val hours = minutes / 60
            val mins = minutes % 60
            
            val timeText = when {
                hours > 0 -> "${hours}h ${mins}m"
                mins > 0 -> "${mins}m"
                else -> "< 1m"
            }
            tvAppTime.text = timeText
            
            // Try to get app icon - FIXED: using system icon as fallback
            try {
                val icon = packageManager.getApplicationIcon(appInfo.packageName)
                ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                // Use system default icon instead of custom drawable
                ivAppIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            containerMostUsedApps.addView(itemView)
        }
    }

    private fun openDailyLimitActivity() {
        val intent = Intent(this, DailyLimitActivity::class.java)
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/com.itsraj.forcegard.services.ForcegardAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }
}
