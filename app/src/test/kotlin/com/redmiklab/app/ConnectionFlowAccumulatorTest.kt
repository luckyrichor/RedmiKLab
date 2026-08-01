package com.redmiklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFlowAccumulatorTest {
    @Test
    fun aggregates_bytes_only_when_flow_owner_and_activity_class_match() {
        val accumulator = ConnectionFlowAccumulator()
        val foreground = CapturedConnectionFlow(10, 6, "10.0.0.2", 50000, "2001:db8::2", 443, 40, "video", "Video", "FOREGROUND_ACTIVE")
        accumulator.add(foreground)
        accumulator.add(foreground.copy(timestampEpochMs = 20, wireBytes = 60))
        accumulator.add(foreground.copy(timestampEpochMs = 30, wireBytes = 10, activityClass = "BACKGROUND_SCREEN_LOCKED"))

        val drained = accumulator.drain().sortedBy { it.activityClass }
        assertEquals(2, drained.size)
        assertEquals(10, drained[0].wireBytes)
        assertEquals(100, drained[1].wireBytes)
        assertEquals(20, drained[1].timestampEpochMs)
        assertEquals(emptyList<AggregatedConnectionFlow>(), accumulator.drain())
    }

    @Test
    fun same_flow_at_different_forwarding_layers_remains_separate() {
        val accumulator = ConnectionFlowAccumulator()
        val observed = CapturedConnectionFlow(
            10, 6, "10.0.0.2", 50_000, "203.0.113.8", 443, 100,
            "video", "Video", "FOREGROUND_ACTIVE", "cdn.example",
            direction = "UP", stage = "TUNNEL_OBSERVED", outcome = "OBSERVED", reason = null,
        )
        accumulator.add(observed)
        accumulator.add(
            observed.copy(
                timestampEpochMs = 20,
                wireBytes = 80,
                stage = "UPSTREAM_SOCKET_ACCEPTED",
                outcome = "ACCEPTED",
            ),
        )

        val drained = accumulator.drain()

        assertEquals(2, drained.size)
        assertEquals(setOf("TUNNEL_OBSERVED", "UPSTREAM_SOCKET_ACCEPTED"), drained.map { it.stage }.toSet())
    }

    @Test
    fun retains_zero_byte_connection_failure_as_diagnostic_evidence() {
        val accumulator = ConnectionFlowAccumulator()

        accumulator.add(
            CapturedConnectionFlow(
                10, 6, "10.0.0.2", 50_000, "203.0.113.8", 443, 0,
                null, null, "UNATTRIBUTED", direction = "UP", stage = "FORWARDING_FAILED",
                outcome = "FAILED", reason = "CONNECT_FAILED",
            ),
        )

        assertEquals("CONNECT_FAILED", accumulator.drain().single().reason)
    }
}
