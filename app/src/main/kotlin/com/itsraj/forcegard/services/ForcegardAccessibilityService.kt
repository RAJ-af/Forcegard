// services/ForcegardAccessibilityService.kt

package com.itsraj.forcegard.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.itsraj.forcegard.R
import com.itsraj.forcegard.limits.AllowedAppsManager
import com.itsraj.forcegard.limits.DailyLimitManager
import com.itsraj.forcegard.utils.UsageTimeHelper
import com.itsraj.forcegard.managers.*
import com.itsraj.forcegard.models.CooldownReason
import com.itsraj.forcegard.models.TimerData
import com.itsraj.forcegard.utils.AppCategory

class ForcegardAccessibilityService : AccessibilityService(),
    AppDetectionManager.AppStateListener,
    TimerManager.TimerEventListener,
    CooldownManager.CooldownEventListener,
    OverlayManager.OverlayEventListener,
    ForegroundAppTracker.ForegroundChangeListener {

    // Managers
    private lateinit var appDetectionManager: AppDetectionManager
    private lateinit var timerManager: TimerManager
    private lateinit var cooldownManager: CooldownManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var foregroundTracker: ForegroundAppTracker
    private lateinit var dailyLimitManager: DailyLimitManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var dailyLimitOverlay: View? = null

    companion object {
        private const val TAG = "ForcegardService"

        // Default guarded categories
        private val GUARDED_CATEGORIES = setOf(
            AppCategory.SOCIAL,
            AppCategory.GAME
        )
    }

    // ========== SERVICE LIFECYCLE ==========
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Service Connected")
        
        // Configure accessibility service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
            packageNames = null
        }
        setServiceInfo(info)
        
        // Initialize managers
        initializeManagers()
        
        // Start tracking
        foregroundTracker.startTracking()
        Log.d(TAG, "🎯 Service ready")
    }

    private fun initializeManagers() {
        // Initialize all managers
        appDetectionManager = AppDetectionManager(this)
        timerManager = TimerManager()
        cooldownManager = CooldownManager(this)
        overlayManager = OverlayManager(this)
        foregroundTracker = ForegroundAppTracker(this)
        dailyLimitManager = DailyLimitManager(this)
        
        // Register listeners
        appDetectionManager.addListener(this)
        timerManager.addListener(this)
        cooldownManager.addListener(this)
        overlayManager.addListener(this)
        foregroundTracker.addListener(this)
        
        // Initialize app list
        appDetectionManager.initialize()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (overlayManager.isTransitioning()) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Ignore system UI
        if (packageName.startsWith("com.android.systemui")) return
        if (packageName == this.packageName) return
        
        handleAppChange(packageName, "Event")
    }

    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroyed")
        
        // Cleanup
        foregroundTracker.stopTracking()
        timerManager.stopAll()
        overlayManager.cleanupAll()
        removeDailyLimitOverlay()
        
        // Unregister listeners
        appDetectionManager.removeListener(this)
        timerManager.removeListener(this)
        cooldownManager.removeListener(this)
        overlayManager.removeListener(this)
        foregroundTracker.removeListener(this)
    }

    // ========== FOREGROUND APP TRACKING ==========
    
    override fun onForegroundChanged(from: String?, to: String) {
        Log.d(TAG, "🔄 Foreground: $from → $to")
        
        // Detect app going to background with active timer
        // Show exit confirmation if switching to Launcher or another user app
        // Do NOT show if switching to a SYSTEM app (allow quick settings/phone calls)
        if (from != null &&
            appDetectionManager.shouldMonitor(from) &&
            timerManager.hasActiveTimer(from) &&
            to != from) {
            
            val toCategory = appDetectionManager.getAppCategory(to)
            if (toCategory != AppCategory.SYSTEM) {
                // Show exit confirmation
                val remainingMs = timerManager.getRemainingTime(from) ?: return
                val remainingSeconds = (remainingMs / 1000).toInt()
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                overlayManager.showExitConfirmationPopup(from, minutes, seconds)
            }
        }
    }

    override fun onForegroundAppChanged(packageName: String, source: String) {
        handleAppChange(packageName, source)
    }

    private fun handleAppChange(packageName: String, source: String) {
        // Skip if overlay is transitioning
        if (overlayManager.isTransitioning()) return

        // Hide timer pill if the current app is not the one with the timer
        if (!timerManager.hasActiveTimer(packageName)) {
            overlayManager.hideAllTimerPills()
        }
        
        // ===== DAILY LIMIT CHECK (PRIORITY 1) =====
        if (isDailyLimitExceeded()) {
            if (!AllowedAppsManager.isAllowedWhenLimited(packageName)) {
                Log.d(TAG, "⏰ Daily limit exceeded, blocking: $packageName")
                showDailyLimitOverlay()
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 1000)
                return
            }
        } else {
            removeDailyLimitOverlay()
        }
        // ==========================================
        
        // Get app state
        val appState = appDetectionManager.handleAppDetection(packageName)
        
        // Skip if not monitored
        if (!appState.isMonitored) return
        
        // Check cooldown
        if (cooldownManager.isInCooldown(packageName)) {
            val remainingMs = cooldownManager.getRemainingCooldownTime(packageName) ?: return
            overlayManager.showCooldownLockPopup(packageName, remainingMs)
            return
        }
        
        // Check active timer
        if (timerManager.hasActiveTimer(packageName)) {
            val remainingMs = timerManager.getRemainingTime(packageName) ?: return
            val remainingSeconds = (remainingMs / 1000).toInt()
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            overlayManager.showOrUpdateTimerPill(packageName, minutes, seconds)
            return
        }
        
        // Show confirmation ONLY if category is guarded and no overlay visible
        if (!overlayManager.isOverlayVisible() && isCategoryGuarded(appState.category)) {
            Log.d(TAG, "✅ TRIGGER [$source]: $packageName (Category: ${appState.category})")
            overlayManager.showConfirmationPopup(packageName)
        }
    }

    private fun isCategoryGuarded(category: AppCategory): Boolean {
        return GUARDED_CATEGORIES.contains(category)
    }

    // ========== DAILY LIMIT HELPERS ==========
    
    private fun isDailyLimitExceeded(): Boolean {
        val config = dailyLimitManager.getConfig() ?: return false
        if (!config.enabled) return false
        
        val (startWindow, endWindow) = dailyLimitManager.getTodayWindow()
        val usedMillis = getTotalUsageInWindow(startWindow, endWindow)
        val limitMillis = config.limitMinutes * 60000L
        
        return usedMillis >= limitMillis
    }

    private fun getTotalUsageInWindow(startMillis: Long, endMillis: Long): Long {
        return UsageTimeHelper.getTotalScreenTime(this, startMillis, endMillis)
    }

    private fun showDailyLimitOverlay() {
        if (dailyLimitOverlay != null) return // Already showing
        
        val config = dailyLimitManager.getConfig() ?: return
        
        val inflater = LayoutInflater.from(this)
        dailyLimitOverlay = inflater.inflate(R.layout.overlay_daily_limit_reached, null)
        
        val tvResetTime = dailyLimitOverlay?.findViewById<TextView>(R.id.tvResetTime)
        val btnGoHome = dailyLimitOverlay?.findViewById<Button>(R.id.btnGoHome)
        
        val resetText = if (config.resetHour == 0) "12:00 AM" else "5:00 AM"
        tvResetTime?.text = "Come back at $resetText"
        
        btnGoHome?.setOnClickListener {
            removeDailyLimitOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(dailyLimitOverlay, params)
    }

    private fun removeDailyLimitOverlay() {
        dailyLimitOverlay?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing limit overlay: ${e.message}")
            }
            dailyLimitOverlay = null
        }
    }

    // ========== APP STATE LISTENER ==========
    
    override fun onAppOpened(packageName: String) {
        Log.d(TAG, "📱 App opened: $packageName")
    }

    override fun onAppClosed(packageName: String) {
        Log.d(TAG, "📱 App closed: $packageName")
        // Do NOT cancel timer when app goes to background, just hide the pill
        overlayManager.removeTimerPill(packageName)
    }

    // ========== TIMER LISTENER ==========
    
    override fun onTimerStarted(timerData: TimerData) {
        Log.d(TAG, "⏱️ Timer started: ${timerData.packageName}")
        // Show timer pill if app is in foreground
        if (timerData.packageName == foregroundTracker.getCurrentForegroundApp()) {
            val minutes = (timerData.totalDurationMs / 60000).toInt()
            overlayManager.showOrUpdateTimerPill(timerData.packageName, minutes, 0)
        }
    }

    override fun onTimerTick(packageName: String, remainingMinutes: Int, remainingSeconds: Int) {
        // Update timer pill
        if (packageName == foregroundTracker.getCurrentForegroundApp()) {
            overlayManager.showOrUpdateTimerPill(packageName, remainingMinutes, remainingSeconds)
        }
    }

    override fun onTimerExpired(packageName: String) {
        Log.d(TAG, "⏰ Timer expired: $packageName")
        overlayManager.removeTimerPill(packageName)
        
        // Start cooldown
        cooldownManager.startCooldown(packageName, CooldownReason.TIMER_EXPIRED)
        
        // Show time finished popup
        overlayManager.showTimeFinishedPopup(packageName)
        
        // Close app after delay
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 500)
    }

    override fun onTimerCancelled(packageName: String) {
        Log.d(TAG, "🛑 Timer cancelled: $packageName")
        overlayManager.removeTimerPill(packageName)
    }

    // ========== COOLDOWN LISTENER ==========
    
    override fun onCooldownStarted(cooldownData: com.itsraj.forcegard.models.CooldownData) {
        Log.d(TAG, "🔒 Cooldown started: ${cooldownData.packageName}")
    }

    override fun onCooldownExpired(packageName: String) {
        Log.d(TAG, "🔓 Cooldown expired: $packageName")
    }

    override fun onCooldownActive(packageName: String, remainingMs: Long) {
        // Cooldown is still active
    }

    // ========== OVERLAY LISTENER ==========
    
    override fun onConfirmationYes(packageName: String) {
        Log.d(TAG, "✅ User confirmed need: $packageName")
        handler.postDelayed({
            overlayManager.showTimeSelectionPopup(packageName) {
                overlayManager.clearTransitioning()
            }
        }, 500)
    }

    override fun onConfirmationNo(packageName: String) {
        Log.d(TAG, "❌ User rejected: $packageName")
        // Start cooldown
        cooldownManager.startCooldown(packageName, CooldownReason.USER_REJECTED)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            overlayManager.clearTransitioning()
        }, 500)
    }

    override fun onTimeSelected(packageName: String, minutes: Int) {
        Log.d(TAG, "⏱️ User selected: $minutes minutes")
        // Start the actual timer immediately
        timerManager.startTimer(packageName, minutes)
        handler.postDelayed({
            overlayManager.clearTransitioning()
        }, 500)
    }

    override fun onCloseAppRequested() {
        Log.d(TAG, "🏠 Closing app")
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlayManager.clearTransitioning()
    }

    // ========== EXIT CONFIRMATION HANDLERS ==========
    
    override fun onExitConfirmed(packageName: String) {
        Log.d(TAG, "✅ Exit confirmed, cleaning up: $packageName")
        // Cancel timer
        timerManager.cancelTimer(packageName)
        // Remove timer pill
        overlayManager.removeTimerPill(packageName)
        // Close app
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 300)
    }

    override fun onExitCancelled(packageName: String) {
        Log.d(TAG, "❌ Exit cancelled, resuming: $packageName")
        // Timer continues running
        // Reopen the app
        handler.postDelayed({
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        }, 300)
    }
}
