package com.itsraj.forcegard.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.util.*

class PeriodSelectionActivity : AppCompatActivity() {

    private lateinit var weeklyCard: CardView
    private lateinit var monthlyCard: CardView
    private lateinit var weeklyText: TextView
    private lateinit var monthlyText: TextView
    private lateinit var weeklySubtext: TextView
    private lateinit var monthlySubtext: TextView
    
    private var selectedPeriod = "weekly"
    private var totalMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load temp data
        val prefs = getSharedPreferences("forcegard_prefs", Context.MODE_PRIVATE)
        totalMinutes = prefs.getInt("temp_total_minutes", 270)
        
        setContentView(createUI())
    }
    
    private fun createUI(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        
        // Header
        container.addView(createHeader())
        
        // Title
        container.addView(TextView(this).apply {
            text = "Choose period"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                bottomMargin = dp(8)
            }
        })
        
        // Subtitle
        container.addView(TextView(this).apply {
            text = "How often do you want to adjust your limit?"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                bottomMargin = dp(48)
            }
        })
        
        // Weekly Card
        val weeklyPair = createPeriodCard("Weekly", "Change limit every 7 days", true)
        weeklyCard = weeklyPair.first
        weeklyText = weeklyPair.second
        weeklySubtext = weeklyPair.third
        weeklyCard.setOnClickListener {
            selectedPeriod = "weekly"
            updateSelection()
        }
        container.addView(weeklyCard)
        
        // Monthly Card
        val monthlyPair = createPeriodCard("Monthly", "Change limit every 30 days", false)
        monthlyCard = monthlyPair.first
        monthlyText = monthlyPair.second
        monthlySubtext = monthlyPair.third
        monthlyCard.setOnClickListener {
            selectedPeriod = "monthly"
            updateSelection()
        }
        container.addView(monthlyCard)
        
        // Spacer
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        })
        
        // Continue Button
        container.addView(createButton("CONTINUE") {
            saveAndFinish()
        })
        
        root.addView(container)
        return root
    }
    
    private fun createHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(32)
            }
            
            addView(TextView(this@PeriodSelectionActivity).apply {
                text = "FORCEGARD"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            
            addView(View(this@PeriodSelectionActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#df2531"))
                }
            })
            
            addView(TextView(this@PeriodSelectionActivity).apply {
                text = "Safe Parental"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 14f
            })
        }
    }
    
    private fun createPeriodCard(title: String, subtitle: String, isSelected: Boolean): Triple<CardView, TextView, TextView> {
        val card = CardView(this).apply {
            setCardBackgroundColor(if (isSelected) Color.parseColor("#df2531") else Color.parseColor("#1F1F1F"))
            radius = dp(16).toFloat()
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(16)
            }
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                null, null
            )
        }
        
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        
        val titleText = TextView(this).apply {
            text = title
            setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#AAAAAA"))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                bottomMargin = dp(8)
            }
        }
        inner.addView(titleText)
        
        val subtitleText = TextView(this).apply {
            text = subtitle
            setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#888888"))
            textSize = 14f
            alpha = if (isSelected) 0.7f else 1f
        }
        inner.addView(subtitleText)
        
        card.addView(inner)
        return Triple(card, titleText, subtitleText)
    }
    
    private fun createButton(text: String, onClick: () -> Unit): CardView {
        return CardView(this).apply {
            setCardBackgroundColor(Color.parseColor("#df2531"))
            radius = dp(12).toFloat()
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                null, null
            )
            setOnClickListener { onClick() }
            
            addView(TextView(this@PeriodSelectionActivity).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                letterSpacing = 0.05f
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            })
        }
    }
    
    private fun updateSelection() {
        if (selectedPeriod == "weekly") {
            weeklyCard.setCardBackgroundColor(Color.parseColor("#df2531"))
            monthlyCard.setCardBackgroundColor(Color.parseColor("#1F1F1F"))
            weeklyText.setTextColor(Color.WHITE)
            monthlyText.setTextColor(Color.parseColor("#AAAAAA"))
            weeklySubtext.setTextColor(Color.WHITE)
            weeklySubtext.alpha = 0.7f
            monthlySubtext.setTextColor(Color.parseColor("#888888"))
            monthlySubtext.alpha = 1f
        } else {
            monthlyCard.setCardBackgroundColor(Color.parseColor("#df2531"))
            weeklyCard.setCardBackgroundColor(Color.parseColor("#1F1F1F"))
            monthlyText.setTextColor(Color.WHITE)
            weeklyText.setTextColor(Color.parseColor("#AAAAAA"))
            monthlySubtext.setTextColor(Color.WHITE)
            monthlySubtext.alpha = 0.7f
            weeklySubtext.setTextColor(Color.parseColor("#888888"))
            weeklySubtext.alpha = 1f
        }
    }
    
    private fun saveAndFinish() {
        val prefs = getSharedPreferences("forcegard_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("spend_limit_set", true)
            putInt("daily_limit_minutes", totalMinutes)
            putString("period_type", selectedPeriod)
            putLong("installation_timestamp", System.currentTimeMillis())
            
            val calendar = Calendar.getInstance()
            if (selectedPeriod == "weekly") {
                calendar.add(Calendar.DAY_OF_YEAR, 7)
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, 30)
            }
            putLong("period_end_date", calendar.timeInMillis)
            apply()
        }
        
        // Go to Dashboard
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
