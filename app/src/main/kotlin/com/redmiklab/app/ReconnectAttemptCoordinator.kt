package com.redmiklab.app

interface RetryScheduler {
    fun schedule(delayMs: Long, action: () -> Unit)
}

data class ScreenWakeRequestResult(
    val accepted: Boolean,
    val details: String,
)

enum class ReconnectActionType {
    RETRY_SCHEDULED,
    SCREEN_WAKE_REQUESTED,
    RETRY_STARTED,
    RETRIES_EXHAUSTED,
}

data class ReconnectActionEvent(
    val type: ReconnectActionType,
    val generation: Long,
    val attempt: Int,
    val details: String,
)

object ReconnectAttemptPolicy {
    const val SCREEN_SETTLE_DELAY_MS = 10_000L
    private val screenWakeAttempts = setOf(1, 4)

    fun shouldWakeScreen(attempt: Int): Boolean = attempt in screenWakeAttempts
}

class ReconnectAttemptCoordinator(
    private val scheduler: RetryScheduler,
    private val isCurrent: (generation: Long, attempt: Int) -> Boolean,
    private val screenWake: (generation: Long, attempt: Int) -> ScreenWakeRequestResult,
    private val reconnect: (generation: Long, attempt: Int) -> Unit,
    private val recorder: (ReconnectActionEvent) -> Unit,
) {
    fun schedule(delayMs: Long, generation: Long, attempt: Int) {
        record(ReconnectActionType.RETRY_SCHEDULED, generation, attempt, "delayMs=$delayMs")
        scheduler.schedule(delayMs.coerceAtLeast(0)) {
            if (!isCurrent(generation, attempt)) return@schedule
            if (ReconnectAttemptPolicy.shouldWakeScreen(attempt)) {
                val result = screenWake(generation, attempt)
                record(
                    ReconnectActionType.SCREEN_WAKE_REQUESTED,
                    generation,
                    attempt,
                    "accepted=${result.accepted};${result.details}",
                )
                scheduler.schedule(ReconnectAttemptPolicy.SCREEN_SETTLE_DELAY_MS) {
                    startReconnectIfCurrent(generation, attempt)
                }
            } else {
                startReconnectIfCurrent(generation, attempt)
            }
        }
    }

    private fun startReconnectIfCurrent(generation: Long, attempt: Int) {
        if (!isCurrent(generation, attempt)) return
        record(ReconnectActionType.RETRY_STARTED, generation, attempt, "actual_request")
        reconnect(generation, attempt)
    }

    private fun record(
        type: ReconnectActionType,
        generation: Long,
        attempt: Int,
        details: String,
    ) {
        recorder(ReconnectActionEvent(type, generation, attempt, details))
    }
}
