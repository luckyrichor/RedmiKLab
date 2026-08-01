package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGuardianPolicyTest {
    @Test
    fun network_loss_pauses_tunnel_opens_gap_and_waits_30_seconds_before_first_retry() {
        val healthy = GuardianState.healthy(generation = 7, networkId = "115")

        val decision = NetworkGuardianPolicy.onEvent(healthy, GuardianEvent.NetworkLost("115"))

        assertEquals(GuardianPhase.RECONNECTING, decision.state.phase)
        assertEquals(8L, decision.state.generation)
        assertEquals(1, decision.state.retryAttempt)
        assertEquals(30_000L, decision.state.nextRetryDelayMs)
        assertTrue(GuardianAction.PAUSE_TUNNEL in decision.actions)
        assertTrue(GuardianAction.OPEN_GAP in decision.actions)
        assertTrue(GuardianAction.SCHEDULE_RETRY in decision.actions)
    }

    @Test
    fun fifth_failure_stops_dense_retries_but_keeps_passive_monitoring() {
        var state = NetworkGuardianPolicy.onEvent(
            GuardianState.healthy(7, "115"),
            GuardianEvent.NetworkLost("115"),
        ).state
        repeat(5) {
            state = NetworkGuardianPolicy.onEvent(
                state,
                GuardianEvent.ReconnectFailed(state.generation),
            ).state
        }

        assertEquals(GuardianPhase.WAITING_FOR_NETWORK, state.phase)
        assertTrue(state.passiveMonitoring)
        assertEquals(5, state.retryAttempt)
        assertNull(state.nextRetryDelayMs)
    }

    @Test
    fun retry_delays_are_30_seconds_then_30_seconds_1_minute_2_minutes_and_5_minutes() {
        var state = NetworkGuardianPolicy.onEvent(
            GuardianState.healthy(1, "115"),
            GuardianEvent.NetworkLost("115"),
        ).state
        val delays = mutableListOf(state.nextRetryDelayMs)
        repeat(4) {
            state = NetworkGuardianPolicy.onEvent(
                state,
                GuardianEvent.ReconnectFailed(state.generation),
            ).state
            delays += state.nextRetryDelayMs
        }

        assertEquals(listOf(30_000L, 30_000L, 60_000L, 120_000L, 300_000L), delays)
    }

    @Test
    fun stale_generation_cannot_rebuild_tunnel() {
        val state = GuardianState.waiting(generation = 9, retryAttempt = 5)

        val decision = NetworkGuardianPolicy.onEvent(
            state,
            GuardianEvent.NetworkAvailable(generation = 8, networkId = "119"),
        )

        assertEquals(state, decision.state)
        assertFalse(GuardianAction.REBUILD_TUNNEL in decision.actions)
    }

    @Test
    fun current_generation_network_rebuilds_tunnel_and_closes_gap() {
        val state = GuardianState.waiting(generation = 9, retryAttempt = 5)

        val decision = NetworkGuardianPolicy.onEvent(
            state,
            GuardianEvent.NetworkAvailable(generation = 9, networkId = "119"),
        )

        assertEquals(GuardianPhase.HEALTHY, decision.state.phase)
        assertEquals("119", decision.state.physicalNetworkId)
        assertTrue(GuardianAction.REBUILD_TUNNEL in decision.actions)
        assertTrue(GuardianAction.CLOSE_GAP in decision.actions)
    }

    @Test
    fun stop_disables_monitoring_without_reusing_old_callbacks() {
        val state = GuardianState.healthy(3, "119")
        val stopped = NetworkGuardianPolicy.onEvent(state, GuardianEvent.Stop("PLAN_CANCELLED"))

        assertEquals(GuardianPhase.STOPPED, stopped.state.phase)
        assertFalse(stopped.state.passiveMonitoring)
        assertEquals(4L, stopped.state.generation)
        assertTrue(GuardianAction.STOP_MONITORING in stopped.actions)
    }
}
