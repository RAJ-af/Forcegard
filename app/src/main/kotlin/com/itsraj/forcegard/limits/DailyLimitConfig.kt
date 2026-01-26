package com.itsraj.forcegard.limits

data class DailyLimitConfig(
    val enabled: Boolean = true,
    val dailyLimitMinutes: Int,
    val planDays: Int,  // 7 or 30
    val resetHour: Int,  // 0 for midnight, 5 for 5 AM
    val startDayEpoch: Long  // Start date in epoch days
)
