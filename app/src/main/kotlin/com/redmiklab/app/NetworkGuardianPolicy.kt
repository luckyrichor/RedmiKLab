package com.redmiklab.app

enum class GuardianPhase {
    WAITING_FOR_PLAN,
    HEALTHY,
    RECONNECTING,
    WAITING_FOR_NETWORK,
    STOPPED,
}

enum class GuardianAction {
    START_MONITORING,
    PAUSE_TUNNEL,
    OPEN_GAP,
    SCHEDULE_RETRY,
    REBUILD_TUNNEL,
    CLOSE_GAP,
    STOP_MONITORING,
}

data class GuardianState(
    val phase: GuardianPhase,
    val generation: Long,
    val physicalNetworkId: String?,
    val retryAttempt: Int,
    val passiveMonitoring: Boolean,
    val nextRetryDelayMs: Long?,
    val reason: String,
) {
    companion object {
        fun initial() = GuardianState(
            GuardianPhase.WAITING_FOR_PLAN,
            generation = 0,
            physicalNetworkId = null,
            retryAttempt = 0,
            passiveMonitoring = false,
            nextRetryDelayMs = null,
            reason = "INITIAL",
        )

        fun healthy(generation: Long, networkId: String) = GuardianState(
            GuardianPhase.HEALTHY,
            generation,
            networkId,
            retryAttempt = 0,
            passiveMonitoring = true,
            nextRetryDelayMs = null,
            reason = "NETWORK_AVAILABLE",
        )

        fun waiting(generation: Long, retryAttempt: Int) = GuardianState(
            GuardianPhase.WAITING_FOR_NETWORK,
            generation,
            physicalNetworkId = null,
            retryAttempt,
            passiveMonitoring = true,
            nextRetryDelayMs = null,
            reason = "RETRIES_EXHAUSTED",
        )
    }
}

sealed interface GuardianEvent {
    data object PlanStarted : GuardianEvent
    data class NetworkLost(val networkId: String?) : GuardianEvent
    data class NetworkAvailable(val generation: Long, val networkId: String) : GuardianEvent
    data class ReconnectFailed(val generation: Long) : GuardianEvent
    data class Stop(val reason: String) : GuardianEvent
}

data class GuardianDecision(
    val state: GuardianState,
    val actions: Set<GuardianAction>,
)

object NetworkGuardianPolicy {
    val retryDelaysMs = listOf(30_000L, 30_000L, 60_000L, 120_000L, 300_000L)

    fun onEvent(state: GuardianState, event: GuardianEvent): GuardianDecision = when (event) {
        GuardianEvent.PlanStarted -> {
            val next = GuardianState(
                GuardianPhase.RECONNECTING,
                generation = state.generation + 1,
                physicalNetworkId = null,
                retryAttempt = 1,
                passiveMonitoring = true,
                nextRetryDelayMs = retryDelaysMs.first(),
                reason = "PLAN_STARTED",
            )
            GuardianDecision(next, setOf(GuardianAction.START_MONITORING, GuardianAction.SCHEDULE_RETRY))
        }

        is GuardianEvent.NetworkLost -> {
            if (state.phase == GuardianPhase.STOPPED) GuardianDecision(state, emptySet())
            else {
                val next = GuardianState(
                    GuardianPhase.RECONNECTING,
                    generation = state.generation + 1,
                    physicalNetworkId = null,
                    retryAttempt = 1,
                    passiveMonitoring = true,
                    nextRetryDelayMs = retryDelaysMs.first(),
                    reason = "NETWORK_LOST:${event.networkId ?: "UNKNOWN"}",
                )
                GuardianDecision(
                    next,
                    setOf(GuardianAction.PAUSE_TUNNEL, GuardianAction.OPEN_GAP, GuardianAction.SCHEDULE_RETRY),
                )
            }
        }

        is GuardianEvent.ReconnectFailed -> {
            if (event.generation != state.generation || state.phase != GuardianPhase.RECONNECTING) {
                GuardianDecision(state, emptySet())
            } else if (state.retryAttempt >= retryDelaysMs.size) {
                GuardianDecision(
                    GuardianState.waiting(state.generation, retryDelaysMs.size),
                    emptySet(),
                )
            } else {
                val nextAttempt = state.retryAttempt + 1
                GuardianDecision(
                    state.copy(
                        retryAttempt = nextAttempt,
                        nextRetryDelayMs = retryDelaysMs[nextAttempt - 1],
                        reason = "RECONNECT_FAILED",
                    ),
                    setOf(GuardianAction.SCHEDULE_RETRY),
                )
            }
        }

        is GuardianEvent.NetworkAvailable -> {
            if (event.generation != state.generation || state.phase == GuardianPhase.STOPPED) {
                GuardianDecision(state, emptySet())
            } else {
                GuardianDecision(
                    GuardianState.healthy(state.generation, event.networkId),
                    setOf(GuardianAction.REBUILD_TUNNEL, GuardianAction.CLOSE_GAP),
                )
            }
        }

        is GuardianEvent.Stop -> GuardianDecision(
            GuardianState(
                GuardianPhase.STOPPED,
                generation = state.generation + 1,
                physicalNetworkId = null,
                retryAttempt = 0,
                passiveMonitoring = false,
                nextRetryDelayMs = null,
                reason = event.reason,
            ),
            setOf(GuardianAction.STOP_MONITORING),
        )
    }
}
