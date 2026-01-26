// managers/OverlayManager.kt
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

class OverlayManager(private val context: Context) {
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentOverlay: View? = null
    private var currentTimer: CountDownTimer? = null
    
    private val timerPills = mutableMapOf<String, View>()
    
    private var isTransitioning = false
    
    private val listeners = mutableListOf<OverlayEventListener>()
    
    companion object {
        private const val TAG = "OverlayManager"
    }
    
    interface OverlayEventListener {
        fun onConfirmationYes(packageName: String)
        fun onConfirmationNo(packageName: String)
        fun onTimeSelected(packageName: String, minutes: Int)
        fun onImpulseCooldownFinished(packageName: String, minutes: Int)
        fun onExitConfirmed(packageName: String)
        fun onExitCancelled(packageName: String)
        fun onCloseAppRequested()
    }
    
    enum class OverlayType {
        CONFIRMATION,
        TIME_SELECTION,
        IMPULSE_COOLDOWN,
        TIME_FINISHED,
        COOLDOWN_LOCK,
        TIMER_PILL
    }
    
    fun isOverlayVisible(): Boolean = currentOverlay != null
    
    fun isTransitioning(): Boolean = isTransitioning
    
    // ========== CONFIRMATION POPUP ==========
    
    fun showConfirmationPopup(packageName: String) {
        if (isOverlayVisible()) {
            Log.w(TAG, "⚠️ Overlay already visible, skipping")
            return
        }
        
        removeCurrentOverlay()
        
        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.overlay_mindfulness_main, null)
            
            val params = createFullScreenParams()
            
            val btnYes = view.findViewById<Button>(R.id.btn_yes_need)
            val btnNo = view.findViewById<Button>(R.id.btn_no_need)
            
            view.findViewById<Button>(R.id.btn_exploring)?.visibility = View.GONE
            
            btnYes.setOnClickListener {
                Log.d(TAG, "✅ YES clicked: $packageName")
                isTransitioning = true
                btnYes.isEnabled = false
                btnNo.isEnabled = false
                
                removeCurrentOverlay()
                notifyConfirmationYes(packageName)
            }
            
            btnNo.setOnClickListener {
                Log.d(TAG, "❌ NO clicked: $packageName")
                isTransitioning = true
                btnYes.isEnabled = false
                btnNo.isEnabled = false
                
                removeCurrentOverlay()
                notifyConfirmationNo(packageName)
            }
            
            windowManager.addView(view, params)
            currentOverlay = view
            
            Log.d(TAG, "🔔 Confirmation popup shown for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show confirmation: ${e.message}", e)
        }
    }
    
    // ========== TIME SELECTION POPUP ==========
    
    fun showTimeSelectionPopup(packageName: String, onComplete: () -> Unit = {}) {
        handler.postDelayed({
            if (isOverlayVisible()) {
                showTimeSelectionPopup(packageName, onComplete)
                return@postDelayed
            }
            
            removeCurrentOverlay()
            
            try {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.overlay_time_selection, null)
                
                val params = createFullScreenParams()
                
                val btn5 = view.findViewById<Button>(R.id.btn_5min)
                val btn10 = view.findViewById<Button>(R.id.btn_10min)
                val btn20 = view.findViewById<Button>(R.id.btn_20min)
                val btnCustom = view.findViewById<Button>(R.id.btn_custom)
                
                val clickListener = View.OnClickListener { btn ->
                    val minutes = when (btn.id) {
                        R.id.btn_5min -> 5
                        R.id.btn_10min -> 10
                        R.id.btn_20min -> 20
                        R.id.btn_custom -> 15
                        else -> 5
                    }
                    
                    Log.d(TAG, "⏱️ Time selected: $minutes min")
                    
                    btn5.isEnabled = false
                    btn10.isEnabled = false
                    btn20.isEnabled = false
                    btnCustom.isEnabled = false
                    
                    removeCurrentOverlay()
                    notifyTimeSelected(packageName, minutes)
                    onComplete()
                }
                
                btn5.setOnClickListener(clickListener)
                btn10.setOnClickListener(clickListener)
                btn20.setOnClickListener(clickListener)
                btnCustom.setOnClickListener(clickListener)
                
                windowManager.addView(view, params)
                currentOverlay = view
                
                isTransitioning = false
                Log.d(TAG, "⏱️ Time selection shown for: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to show time selection: ${e.message}", e)
                isTransitioning = false
            }
        }, 500)
    }
    
    // ========== IMPULSE COOLDOWN ==========
    
    fun showImpulseCooldown(packageName: String, selectedMinutes: Int, cooldownSeconds: Int = 25) {
        removeCurrentOverlay()
        
        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.overlay_cooldown, null)
            
            val params = createFullScreenParams()
            
            val tvNumber = view.findViewById<TextView>(R.id.tv_cooldown_number)
            
            currentTimer = object : CountDownTimer(cooldownSeconds * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    tvNumber.text = secondsLeft.toString()
                }
                
                override fun onFinish() {
                    removeCurrentOverlay()
                    notifyImpulseCooldownFinished(packageName, selectedMinutes)
                }
            }.start()
            
            windowManager.addView(view, params)
            currentOverlay = view
            
            Log.d(TAG, "⏳ Impulse cooldown shown: ${cooldownSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show impulse cooldown: ${e.message}", e)
        }
    }
    
    // ========== TIMER PILL ==========
    
    fun showOrUpdateTimerPill(packageName: String, minutes: Int, seconds: Int) {
        val timeText = String.format("%02d:%02d", minutes, seconds)
        
        // Update existing pill
        timerPills[packageName]?.let { pill ->
            pill.findViewById<TextView>(R.id.tvTimerDisplay)?.text = timeText
            return
        }
        
        // Create new pill
        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.overlay_active_timer, null)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 50
            
            view.findViewById<TextView>(R.id.tvTimerDisplay).text = timeText
            
            windowManager.addView(view, params)
            timerPills[packageName] = view
            
            Log.d(TAG, "⏱️ Timer pill created: $packageName ($timeText)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create timer pill: ${e.message}")
        }
    }
    
    fun removeTimerPill(packageName: String) {
        timerPills.remove(packageName)?.let { pill ->
            try {
                windowManager.removeView(pill)
                Log.d(TAG, "🗑️ Timer pill removed: $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove timer pill: ${e.message}")
            }
        }
    }
    
    // ========== EXIT CONFIRMATION POPUP ==========

fun showExitConfirmationPopup(
    packageName: String, 
    remainingMinutes: Int, 
    remainingSeconds: Int
) {
    removeCurrentOverlay()
    
    try {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.overlay_exit_confirmation, null)
        
        val params = createFullScreenParams()
        
        val tvAppName = view.findViewById<TextView>(R.id.tvExitAppName)
        val tvTimeRemaining = view.findViewById<TextView>(R.id.tvTimeRemaining)
        val btnYesClose = view.findViewById<TextView>(R.id.btnYesCloseApp)
        val btnNoKeep = view.findViewById<TextView>(R.id.btnNoKeepRunning)
        
        // Set app name
        try {
            val pm = context.packageManager
            val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
            tvAppName.text = "Close $label?"
        } catch (_: Exception) {
            tvAppName.text = "Close this app?"
        }

        // Show remaining time
        val timeText = String.format("%02d:%02d remaining", remainingMinutes, remainingSeconds)
        tvTimeRemaining.text = timeText
        
        btnYesClose.setOnClickListener {
            Log.d(TAG, "✅ User confirmed app exit: $packageName")
            removeCurrentOverlay()
            notifyExitConfirmed(packageName)
        }
        
        btnNoKeep.setOnClickListener {
            Log.d(TAG, "❌ User cancelled app exit: $packageName")
            removeCurrentOverlay()
            notifyExitCancelled(packageName)
        }
        
        windowManager.addView(view, params)
        currentOverlay = view
        
        Log.d(TAG, "🚪 Exit confirmation shown for: $packageName")
    } catch (e: Exception) {
        Log.e(TAG, "❌ Failed to show exit confirmation: ${e.message}", e)
    }
}

    
     private fun notifyExitConfirmed(packageName: String) {
    listeners.forEach { it.onExitConfirmed(packageName) }
}

private fun notifyExitCancelled(packageName: String) {
    listeners.forEach { it.onExitCancelled(packageName) }
}

    // ========== TIME FINISHED POPUP ==========
    
    fun showTimeFinishedPopup(packageName: String) {
        removeCurrentOverlay()
        
        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.overlay_cooldown_lock, null)
            
            val params = createFullScreenParams()
            
            val tvAppName = view.findViewById<TextView>(R.id.tvCooldownAppName)
            val tvTimer = view.findViewById<TextView>(R.id.tvCooldownTimer)
            val btnCloseApp = view.findViewById<TextView>(R.id.btnCloseApp)
            
            view.findViewById<Button>(R.id.btnReuseAfter)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvAutoCloseCountdown)?.visibility = View.GONE
            
            try {
                val pm = context.packageManager
                val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
                tvAppName.text = "Time finished for $label"
            } catch (_: Exception) {
                tvAppName.text = "Your time is finished"
            }
            
            tvTimer.text = "00:00"
            
            btnCloseApp.setOnClickListener {
                removeCurrentOverlay()
                notifyCloseAppRequested()
            }
            
            handler.postDelayed({
                removeCurrentOverlay()
                notifyCloseAppRequested()
            }, 3000)
            
            windowManager.addView(view, params)
            currentOverlay = view
            
            Log.d(TAG, "⏰ Time finished popup shown")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show time finished: ${e.message}", e)
        }
    }
    
    // ========== COOLDOWN LOCK POPUP ==========
    
    fun showCooldownLockPopup(packageName: String, cooldownMs: Long) {
        removeCurrentOverlay()
        
        try {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.overlay_cooldown_lock, null)
            
            val params = createFullScreenParams()
            
            val tvAppName = view.findViewById<TextView>(R.id.tvCooldownAppName)
            val tvTimer = view.findViewById<TextView>(R.id.tvCooldownTimer)
            val btnReuseAfter = view.findViewById<Button>(R.id.btnReuseAfter)
            val btnCloseApp = view.findViewById<TextView>(R.id.btnCloseApp)
            val tvAutoClose = view.findViewById<TextView>(R.id.tvAutoCloseCountdown)
            
            try {
                val pm = context.packageManager
                val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
                tvAppName.text = label.toString()
            } catch (_: Exception) {
                tvAppName.text = "This app"
            }
            
            val totalSeconds = (cooldownMs / 1000).toInt()
            currentTimer = object : CountDownTimer(cooldownMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val sec = (millisUntilFinished / 1000).toInt()
                    val m = sec / 60
                    val s = sec % 60
                    val timeStr = String.format("%02d:%02d", m, s)
                    tvTimer.text = timeStr
                    btnReuseAfter.text = "Reuse after $timeStr"
                    
                    val autoCloseSec = sec.coerceAtMost(5)
                    tvAutoClose.text = "Closing in ${autoCloseSec}s..."
                }
                
                override fun onFinish() {
                    removeCurrentOverlay()
                    notifyCloseAppRequested()
                }
            }.start()
            
            btnCloseApp.setOnClickListener {
                removeCurrentOverlay()
                notifyCloseAppRequested()
            }
            
            windowManager.addView(view, params)
            currentOverlay = view
            
            Log.d(TAG, "🔒 Cooldown lock shown: ${totalSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show cooldown lock: ${e.message}", e)
        }
    }
    
    // ========== UTILITIES ==========
    
    private fun createFullScreenParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
    }
    
    fun removeCurrentOverlay() {
        currentOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay: ${e.message}")
            }
        }
        currentOverlay = null
        
        currentTimer?.cancel()
        currentTimer = null
    }
    
    fun clearTransitioning() {
        isTransitioning = false
    }
    
    fun cleanupAll() {
        removeCurrentOverlay()
        timerPills.values.forEach { pill ->
            try { windowManager.removeView(pill) } catch (_: Exception) {}
        }
        timerPills.clear()
    }
    
    // ========== LISTENERS ==========
    
    fun addListener(listener: OverlayEventListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: OverlayEventListener) {
        listeners.remove(listener)
    }
    
    private fun notifyConfirmationYes(packageName: String) {
        listeners.forEach { it.onConfirmationYes(packageName) }
    }
    
    private fun notifyConfirmationNo(packageName: String) {
        listeners.forEach { it.onConfirmationNo(packageName) }
    }
    
    private fun notifyTimeSelected(packageName: String, minutes: Int) {
        listeners.forEach { it.onTimeSelected(packageName, minutes) }
    }
    
    private fun notifyImpulseCooldownFinished(packageName: String, minutes: Int) {
        listeners.forEach { it.onImpulseCooldownFinished(packageName, minutes) }
    }
    
    private fun notifyCloseAppRequested() {
        listeners.forEach { it.onCloseAppRequested() }
    }
}
