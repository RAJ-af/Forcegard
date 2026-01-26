package com.itsraj.forcegard.activities

import android.app.AppOpsManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.itsraj.forcegard.MainActivity
import com.itsraj.forcegard.activities.DashboardActivity
import com.itsraj.forcegard.R

class PermissionActivity : AppCompatActivity() {

    private var contentLayout: LinearLayout? = null
    private var btnContinue: Button? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(createPermissionUI())
        } catch (e: Exception) {
            android.util.Log.e("PermissionActivity", "Error in onCreate", e)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    
    private fun createPermissionUI(): View {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        
        val rootLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
        }
        
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        contentLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp(), 60.dp(), 28.dp(), 32.dp())
        }
        
        // Red danger bar
        contentLayout?.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp(), 4.dp()).apply {
                bottomMargin = 48.dp()
            }
            setBackgroundColor(Color.parseColor("#df2531"))
        })
        
        // App name
        contentLayout?.addView(TextView(this).apply {
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
        })
        
        // Subtitle
        contentLayout?.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 56.dp()
            }
            text = "Basic Permissions"
            setTextColor(Color.parseColor("#666666"))
            textSize = 13f
        })
        
        // Main heading
        contentLayout?.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dp()
            }
            text = "Grant system permissions"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setLineSpacing(6f, 1f)
        })
        
        // Sub heading
        contentLayout?.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 48.dp()
            }
            text = "These are core Android permissions needed for monitoring."
            setTextColor(Color.parseColor("#999999"))
            textSize = 16f
            setLineSpacing(4f, 1f)
        })
        
        // Warning notice
        val warningNotice = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32.dp()
                bottomMargin = 16.dp()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        warningNotice.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(3.dp(), 3.dp()).apply {
                rightMargin = 10.dp()
            }
            setBackgroundColor(Color.parseColor("#df2531"))
        })
        
        warningNotice.addView(TextView(this).apply {
            text = "Additional service activation required on next screen"
            setTextColor(Color.WHITE)
            textSize = 13f
        })
        
        contentLayout?.addView(warningNotice)
        
        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)
        
        // Button container
        val buttonContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(28.dp(), 0, 28.dp(), 32.dp())
        }
        
        btnContinue = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                60.dp()
            )
         text = "Grant All Permissions First"
setTextColor(Color.WHITE)
textSize = 16f
typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
background = createGrayButton()
isAllCaps = false
isEnabled = false

setOnClickListener {
    if (allBasicPermissionsGranted()) {
        // Change "this" to "this@PermissionActivity"
        startActivity(Intent(this@PermissionActivity, DashboardActivity::class.java))  // ✅ Fixed
        finish()
    }
}

        }
        buttonContainer.addView(btnContinue)
        
        rootLayout.addView(buttonContainer)
        
        updatePermissionStatus()
        
        return rootLayout
    }
    
    private fun updatePermissionStatus() {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()
        
        val layout = contentLayout ?: return
        val button = btnContinue ?: return
        
        // Remove old permission cards
        val childCount = layout.childCount
        for (i in childCount - 1 downTo 0) {
            val child = layout.getChildAt(i)
            if (child.tag == "permission_card") {
                layout.removeViewAt(i)
            }
        }
        
        // Get permission statuses (ONLY 3 BASIC PERMISSIONS)
        val usageGranted = hasUsageStatsPermission()
        val overlayGranted = hasOverlayPermission()
        val notificationGranted = hasNotificationPermission()
        
        val permissions = listOf(
            PermissionItem(
                title = "Usage Access",
                subtitle = "Monitor app usage and screen time",
                description = "Required to track which apps you open and for how long",
                granted = usageGranted,
                number = "1"
            ),
            PermissionItem(
                title = "Overlay Permission",
                subtitle = "Display blocking screens",
                description = "Required to show restriction warnings over blocked apps",
                granted = overlayGranted,
                number = "2"
            ),
            PermissionItem(
                title = "Notifications",
                subtitle = "Send enforcement alerts",
                description = "Required to notify you about limit breaches and lockouts",
                granted = notificationGranted,
                number = "3"
            )
        )
        
        // Find index to insert
        var insertIndex = 0
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is TextView && child.text.contains("These are core")) {
                insertIndex = i + 1
                break
            }
        }
        
        permissions.forEach { permission ->
            val card = createPermissionCard(permission)
            card.tag = "permission_card"
            layout.addView(card, insertIndex++)
        }
        
        // Update button state
        if (allBasicPermissionsGranted()) {
            button.isEnabled = true
            button.background = createRedButton()
            button.text = "Continue to App"
        } else {
            button.isEnabled = false
            button.background = createGrayButton()
            button.text = "Grant All Permissions First"
        }
    }
    
    private fun createPermissionCard(permission: PermissionItem): LinearLayout {
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
            
            // Make clickable only if not granted
            if (!permission.granted) {
                setOnClickListener {
                    openPermissionSettings(permission.number)
                }
            }
        }
        
        // Vertical line (RED or GREEN)
        row.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(2.dp(), 50.dp()).apply {
                rightMargin = 20.dp()
            }
            setBackgroundColor(
                if (permission.granted) {
                    Color.parseColor("#4CAF50") // Green
                } else {
                    Color.parseColor("#df2531") // Red
                }
            )
        })
        
        // Text container
        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }
        
        textContainer.addView(TextView(this).apply {
            text = permission.title
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        })
        
        textContainer.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp()
            }
            text = permission.subtitle
            setTextColor(Color.parseColor("#777777"))
            textSize = 14f
        })
        
        textContainer.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
            text = permission.description
            setTextColor(Color.parseColor("#666666"))
            textSize = 13f
            setLineSpacing(2f, 1f)
        })
        
        row.addView(textContainer)
        return row
    }
    
    private fun openPermissionSettings(permissionNumber: String) {
        when (permissionNumber) {
            "1" -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "2" -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            "3" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
                }
            }
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hasOverlayPermission(): Boolean {
        return try {
            Settings.canDrawOverlays(this)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun hasNotificationPermission(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
    
    private fun allBasicPermissionsGranted(): Boolean {
        return hasUsageStatsPermission() && 
               hasOverlayPermission() && 
               hasNotificationPermission()
    }
    
    private fun createRedButton(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#df2531"))
            cornerRadius = 8f
        }
    }
    
    private fun createGrayButton(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#2A2A2A"))
            cornerRadius = 8f
        }
    }
    
    data class PermissionItem(
        val title: String,
        val subtitle: String,
        val description: String,
        val granted: Boolean,
        val number: String
    )
}