// models/TimerData.kt
package com.itsraj.forcegard.models

data class TimerData(
    val packageName: String,
    val endTimeMillis: Long,
    val totalDurationMs: Long,
    val startTimeMillis: Long = System.currentTimeMillis()
)
