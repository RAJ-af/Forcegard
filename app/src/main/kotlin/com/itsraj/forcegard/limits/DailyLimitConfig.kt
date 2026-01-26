package com.itsraj.forcegard.limits

data class DailyLimitConfig(
    val enabled: Boolean = true,
    val limitMinutes: Int,
    val planDays: Int, // 1 for Daily, 7 for Weekly, 30 for Monthly
    val resetHour: Int = 0,
    val startDayEpoch: Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
)
