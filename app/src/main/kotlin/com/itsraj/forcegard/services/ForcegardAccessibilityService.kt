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
    private var dailyLimitOverlay: android.view.View? = null

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

            Log.i(TAG, "✅ Forcegard Accessibility Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize service components", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            Log.v(TAG, "Received accessibility event: ${AccessibilityEvent.eventTypeToString(event.eventType)}")

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                val packageName = event.packageName?.toString()
                if (packageName != null) {
                    Log.d(TAG, "Processing app event: $packageName")
                    onForegroundAppChanged(packageName, "AccessibilityEvent")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        timerManager.stopAll()
        overlayManager.cleanupAll()
    }

    override fun onForegroundAppChanged(packageName: String, source: String) {
        Log.v(TAG, "Foreground changed to: $packageName (Source: $source)")

        // Always update foreground app
        spendLimitManager.updateForegroundApp(packageName)

        // Hide timer pills if no active timer
        if (!timerManager.hasActiveTimer(packageName)) {
            overlayManager.hideAllTimerPills()
        }

        // CHECK 1: Global Spend Limit
        if (spendLimitManager.isLimitReached()) {
            if (!AllowedAppsManager.isAllowedWhenLimited(packageName)) {
                showDailyLimitOverlay()
                return
            }
        } else {
            removeDailyLimitOverlay()
        }

        // CHECK 2: Monitored App Logic
        val appState = appDetectionManager.handleAppDetection(packageName)
        // Always monitor all apps, don't filter out
        if (!appState.isMonitored) {
            Log.d(TAG, "App $packageName is not monitored, but we'll still process it")
        }

        // Record Pickup for all apps (not just monitored ones)
        pickupManager.recordPickup()

        // CHECK 3: Cooldown Lock (BLOCKING)
        if (cooldownManager.isInCooldown(packageName)) {
            val remainingMs = cooldownManager.getRemainingCooldownTime(packageName)
            if (remainingMs != null) {
                Log.i(TAG, "🔒 Blocking $packageName - In Cooldown (${remainingMs/1000}s left)")
                overlayManager.showCooldownLockPopup(packageName, remainingMs)
                return
            }
        }

        // CHECK 4: Active Timer (CONTINUING SESSION)
        if (timerManager.hasActiveTimer(packageName)) {
            val remainingMs = timerManager.getRemainingTime(packageName)
            if (remainingMs != null) {
                val remainingSeconds = (remainingMs / 1000).toInt()
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                overlayManager.showOrUpdateTimerPill(packageName, minutes, seconds)
                return
            }
        }

        // CHECK 5: Guard Trigger (STARTING SESSION)
        // For debugging, let's trigger for ALL apps, not just guarded categories
        if (packageName.isNotEmpty()) {
            if (!overlayManager.isOverlayVisible()) {
                Log.i(TAG, "🛡️ Guard Triggered for $packageName")
                overlayManager.showConfirmationPopup(packageName)
            }
        }
    }

    private fun showDailyLimitOverlay() {
        // Simplified overlay logic
    }

    private fun removeDailyLimitOverlay() {
        // Simplified overlay removal logic
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
        Log.d(TAG, "⏰ Timer expired: $packageName")

        val timerData = timerManager.getActiveTimer(packageName)
        val selectedTimeMs = timerData?.totalDurationMs ?: (5 * 60000L)

        // NEW RULE: Lock Time = Selected Time + 1 minute
        val cooldownMs = selectedTimeMs + (1 * 60000L)

        Log.i(TAG, "🎯 APPLYING NEW COOLDOWN RULE for $packageName: Selected ${selectedTimeMs/60000}m -> Lock ${cooldownMs/60000}m")

        overlayManager.removeTimerPill(packageName)
        cooldownManager.startCooldown(packageName, CooldownReason.TIMER_EXPIRED, cooldownMs)
        overlayManager.showTimeFinishedPopup(packageName)

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 500)
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