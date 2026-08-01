package com.redmiklab.app

import com.redmiklab.model.ProbeFailure
import com.redmiklab.model.ProbeResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailoverProbeRunnerTest {
    @Test
    fun tries_fallback_after_primary_connection_failure() {
        val attempted = mutableListOf<String>()
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test", "https://fallback.example/test"),
            reserveAttempt = { true },
            retryDelayMs = 0,
            runAttempt = { endpoint ->
                attempted += endpoint
                if (endpoint.contains("primary")) failedConnection() else successfulProbe()
            },
        ).run()

        assertEquals(List(5) { "https://primary.example/test" } + "https://fallback.example/test", attempted)
        assertNull(result.failure)
    }

    @Test
    fun stops_before_fallback_when_budget_cannot_reserve_another_attempt() {
        var reserves = 0
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test", "https://fallback.example/test"),
            reserveAttempt = { ++reserves == 1 },
            retryDelayMs = 0,
            runAttempt = { failedConnection() },
        ).run()

        assertEquals(2, reserves)
        assertEquals(ProbeFailure.BudgetExhausted, result.failure)
    }

    @Test
    fun retries_primary_five_times_before_trying_fallback_five_times_with_delay_between_failures() {
        val attempted = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test", "https://fallback.example/test"),
            reserveAttempt = { true },
            retryDelayMs = 10_000,
            waitBeforeRetry = { delays += it },
            runAttempt = { endpoint ->
                attempted += endpoint
                failedConnection()
            },
        ).run()

        assertEquals(List(5) { "https://primary.example/test" } + List(5) { "https://fallback.example/test" }, attempted)
        assertEquals(List(9) { 10_000L }, delays)
        assertEquals(ProbeFailure.Connection, result.failure)
    }

    @Test
    fun stops_retrying_when_budget_runs_out_before_the_next_attempt() {
        var reserves = 0
        val attempted = mutableListOf<String>()
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test", "https://fallback.example/test"),
            reserveAttempt = { ++reserves <= 3 },
            retryDelayMs = 10_000,
            waitBeforeRetry = {},
            runAttempt = { endpoint ->
                attempted += endpoint
                failedConnection()
            },
        ).run()

        assertEquals(4, reserves)
        assertEquals(3, attempted.size)
        assertEquals(ProbeFailure.BudgetExhausted, result.failure)
    }

    @Test
    fun successful_result_includes_bytes_from_earlier_failed_attempts() {
        var attempts = 0
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test"),
            reserveAttempt = { true },
            retryDelayMs = 0,
            runAttempt = {
                attempts += 1
                if (attempts < 3) failedConnection(bytes = 100) else successfulProbe(bytes = 200)
            },
        ).run()

        assertEquals(400L, result.consumedBytes)
        assertNull(result.failure)
    }

    @Test
    fun final_failure_includes_bytes_from_every_failed_attempt() {
        val result = FailoverProbeRunner(
            endpoints = listOf("https://primary.example/test", "https://fallback.example/test"),
            reserveAttempt = { true },
            retryDelayMs = 0,
            runAttempt = { failedConnection(bytes = 100) },
        ).run()

        assertEquals(1_000L, result.consumedBytes)
        assertEquals(ProbeFailure.Connection, result.failure)
    }

    private fun failedConnection(bytes: Long = 0) =
        ProbeResult(Instant.EPOCH, null, null, null, null, bytes, ProbeFailure.Connection)

    private fun successfulProbe(bytes: Long = 5_000_000) = ProbeResult(Instant.EPOCH, 10, 20, 8.0, null, bytes, null)
}
