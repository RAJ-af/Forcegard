package com.itsraj.forcegard.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.itsraj.forcegard.limits.SpendLimitManager

class GuardService : Service() {

    private lateinit var spendLimitManager: SpendLimitManager
    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "guard_service_channel"

    override fun onCreate() {
        super.onCreate()
        spendLimitManager = SpendLimitManager(this)
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Forcegard Protection Active")
            .setContentText("Monitoring limits and security.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Forcegard Guard Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
