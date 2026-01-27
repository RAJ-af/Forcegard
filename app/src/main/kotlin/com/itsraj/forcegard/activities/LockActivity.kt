package com.itsraj.forcegard.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.itsraj.forcegard.databinding.OverlayDailyLimitReachedBinding
import com.itsraj.forcegard.limits.SpendLimitManager
import java.text.SimpleDateFormat
import java.util.*

class LockActivity : AppCompatActivity() {

    private lateinit var binding: OverlayDailyLimitReachedBinding
    private lateinit var spendLimitManager: SpendLimitManager
    private val handler = Handler(Looper.getMainLooper())

    private val updateTask = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverlayDailyLimitReachedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        spendLimitManager = SpendLimitManager(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - strict lock
            }
        })

        binding.btnAction.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTask)

        // If still should be locked, bring back to front
        if (shouldBeLocked()) {
            val intent = Intent(this, LockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }

    private fun shouldBeLocked(): Boolean {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this)
        val isLimitReached = spendLimitManager.isLimitReached()
        return !isAccessibilityEnabled || isLimitReached
    }

    private fun updateUI() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this)
        val isLimitReached = spendLimitManager.isLimitReached()

        if (isAccessibilityEnabled && !isLimitReached) {
            finish()
            return
        }

        if (!isAccessibilityEnabled) {
            binding.tvLockTitle.text = "SECURITY ALERT"
            binding.tvLockSubtitle.text = "Accessibility service has been disabled. Forcegard requires it to maintain discipline."
            binding.tvTimeRemaining.visibility = View.GONE
            binding.tvResetDate.visibility = View.GONE
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.text = "ENABLE ACCESSIBILITY"
        } else if (isLimitReached) {
            binding.tvLockTitle.text = "LIMIT REACHED"
            binding.tvLockSubtitle.text = "You have exhausted your digital spend limit."
            binding.tvTimeRemaining.visibility = View.VISIBLE
            binding.tvResetDate.visibility = View.VISIBLE
            binding.btnAction.visibility = View.GONE

            val remainingMs = getRemainingTimeUntilReset()
            binding.tvTimeRemaining.text = "Resets in: ${formatDuration(remainingMs)}"

            val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
            binding.tvResetDate.text = "Next reset: ${sdf.format(spendLimitManager.getNextResetDate())}"
        }
    }

    private fun getRemainingTimeUntilReset(): Long {
        return spendLimitManager.getNextResetDate().time - System.currentTimeMillis()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = "com.itsraj.forcegard/com.itsraj.forcegard.services.ForcegardAccessibilityService"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName) == true
    }
}
