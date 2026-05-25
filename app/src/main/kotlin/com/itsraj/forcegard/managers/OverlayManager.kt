package com.itsraj.forcegard.managers

import android.content.Context
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.itsraj.forcegard.R
import java.util.*

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentOverlay: View? = null
    private var currentTimer: CountDownTimer? = null
    
    private val timerPills = mutableMapOf<String, View>()
    private val listeners = mutableListOf<OverlayEventListener>()

    companion object {
        private const val TAG = "OverlayManager"
    }

    interface OverlayEventListener {
        fun onConfirmationYes(packageName: String)
        fun onConfirmationNo(packageName: String)
        fun onTimeSelected(packageName: String, minutes: Int)
        fun onCloseAppRequested()
        fun onExitConfirmed(packageName: String)
        fun onExitCancelled(packageName: String)
    }

    fun isOverlayVisible(): Boolean = currentOverlay != null

    fun showMindfulnessOverlay(packageName: String) {
        removeCurrentOverlay()
        
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_mindfulness_main, null)
            val params = createFullScreenParams()
            
            view.findViewById<Button>(R.id.btn_yes_need)?.setOnClickListener {
                notifyConfirmationYes(packageName)
            }
            
            view.findViewById<Button>(R.id.btn_no_need)?.setOnClickListener {
                removeCurrentOverlay()
                notifyConfirmationNo(packageName)
            }

            view.findViewById<Button>(R.id.btn_exploring)?.setOnClickListener {
                removeCurrentOverlay()
                notifyConfirmationNo(packageName)
            }
            
            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {
            Log.e(TAG, "Error showing mindfulness overlay: ${e.message}")
        }
    }

    fun showTimeSelectionPopup(packageName: String) {
        removeCurrentOverlay()
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_time_selection, null)
            val params = createFullScreenParams()
            
            view.findViewById<Button>(R.id.btn_5min)?.setOnClickListener {
                removeCurrentOverlay()
                notifyTimeSelected(packageName, 5)
            }
            view.findViewById<Button>(R.id.btn_10min)?.setOnClickListener {
                removeCurrentOverlay()
                notifyTimeSelected(packageName, 10)
            }
            view.findViewById<Button>(R.id.btn_20min)?.setOnClickListener {
                removeCurrentOverlay()
                notifyTimeSelected(packageName, 20)
            }
            view.findViewById<Button>(R.id.btn_custom)?.setOnClickListener {
                removeCurrentOverlay()
                notifyTimeSelected(packageName, 1)
            }

            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {
            Log.e(TAG, "Error showing time selection: ${e.message}")
        }
    }

    fun showOrUpdateTimerPill(packageName: String, minutes: Int, seconds: Int) {
        val timeText = String.format("%02d:%02d", minutes, seconds)
        val existingPill = timerPills[packageName]
        
        if (existingPill != null) {
            existingPill.findViewById<TextView>(R.id.tvTimerDisplay)?.text = timeText
            return
        }
        
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_active_timer, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 100
            }
            
            view.findViewById<TextView>(R.id.tvTimerDisplay)?.text = timeText

            // Allow clicking to exit session
            view.setOnClickListener {
                showExitConfirmation(packageName)
            }

            windowManager.addView(view, params)
            timerPills[packageName] = view
        } catch (e: Exception) {}
    }

    fun showExitConfirmation(packageName: String) {
        removeCurrentOverlay()
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_exit_confirmation, null)
            val params = createFullScreenParams()

            view.findViewById<TextView>(R.id.tvExitAppName)?.text = "End session for ${packageName}?"

            view.findViewById<View>(R.id.btnYesCloseApp)?.setOnClickListener {
                removeCurrentOverlay()
                notifyExitConfirmed(packageName)
            }

            view.findViewById<View>(R.id.btnNoKeepRunning)?.setOnClickListener {
                removeCurrentOverlay()
                notifyExitCancelled(packageName)
            }

            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {}
    }

    fun showDailyLimitReachedOverlay(packageName: String) {
        removeCurrentOverlay()
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_daily_limit_reached, null)
            val params = createFullScreenParams()

            // Re-using btnAction or similar if it exists for closing
            val btnClose = view.findViewById<Button>(R.id.btnAction)
            if (btnClose != null) {
                btnClose.visibility = View.VISIBLE
                btnClose.text = "CLOSE APP"
                btnClose.setOnClickListener {
                    removeCurrentOverlay()
                    notifyCloseAppRequested()
                }
            } else {
                // Fallback: Click anywhere to close if button not found or use a default one
                view.setOnClickListener {
                    removeCurrentOverlay()
                    notifyCloseAppRequested()
                }
            }

            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {}
    }

    fun removeTimerPill(packageName: String) {
        timerPills.remove(packageName)?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
    }

    fun hideAllTimerPills() {
        timerPills.forEach { (_, view) ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        timerPills.clear()
    }

    fun showTimeFinishedPopup(packageName: String) {
        removeCurrentOverlay()
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_cooldown_lock, null)
            val params = createFullScreenParams()
            
            view.findViewById<TextView>(R.id.tvCooldownAppName)?.text = "Time is finished!"
            view.findViewById<Button>(R.id.btnReuseAfter)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvAutoCloseCountdown)?.text = "Redirecting home..."
            
            view.findViewById<TextView>(R.id.btnCloseApp)?.setOnClickListener {
                removeCurrentOverlay()
                notifyCloseAppRequested()
            }
            
            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {}
    }

    fun showCooldownLockPopup(packageName: String, cooldownMs: Long) {
        removeCurrentOverlay()
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_cooldown_lock, null)
            val params = createFullScreenParams()
            
            val tvTimer = view.findViewById<TextView>(R.id.tvCooldownTimer)
            val btnReuseAfter = view.findViewById<Button>(R.id.btnReuseAfter)
            
            currentTimer = object : CountDownTimer(cooldownMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val sec = (millisUntilFinished / 1000).toInt()
                    val m = sec / 60
                    val s = sec % 60
                    val timeStr = String.format("%02d:%02d", m, s)
                    tvTimer?.text = timeStr
                    btnReuseAfter?.text = "LOCKED: $timeStr"
                }
                override fun onFinish() {
                    removeCurrentOverlay()
                }
            }.start()
            
            view.findViewById<TextView>(R.id.btnCloseApp)?.setOnClickListener {
                removeCurrentOverlay()
                notifyCloseAppRequested()
            }
            
            windowManager.addView(view, params)
            currentOverlay = view
        } catch (e: Exception) {}
    }

    private fun createFullScreenParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    fun removeCurrentOverlay() {
        currentOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        currentOverlay = null
        currentTimer?.cancel()
        currentTimer = null
    }

    fun cleanupAll() {
        removeCurrentOverlay()
        hideAllTimerPills()
    }

    fun addListener(listener: OverlayEventListener) { listeners.add(listener) }
    fun removeListener(listener: OverlayEventListener) { listeners.remove(listener) }

    private fun notifyConfirmationYes(packageName: String) { listeners.forEach { it.onConfirmationYes(packageName) } }
    private fun notifyConfirmationNo(packageName: String) { listeners.forEach { it.onConfirmationNo(packageName) } }
    private fun notifyTimeSelected(packageName: String, minutes: Int) { listeners.forEach { it.onTimeSelected(packageName, minutes) } }
    private fun notifyCloseAppRequested() { listeners.forEach { it.onCloseAppRequested() } }
    private fun notifyExitConfirmed(packageName: String) { listeners.forEach { it.onExitConfirmed(packageName) } }
    private fun notifyExitCancelled(packageName: String) { listeners.forEach { it.onExitCancelled(packageName) } }
}
