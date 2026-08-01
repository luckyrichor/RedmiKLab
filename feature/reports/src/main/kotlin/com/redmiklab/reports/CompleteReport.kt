package com.redmiklab.reports

data class CompleteReport(
    val schemaVersion: Int = 4,
    val run: ReportRun,
    val summary: ReportNumericSummary = ReportNumericSummary(),
    val tables: Map<String, List<Map<String, String?>>> = emptyMap(),
)

data class ReportRun(
    val runId: String,
    val status: String,
    val runtimeMode: String,
    val plannedStart: Long,
    val plannedEnd: Long,
    val actualStart: Long,
    val actualEnd: Long,
    val snapshotMinutes: Int,
    val connectionCaptureEnabled: Boolean,
    val reportBaseName: String,
    val preferredFormat: String,
    val deviceModel: String,
    val connectivityMinutes: Int = 10,
    val throughputMinutes: Int = 30,
    val terminalReason: String? = null,
    val lastActionAt: Long = 0,
)

data class ReportNumericSummary(
    val averageDownloadMbps: Double? = null,
    val snapshotCount: Int = 0,
    val probeCount: Int = 0,
    val probeAttemptCount: Int = 0,
    val appTrafficCount: Int = 0,
    val connectionFlowCount: Int = 0,
    val evidenceCount: Int = 0,
    val systemMobileBytes: Long = 0,
    val tunnelObservedBytes: Long = 0,
    val probeBytes: Long = 0,
    val heartbeatBytes: Long = 0,
    val screenWakeRequestCount: Int = 0,
    val reconnectStartedCount: Int = 0,
    val reconnectExhaustedCount: Int = 0,
)
