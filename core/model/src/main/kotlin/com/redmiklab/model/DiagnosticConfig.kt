package com.redmiklab.model

import java.time.LocalTime

data class DiagnosticConfig(
    val start: LocalTime,
    val end: LocalTime,
    val snapshotMinutes: Int,
    val connectivityMinutes: Int,
    val throughputMinutes: Int,
    val singleProbeLimitBytes: Long,
    val nightlyBudgetBytes: Long,
    val probeEndpoint: String,
    val fallbackProbeEndpoint: String,
    val probeRetryDelaySeconds: Int,
    val runtimeMode: DiagnosticRuntimeMode,
) {
    fun canStartDiagnosis(): Boolean = validate().isEmpty()

    fun hasValidProbeSettings(): Boolean =
        probeEndpoint.startsWith("https://") &&
            fallbackProbeEndpoint.startsWith("https://") &&
            probeRetryDelaySeconds in 0..MAX_PROBE_RETRY_DELAY_SECONDS

    fun validateRuntime(connectionCaptureEnabled: Boolean): List<ConfigIssue> =
        if (runtimeMode == DiagnosticRuntimeMode.STRICT && !connectionCaptureEnabled) {
            listOf(ConfigIssue.StrictModeRequiresConnectionCapture)
        } else {
            emptyList()
        }

    fun validate(): List<ConfigIssue> = buildList {
        if (snapshotMinutes !in 1..60) add(ConfigIssue.InvalidSnapshotInterval)
        if (connectivityMinutes !in 1..60) add(ConfigIssue.InvalidConnectivityInterval)
        if (throughputMinutes !in 1..120) add(ConfigIssue.InvalidThroughputInterval)
        if (singleProbeLimitBytes <= 0) add(ConfigIssue.InvalidSingleProbeLimit)
        if (nightlyBudgetBytes <= 0) add(ConfigIssue.InvalidNightlyBudget)
        if (singleProbeLimitBytes > nightlyBudgetBytes) add(ConfigIssue.SingleProbeExceedsBudget)
        if (!probeEndpoint.startsWith("https://")) add(ConfigIssue.InvalidProbeEndpoint)
        if (!fallbackProbeEndpoint.startsWith("https://")) add(ConfigIssue.InvalidFallbackProbeEndpoint)
        if (probeRetryDelaySeconds !in 0..MAX_PROBE_RETRY_DELAY_SECONDS) add(ConfigIssue.InvalidProbeRetryDelay)
        if (runtimeMode == DiagnosticRuntimeMode.STANDARD &&
            diagnosticWindowMinutes() > MAX_DIAGNOSTIC_WINDOW_MINUTES
        ) add(ConfigIssue.DiagnosticWindowTooLong)
    }

    private fun diagnosticWindowMinutes(): Int {
        val startMinutes = start.toSecondOfDay() / 60
        val endMinutes = end.toSecondOfDay() / 60
        return (endMinutes - startMinutes).let { if (it > 0) it else it + MINUTES_PER_DAY }
    }

    companion object {
        const val MAX_PROBE_RETRY_DELAY_SECONDS = 300
        fun default() = DiagnosticConfig(
            start = LocalTime.MIDNIGHT,
            end = LocalTime.of(6, 0),
            snapshotMinutes = 5,
            connectivityMinutes = 10,
            throughputMinutes = 30,
            singleProbeLimitBytes = 5_000_000,
            nightlyBudgetBytes = 1_000_000_000,
            probeEndpoint = "https://repo.huaweicloud.com/centos/7/isos/x86_64/CentOS-7-x86_64-DVD-2009.iso",
            fallbackProbeEndpoint = "https://mirrors.ustc.edu.cn/ubuntu-releases/24.04.4/ubuntu-24.04.4-desktop-amd64.iso",
            probeRetryDelaySeconds = 10,
            runtimeMode = DiagnosticRuntimeMode.STANDARD,
        )

        private const val MINUTES_PER_DAY = 24 * 60
        private const val MAX_DIAGNOSTIC_WINDOW_MINUTES = 6 * 60
    }
}

enum class ConfigIssue {
    InvalidSnapshotInterval,
    InvalidConnectivityInterval,
    InvalidThroughputInterval,
    InvalidSingleProbeLimit,
    InvalidNightlyBudget,
    SingleProbeExceedsBudget,
    InvalidProbeEndpoint,
    InvalidFallbackProbeEndpoint,
    InvalidProbeRetryDelay,
    DiagnosticWindowTooLong,
    StrictModeRequiresConnectionCapture,
}
