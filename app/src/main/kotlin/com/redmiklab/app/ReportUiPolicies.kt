package com.redmiklab.app

enum class RetryDelayUnit(val label: String, private val multiplier: Int) {
    SECONDS("秒", 1),
    MINUTES("分", 60),
    HOURS("时", 3_600),
    ;

    fun toSeconds(value: Int): Int =
        (value.toLong() * multiplier).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

data class RetryDelayValue(val value: Int, val unit: RetryDelayUnit) {
    companion object {
        fun fromSeconds(seconds: Int): RetryDelayValue = when {
            seconds > 0 && seconds % 3_600 == 0 -> RetryDelayValue(seconds / 3_600, RetryDelayUnit.HOURS)
            seconds > 0 && seconds % 60 == 0 -> RetryDelayValue(seconds / 60, RetryDelayUnit.MINUTES)
            else -> RetryDelayValue(seconds, RetryDelayUnit.SECONDS)
        }
    }
}

class ProcessFeedbackGate {
    private var shown = false

    @Synchronized
    fun take(): Boolean {
        if (shown) return false
        shown = true
        return true
    }
}
