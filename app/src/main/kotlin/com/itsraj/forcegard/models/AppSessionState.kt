// models/AppSessionState.kt
package com.itsraj.forcegard.models

data class AppSessionState(
    val packageName: String,
    val isMonitored: Boolean,
    val hasActiveTimer: Boolean,
    val isInCooldown: Boolean,
    val lastDetectedTime: Long = System.currentTimeMillis()
)
