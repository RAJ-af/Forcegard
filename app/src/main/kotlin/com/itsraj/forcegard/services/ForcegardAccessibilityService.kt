package com.itsraj.forcegard.services

import android.accessibilityservice.AccessibilityService
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
import android.widget.TextView
import com.itsraj.forcegard.R
import com.itsraj.forcegard.limits.AllowedAppsManager
import com.itsraj.forcegard.limits.DailyLimitManager
import com.itsraj.forcegard.limits.SpendLimitManager
import com.itsraj.forcegard.utils.UsageTimeHelper
import com.itsraj.forcegard.utils.AppCategory
import com.itsraj.forcegard.managers.*
import com.itsraj.forcegard.models.CooldownReason
import com.itsraj.forcegard.models.TimerData
import java.text.SimpleDateFormat
import java.util.*

class ForcegardAccessibilityService : AccessibilityService(),
    ForegroundAppTracker.ForegroundChangeListener,
    TimerManager.TimerEventListener,
    CooldownManager.CooldownEventListener,
    OverlayManager.OverlayEventListener {

    private lateinit var foregroundTracker: ForegroundAppTracker
    private lateinit var overlayManager: OverlayManager
    private lateinit var appDetectionManager: AppDetectionManager
    private lateinit var timerManager: TimerManager
    private lateinit var cooldownManager: CooldownManager
    private lateinit var spendLimitManager: SpendLimitManager
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var pickupManager: PickupManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var dailyLimitOverlay: View? = null

    companion object {
        private const val TAG = "ForcegardAS"
        private val GUARDED_CATEGORIES = setOf(AppCategory.SOCIAL, AppCategory.GAME)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "🚀 Forcegard Accessibility Service Connected")
        
        foregroundTracker = ForegroundAppTracker(this)
        overlayManager = OverlayManager(this)
        appDetectionManager = AppDetectionManager(this)
        timerManager = TimerManager()
        cooldownManager = CooldownManager(this)
        spendLimitManager = SpendLimitManager(this)
        dailyLimitManager = DailyLimitManager(this)
        pickupManager = PickupManager(this)
        
        foregroundTracker.addListener(this)
        timerManager.addListener(this)
        cooldownManager.addListener(this)
        overlayManager.addListener(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                onForegroundAppChanged(packageName, "AccessibilityEvent")
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        timerManager.stopAll()
        overlayManager.cleanupAll()
    }

    override fun onForegroundAppChanged(packageName: String, source: String) {
        spendLimitManager.updateForegroundApp(packageName)

        if (!timerManager.hasActiveTimer(packageName)) {
            overlayManager.hideAllTimerPills()
        }
        
        if (spendLimitManager.isLimitReached()) {
            if (!AllowedAppsManager.isAllowedWhenLimited(packageName)) {
                showDailyLimitOverlay()
                return
            }
        } else {
            removeDailyLimitOverlay()
        }
        
        val appState = appDetectionManager.handleAppDetection(packageName)
        if (!appState.isMonitored) return
        
        if (cooldownManager.isInCooldown(packageName)) {
            val remainingMs = cooldownManager.getRemainingCooldownTime(packageName) ?: return
            overlayManager.showCooldownLockPopup(packageName, remainingMs)
            return
        }
        
        if (timerManager.hasActiveTimer(packageName)) {
            val remainingMs = timerManager.getRemainingTime(packageName) ?: return
            val remainingSeconds = (remainingMs / 1000).toInt()
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            overlayManager.showOrUpdateTimerPill(packageName, minutes, seconds)
            return
        }
        
        if (GUARDED_CATEGORIES.contains(appState.category)) {
            if (!overlayManager.isOverlayVisible()) {
                overlayManager.showConfirmationPopup(packageName)
            }
        }
    }

    private fun showDailyLimitOverlay() {
        if (dailyLimitOverlay != null) {
            updateDailyLimitOverlayUI()
            return
        }
        val inflater = LayoutInflater.from(this)
        dailyLimitOverlay = inflater.inflate(R.layout.overlay_daily_limit_reached, null)
        updateDailyLimitOverlayUI()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(dailyLimitOverlay, params)
    }

    private fun updateDailyLimitOverlayUI() {
        dailyLimitOverlay?.let { view ->
            val remainingMs = spendLimitManager.getNextResetDate().time - System.currentTimeMillis()
            view.findViewById<TextView>(R.id.tvTimeRemaining).text = "Resets in: ${formatDuration(remainingMs)}"
            val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
            view.findViewById<TextView>(R.id.tvResetDate).text = "Next reset: ${sdf.format(spendLimitManager.getNextResetDate())}"
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun removeDailyLimitOverlay() {
        dailyLimitOverlay?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try { windowManager.removeView(it) } catch (_: Exception) {}
            dailyLimitOverlay = null
        }
    }
    
    override fun onTimerStarted(timerData: TimerData) {
        if (timerData.packageName == foregroundTracker.getCurrentForegroundApp()) {
            overlayManager.showOrUpdateTimerPill(timerData.packageName, (timerData.totalDurationMs / 60000).toInt(), 0)
        }
    }

    override fun onTimerTick(packageName: String, remainingMinutes: Int, remainingSeconds: Int) {
        if (packageName == foregroundTracker.getCurrentForegroundApp()) {
            overlayManager.showOrUpdateTimerPill(packageName, remainingMinutes, remainingSeconds)
        }
    }

    override fun onTimerExpired(packageName: String) {
        val timerData = timerManager.getActiveTimer(packageName)
        val usageMs = timerData?.totalDurationMs ?: (5 * 60000L)
        val cooldownMs = (usageMs * 2).coerceIn(5 * 60000L, 60 * 60000L)
        cooldownManager.startCooldown(packageName, CooldownReason.TIMER_EXPIRED, cooldownMs)
        overlayManager.showTimeFinishedPopup(packageName)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 500)
    }

    override fun onTimerCancelled(packageName: String) {
        overlayManager.removeTimerPill(packageName)
    }

    override fun onCooldownStarted(cooldownData: com.itsraj.forcegard.models.CooldownData) {}
    override fun onCooldownExpired(packageName: String) {}
    override fun onCooldownActive(packageName: String, remainingMs: Long) {}

    override fun onConfirmationYes(packageName: String) {
        handler.postDelayed({
            overlayManager.showTimeSelectionPopup(packageName) { overlayManager.clearTransitioning() }
        }, 500)
    }

    override fun onConfirmationNo(packageName: String) {
        cooldownManager.startCooldown(packageName, CooldownReason.USER_REJECTED)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            overlayManager.clearTransitioning()
        }, 500)
    }

    override fun onTimeSelected(packageName: String, minutes: Int) {
        timerManager.startTimer(packageName, minutes)
        handler.postDelayed({ overlayManager.clearTransitioning() }, 500)
    }

    override fun onCloseAppRequested() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        overlayManager.clearTransitioning()
    }

    override fun onExitConfirmed(packageName: String) {
        timerManager.cancelTimer(packageName)
        overlayManager.removeTimerPill(packageName)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 300)
    }

    override fun onExitCancelled(packageName: String) {
        handler.postDelayed({
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
            }
        }, 300)
    }
}
