package com.itsraj.forcegard.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itsraj.forcegard.R

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("ForcegardPrefs", MODE_PRIVATE)
        val introShown = prefs.getBoolean("intro_shown", false)
        
        if (introShown) {
            // Navigate to Permission screen
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }
        
        setContentView(createIntroUI())
    }
    
    private fun createIntroUI(): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        
        // Pure black background
        val rootFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#000000"))
        }
        
        // ScrollView
        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }
        
        // Content container
        val contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp(), 0, 28.dp(), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Red danger bar at top
        val dangerBar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                40.dp(),
                4.dp()
            ).apply {
                bottomMargin = 48.dp()
            }
            setBackgroundColor(Color.parseColor("#df2531"))
        }
        contentLayout.addView(dangerBar)
        
        // App name (bold, white)
        val appName = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dp()
            }
            text = "FORCEGARD"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = 0.15f
        }
        contentLayout.addView(appName)
        
        // Subtitle
        val subtitle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 56.dp()
            }
            text = "Digital Discipline System"
            setTextColor(Color.parseColor("#666666"))
            textSize = 13f
        }
        contentLayout.addView(subtitle)
        
        // Main heading (large, white)
        val mainHeading = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dp()
            }
            text = "Restrictions will be enforced."
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setLineSpacing(6f, 1f)
        }
        contentLayout.addView(mainHeading)
        
        // Sub heading
        val subHeading = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 48.dp()
            }
            text = "You will not be able to bypass limits once activated."
            setTextColor(Color.parseColor("#999999"))
            textSize = 16f
            setLineSpacing(4f, 1f)
        }
        contentLayout.addView(subHeading)
        
        // Feature list (minimal)
        val features = listOf(
            Pair("Interrupt", "Blocks distracting apps instantly"),
            Pair("Limit", "Automatic session termination"),
            Pair("Track", "Complete usage monitoring")
        )
        
        features.forEach { (title, desc) ->
            contentLayout.addView(createFeatureRow(title, desc))
        }
        
        // Spacer
        contentLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                40.dp()
            )
        })
        
        // Warning line (red)
        val warningLine = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40.dp()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val warningIndicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(3.dp(), 3.dp()).apply {
                rightMargin = 10.dp()
            }
            setBackgroundColor(Color.parseColor("#df2531"))
        }
        warningLine.addView(warningIndicator)
        
        val warningText = TextView(this).apply {
            text = "This is enforcement, not guidance"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        warningLine.addView(warningText)
        
        contentLayout.addView(warningLine)
        
        // Accept button (red) - UPDATED TO NAVIGATE TO PERMISSION SCREEN
        val btnAccept = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                60.dp()
            ).apply {
                bottomMargin = 32.dp()
            }
            text = "I Accept Control"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            background = createRedButton()
            isAllCaps = false
            
            setOnClickListener {
                // Mark intro as shown
                getSharedPreferences("ForcegardPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("intro_shown", true)
                    .apply()
                
                // Navigate to Permission screen (NOT MainActivity)
                startActivity(Intent(this@IntroActivity, PermissionActivity::class.java))
                finish()
            }
        }
        contentLayout.addView(btnAccept)
        
        scrollView.addView(contentLayout)
        rootFrame.addView(scrollView)
        
        return rootFrame
    }
    
    private fun createFeatureRow(title: String, description: String): LinearLayout {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dp()
            }
            orientation = LinearLayout.HORIZONTAL
        }
        
        // Red vertical line
        val line = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2.dp(), 50.dp()).apply {
                rightMargin = 20.dp()
            }
            setBackgroundColor(Color.parseColor("#df2531"))
        }
        row.addView(line)
        
        // Text container
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }
        
        // Title (white)
        val titleText = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        textContainer.addView(titleText)
        
        // Description (gray)
        val descText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp()
            }
            text = description
            setTextColor(Color.parseColor("#777777"))
            textSize = 14f
        }
        textContainer.addView(descText)
        
        row.addView(textContainer)
        return row
    }
    
    private fun createRedButton(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#df2531"))
            cornerRadius = 8f
        }
    }
}
