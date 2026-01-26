// models/AppExitRequest.kt
package com.itsraj.forcegard.models

data class AppExitRequest(
    val packageName: String,
    val hasActiveTimer: Boolean,
    val remainingTimeMs: Long,
    val requestTime: Long = System.currentTimeMillis()
)
