package com.redmiklab.app

import com.redmiklab.model.ProbeFailure
import com.redmiklab.model.ProbeResult
import java.time.Instant

class FailoverProbeRunner(
    endpoints: List<String>,
    private val reserveAttempt: () -> Boolean,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val waitBeforeRetry: (Long) -> Unit = Thread::sleep,
    private val onAttempt: (String, Int, ProbeResult) -> Unit = { _, _, _ -> },
    private val runAttempt: (String) -> ProbeResult,
) {
    private val endpoints = endpoints.filter { it.isNotBlank() }.distinct()

    fun run(): ProbeResult {
        var lastFailure: ProbeResult? = null
        var consumedBytes = 0L
        endpoints.forEachIndexed { endpointIndex, endpoint ->
            repeat(MAX_ATTEMPTS_PER_ENDPOINT) { attemptIndex ->
                if (!reserveAttempt()) return budgetExhausted(consumedBytes)
                val result = runAttempt(endpoint)
                consumedBytes += result.consumedBytes
                onAttempt(endpoint, endpointIndex * MAX_ATTEMPTS_PER_ENDPOINT + attemptIndex + 1, result)
                if (result.failure == null) return result.copy(consumedBytes = consumedBytes)
                lastFailure = result
                val hasNextAttempt = endpointIndex < endpoints.lastIndex || attemptIndex < MAX_ATTEMPTS_PER_ENDPOINT - 1
                if (hasNextAttempt && retryDelayMs > 0) waitBeforeRetry(retryDelayMs)
            }
        }
        return lastFailure?.copy(consumedBytes = consumedBytes) ?: budgetExhausted(consumedBytes)
    }

    private fun budgetExhausted(consumedBytes: Long) =
        ProbeResult(Instant.now(), null, null, null, null, consumedBytes, ProbeFailure.BudgetExhausted)

    companion object {
        const val MAX_ATTEMPTS_PER_ENDPOINT = 5
        const val DEFAULT_RETRY_DELAY_MS = 10_000L
    }
}
