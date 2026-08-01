package com.redmiklab.app

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkHeartbeatRunnerTest {
    @Test
    fun failed_heartbeat_retains_bytes_read_before_transport_failure() {
        val runner = NetworkHeartbeatRunner<String>(
            clock = sequenceClock(1_000, 1_250),
            transport = HeartbeatTransport { _, _, _ ->
                HeartbeatTransportResult(
                    dnsBytes = 0,
                    requestBytes = 41,
                    responseBytes = 37,
                    failureStage = "RESPONSE_READ",
                    error = IOException("reset"),
                )
            },
        )

        val result = runner.run("network-119", "https://example.test/ping", 64)

        assertEquals(1_000L, result.startedAtEpochMs)
        assertEquals(1_250L, result.completedAtEpochMs)
        assertEquals(41L, result.requestBytes)
        assertEquals(37L, result.responseBytes)
        assertEquals("RESPONSE_READ", result.failureStage)
        assertEquals("IOException", result.errorType)
    }

    @Test
    fun successful_heartbeat_preserves_separate_request_and_response_layers() {
        val runner = NetworkHeartbeatRunner<String>(
            clock = sequenceClock(2_000, 2_100),
            transport = HeartbeatTransport { _, _, _ ->
                HeartbeatTransportResult(12, 55, 1, null, null)
            },
        )

        val result = runner.run("network-120", "https://example.test/ping", 1)

        assertEquals(12L, result.dnsBytes)
        assertEquals(55L, result.requestBytes)
        assertEquals(1L, result.responseBytes)
        assertEquals(null, result.failureStage)
    }

    @Test
    fun one_endpoint_failure_does_not_invalidate_network_when_fallback_succeeds() {
        val failed = HeartbeatResult(1, 2, "primary", 0, 10, 0, "CONNECT", "IOException")
        val succeeded = HeartbeatResult(3, 4, "fallback", 0, 10, 1, null, null)

        assertEquals(false, HeartbeatDecisionPolicy.shouldInvalidateNetwork(listOf(failed, succeeded)))
        assertEquals(true, HeartbeatDecisionPolicy.shouldInvalidateNetwork(listOf(failed, failed)))
    }

    private fun sequenceClock(vararg values: Long): () -> Long {
        val iterator = values.iterator()
        return { iterator.next() }
    }
}
