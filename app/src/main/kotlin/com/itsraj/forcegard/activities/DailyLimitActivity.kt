package com.itsraj.forcegard.activities

import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.itsraj.forcegard.R
import com.itsraj.forcegard.limits.DailyLimitConfig
import com.itsraj.forcegard.limits.DailyLimitManager

class DailyLimitActivity : AppCompatActivity() {

    private lateinit var hourPicker: NumberPicker
    private lateinit var minutePicker: NumberPicker
    private lateinit var rbWeekly: RadioButton
    private lateinit var rbMonthly: RadioButton
    private lateinit var rbResetMidnight: RadioButton
    private lateinit var rbReset5am: RadioButton
    private lateinit var btnSave: Button
    
    private lateinit var dailyLimitManager: DailyLimitManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_limit)
        
        dailyLimitManager = DailyLimitManager(this)
        
        initViews()
        loadExistingConfig()
    }

    private fun initViews() {
        hourPicker = findViewById(R.id.pickerHours)
        minutePicker = findViewById(R.id.pickerMinutes)
        rbWeekly = findViewById(R.id.rbWeekly)
        rbMonthly = findViewById(R.id.rbMonthly)
        rbResetMidnight = findViewById(R.id.rbResetMidnight)
        rbReset5am = findViewById(R.id.rbReset5am)
        btnSave = findViewById(R.id.btnSaveLimit)

        // Setup hour picker (0-12 hours)
        hourPicker.minValue = 0
        hourPicker.maxValue = 12
        hourPicker.wrapSelectorWheel = false
        
        // Setup minute picker (0, 5, 10, 15... 55)
        minutePicker.minValue = 0
        minutePicker.maxValue = 11
        minutePicker.displayedValues = arrayOf(
            "0", "5", "10", "15", "20", "25", 
            "30", "35", "40", "45", "50", "55"
        )
        minutePicker.wrapSelectorWheel = false

        btnSave.setOnClickListener {
            saveLimit()
        }
    }

    private fun loadExistingConfig() {
        val config = dailyLimitManager.getConfig()
        
        if (config != null) {
            val hours = config.limitMinutes / 60
            val minutes = config.limitMinutes % 60
            
            hourPicker.value = hours
            val minuteIndex = minutes / 5
            minutePicker.value = minuteIndex
            
            // FIXED: using planDays
            if (config.planDays == 7) {
                rbWeekly.isChecked = true
            } else {
                rbMonthly.isChecked = true
            }
            
            if (config.resetHour == 0) {
                rbResetMidnight.isChecked = true
            } else {
                rbReset5am.isChecked = true
            }
        } else {
            hourPicker.value = 3
            minutePicker.value = 0
            rbWeekly.isChecked = true
            rbReset5am.isChecked = true
        }
    }

    private fun saveLimit() {
        val hours = hourPicker.value
        val minuteIndex = minutePicker.value
        val minutes = minuteIndex * 5
        
        val totalMinutes = (hours * 60) + minutes
        
        // Validation
        if (totalMinutes < 30) {
            Toast.makeText(this, "Please set at least 30 minutes", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (totalMinutes > 720) {
            Toast.makeText(this, "Maximum limit is 12 hours", Toast.LENGTH_SHORT).show()
            return
        }
        
        val planDuration = if (rbWeekly.isChecked) 7 else 30
        val resetHour = if (rbResetMidnight.isChecked) 0 else 5
        
        // FIXED: Correct parameter names matching DailyLimitConfig
        val config = DailyLimitConfig(
            enabled = true,
            limitMinutes = totalMinutes,
            planDays = planDuration,
            resetHour = resetHour,
            startDayEpoch = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        )
        
        dailyLimitManager.saveConfig(config)
        
        val hoursText = if (hours > 0) "${hours}h " else ""
        val minutesText = if (minutes > 0) "${minutes}m" else ""
        val planText = if (planDuration == 7) "7 days" else "30 days"
        val resetText = if (resetHour == 0) "12:00 AM" else "5:00 AM"
        
        Toast.makeText(
            this,
            "Daily limit set: $hoursText$minutesText\nPlan: $planText\nResets at: $resetText",
            Toast.LENGTH_LONG
        ).show()
        
        finish()
    }
}
