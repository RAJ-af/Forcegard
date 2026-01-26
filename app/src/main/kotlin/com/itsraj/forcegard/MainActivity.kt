package com.itsraj.forcegard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.itsraj.forcegard.activities.DashboardActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Redirect to Dashboard
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}