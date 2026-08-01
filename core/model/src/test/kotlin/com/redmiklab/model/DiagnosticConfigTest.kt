package com.redmiklab.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticConfigTest {
    @Test
    fun rejects_probe_limit_above_nightly_budget() {
        val config = DiagnosticConfig.default().copy(
            singleProbeLimitBytes = 101,
            nightlyBudgetBytes = 100,
        )

        assertEquals(listOf(ConfigIssue.SingleProbeExceedsBudget), config.validate())
    }

    @Test
    fun accepts_window_that_crosses_midnight() {
        val config = DiagnosticConfig.default().copy(
            start = LocalTime.of(23, 0),
            end = LocalTime.of(5, 0),
        )

        assertEquals(emptyList<ConfigIssue>(), config.validate())
    }

    @Test
    fun rejects_a_window_longer_than_six_hours() {
        val config = DiagnosticConfig.default().copy(
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(6, 1),
        )

        assertEquals(listOf(ConfigIssue.DiagnosticWindowTooLong), config.validate())
    }

    @Test
    fun strict_mode_allows_a_window_longer_than_six_hours() {
        val config = DiagnosticConfig.default().copy(
            end = LocalTime.of(7, 0),
            runtimeMode = DiagnosticRuntimeMode.STRICT,
        )

        assertEquals(emptyList<ConfigIssue>(), config.validate())
    }

    @Test
    fun cannot_start_when_the_diagnostic_window_is_too_long() {
        val config = DiagnosticConfig.default().copy(end = LocalTime.of(6, 1))

        assertEquals(false, config.canStartDiagnosis())
    }

    @Test
    fun allows_saving_valid_probe_settings_even_when_the_window_needs_reconfiguration() {
        val config = DiagnosticConfig.default().copy(end = LocalTime.of(7, 0))

        assertEquals(true, config.hasValidProbeSettings())
        assertEquals(false, config.canStartDiagnosis())
    }

    @Test
    fun rejects_retry_delay_longer_than_five_minutes() {
        val config = DiagnosticConfig.default().copy(probeRetryDelaySeconds = 3_600)

        assertEquals(false, config.hasValidProbeSettings())
    }

    @Test
    fun requires_a_https_probe_endpoint() {
        val config = DiagnosticConfig.default().copy(probeEndpoint = "http://example.com/test")

        assertEquals(listOf(ConfigIssue.InvalidProbeEndpoint), config.validate())
    }

    @Test
    fun defaults_to_a_one_gigabyte_nightly_probe_budget_with_a_https_fallback() {
        val config = DiagnosticConfig.default()

        assertEquals(1_000_000_000, config.nightlyBudgetBytes)
        assertEquals(true, config.fallbackProbeEndpoint.startsWith("https://"))
        assertEquals(10, config.probeRetryDelaySeconds)
        assertEquals(LocalTime.of(6, 0), config.end)
        assertEquals(DiagnosticRuntimeMode.STANDARD, config.runtimeMode)
    }

    @Test
    fun strict_mode_requires_connection_capture() {
        val config = DiagnosticConfig.default().copy(runtimeMode = DiagnosticRuntimeMode.STRICT)

        assertEquals(
            listOf(ConfigIssue.StrictModeRequiresConnectionCapture),
            config.validateRuntime(connectionCaptureEnabled = false),
        )
    }

    @Test
    fun strict_mode_is_valid_when_connection_capture_is_enabled() {
        val config = DiagnosticConfig.default().copy(runtimeMode = DiagnosticRuntimeMode.STRICT)

        assertEquals(emptyList<ConfigIssue>(), config.validateRuntime(connectionCaptureEnabled = true))
    }
}
