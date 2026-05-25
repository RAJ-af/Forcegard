package com.itsraj.forcegard.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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

    companion object {
        private const val TAG = "ForcegardAS"
        private val GUARDED_CATEGORIES = setOf(AppCategory.SOCIAL, AppCategory.GAME)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "🚀 Forcegard Accessibility Service Connected")

        try {
            // Initialize managers
            foregroundTracker = ForegroundAppTracker(this)
            overlayManager = OverlayManager(this)
            appDetectionManager = AppDetectionManager(this)
            timerManager = TimerManager()
            cooldownManager = CooldownManager(this)
            spendLimitManager = SpendLimitManager(this)
            dailyLimitManager = DailyLimitManager(this)
            pickupManager = PickupManager(this)

            // Add listeners
            foregroundTracker.addListener(this)
            timerManager.addListener(this)
            cooldownManager.addListener(this)
            overlayManager.addListener(this)

            // Initialize app detection
            appDetectionManager.initialize()

            Log.i(TAG, "✅ Forcegard Accessibility Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize service components", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // Only process events via tracker to avoid redundant calls and flooding
            foregroundTracker.onAccessibilityEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::timerManager.isInitialized) timerManager.stopAll()
        if (::overlayManager.isInitialized) overlayManager.cleanupAll()
    }

    override fun onForegroundAppChanged(packageName: String, source: String) {
        Log.d(TAG, "📱 Foreground change: $packageName (via $source)")

        // 1. Check if it's a launcher or core app - NEVER monitor or block these
        if (AllowedAppsManager.isLauncherApp(this, packageName) || AllowedAppsManager.isAllowedWhenLimited(packageName)) {
            Log.v(TAG, "Launcher or Core app detected: $packageName. Hiding overlays.")
            overlayManager.removeCurrentOverlay()
            spendLimitManager.updateForegroundApp(packageName) // Still update tracking (it will ignore it anyway)
            return
        }

        // 2. Update usage tracking
        spendLimitManager.updateForegroundApp(packageName)

        // 3. Hide irrelevant timer pills
        if (!timerManager.hasActiveTimer(packageName)) {
            overlayManager.hideAllTimerPills()
        }

        // 4. Security: Check if it's a monitored app
        if (!appDetectionManager.shouldMonitor(packageName)) {
            Log.v(TAG, "Skipping unmonitored app: $packageName")
            overlayManager.removeCurrentOverlay()
            return
        }

        // 5. Check Daily/Spend Limit reached
        if (spendLimitManager.isLimitReached()) {
            overlayManager.showDailyLimitReachedOverlay(packageName)
            return
        }

        // 6. Check Cooldown Lock
        if (cooldownManager.isInCooldown(packageName)) {
            val remainingMs = cooldownManager.getRemainingCooldownTime(packageName)
            if (remainingMs != null) {
                Log.i(TAG, "🔒 Blocking $packageName - In Cooldown (${remainingMs/1000}s left)")
                overlayManager.showCooldownLockPopup(packageName, remainingMs)
                return
            }
        }

        // 7. Active Timer (Session in progress)
        if (timerManager.hasActiveTimer(packageName)) {
            val remainingMs = timerManager.getRemainingTime(packageName)
            if (remainingMs != null) {
                val sec = (remainingMs / 1000).toInt()
                overlayManager.showOrUpdateTimerPill(packageName, sec / 60, sec % 60)
                return
            }
        }

        // 8. Mindfulness Guard Trigger (New Session)
        if (!overlayManager.isOverlayVisible()) {
            val category = appDetectionManager.getAppCategory(packageName)
            if (GUARDED_CATEGORIES.contains(category)) {
                 Log.i(TAG, "🛡️ Guard Triggered for $packageName")
                 overlayManager.showMindfulnessOverlay(packageName)
            }
        }
    }

    override fun onTimerStarted(timerData: TimerData) {
        if (timerData.packageName == foregroundTracker.getCurrentForegroundApp()) {
            val totalSec = (timerData.totalDurationMs / 1000).toInt()
            overlayManager.showOrUpdateTimerPill(timerData.packageName, totalSec / 60, totalSec % 60)
        }
    }

    override fun onTimerTick(packageName: String, remainingMinutes: Int, remainingSeconds: Int) {
        if (packageName == foregroundTracker.getCurrentForegroundApp()) {
            overlayManager.showOrUpdateTimerPill(packageName, remainingMinutes, remainingSeconds)
        }
    }

    override fun onTimerExpired(packageName: String) {
        Log.i(TAG, "⏰ Timer expired: $packageName")

        val timerData = timerManager.getActiveTimer(packageName)
        val selectedTimeMs = timerData?.totalDurationMs ?: (5 * 60000L)
        val cooldownMs = selectedTimeMs + (1 * 60000L)

        overlayManager.removeTimerPill(packageName)
        cooldownManager.startCooldown(packageName, CooldownReason.TIMER_EXPIRED, cooldownMs)
        overlayManager.showTimeFinishedPopup(packageName)

        handler.postDelayed({
            if (foregroundTracker.getCurrentForegroundApp() == packageName) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }, 2000)
    }

    override fun onTimerCancelled(packageName: String) {
        overlayManager.removeTimerPill(packageName)
    }

    override fun onCooldownStarted(cooldownData: com.itsraj.forcegard.models.CooldownData) {}
    override fun onCooldownExpired(packageName: String) {}
    override fun onCooldownActive(packageName: String, remainingMs: Long) {}

    override fun onConfirmationYes(packageName: String) {
        overlayManager.showTimeSelectionPopup(packageName)
    }

    override fun onConfirmationNo(packageName: String) {
        cooldownManager.startCooldown(packageName, CooldownReason.USER_REJECTED)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onTimeSelected(packageName: String, minutes: Int) {
        timerManager.startTimer(packageName, minutes)
    }

    override fun onCloseAppRequested() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onExitConfirmed(packageName: String) {
        timerManager.cancelTimer(packageName)
        overlayManager.removeTimerPill(packageName)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onExitCancelled(packageName: String) {
        // Continue session
    }
}
