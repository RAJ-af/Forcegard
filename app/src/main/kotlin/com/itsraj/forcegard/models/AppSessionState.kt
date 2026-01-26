// models/AppSessionState.kt
package com.itsraj.forcegard.models

import com.itsraj.forcegard.utils.AppCategory

data class AppSessionState(
    val packageName: String,
    val isMonitored: Boolean,
    val hasActiveTimer: Boolean,
    val isInCooldown: Boolean,
    val category: AppCategory = AppCategory.OTHER,
    val lastDetectedTime: Long = System.currentTimeMillis()
)
