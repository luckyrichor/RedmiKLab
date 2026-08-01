package com.redmiklab.app

interface NetworkGuardianEffects<T> {
    fun startMonitoring(generation: Long)
    fun stopMonitoring()
    fun pauseTunnel(reason: String)
    fun rebuildTunnel(network: T)
    fun openGap(reason: String)
    fun closeGap(networkId: String)
    fun scheduleRetry(delayMs: Long, generation: Long, attempt: Int)
    fun recordTransition(previous: GuardianState, current: GuardianState)
}

class NetworkGuardian<T>(
    private val effects: NetworkGuardianEffects<T>,
) {
    var state: GuardianState = GuardianState.initial()
        private set

    @Synchronized
    fun start() {
        if (state.phase != GuardianPhase.WAITING_FOR_PLAN && state.phase != GuardianPhase.STOPPED) return
        apply(GuardianEvent.PlanStarted)
    }

    @Synchronized
    fun networkAvailable(generation: Long, networkId: String, network: T) {
        apply(GuardianEvent.NetworkAvailable(generation, networkId), network)
    }

    @Synchronized
    fun networkLost(networkId: String?) {
        if (state.phase != GuardianPhase.HEALTHY ||
            (networkId != null && state.physicalNetworkId != null && networkId != state.physicalNetworkId)
        ) return
        apply(GuardianEvent.NetworkLost(networkId))
    }

    @Synchronized
    fun reconnectFailed(generation: Long) {
        apply(GuardianEvent.ReconnectFailed(generation))
    }

    @Synchronized
    fun stop(reason: String) {
        if (state.phase == GuardianPhase.STOPPED) return
        apply(GuardianEvent.Stop(reason))
    }

    private fun apply(event: GuardianEvent, network: T? = null) {
        val previous = state
        val decision = NetworkGuardianPolicy.onEvent(previous, event)
        state = decision.state
        if (state != previous) effects.recordTransition(previous, state)
        decision.actions.forEach { action ->
            when (action) {
                GuardianAction.START_MONITORING -> effects.startMonitoring(state.generation)
                GuardianAction.STOP_MONITORING -> effects.stopMonitoring()
                GuardianAction.PAUSE_TUNNEL -> effects.pauseTunnel(state.reason)
                GuardianAction.OPEN_GAP -> effects.openGap(state.reason)
                GuardianAction.REBUILD_TUNNEL -> requireNotNull(network).let(effects::rebuildTunnel)
                GuardianAction.CLOSE_GAP -> effects.closeGap(requireNotNull(state.physicalNetworkId))
                GuardianAction.SCHEDULE_RETRY -> effects.scheduleRetry(
                    requireNotNull(state.nextRetryDelayMs),
                    state.generation,
                    state.retryAttempt,
                )
            }
        }
    }
}
