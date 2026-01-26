// models/CooldownData.kt
package com.itsraj.forcegard.models

data class CooldownData(
    val packageName: String,
    val cooldownEndMillis: Long,
    val reason: CooldownReason
)

enum class CooldownReason {
    USER_REJECTED,
    TIMER_EXPIRED
}
