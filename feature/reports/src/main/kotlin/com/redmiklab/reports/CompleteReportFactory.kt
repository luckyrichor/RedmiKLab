package com.redmiklab.reports

import com.redmiklab.storage.*

class CompleteReportFactory(private val database: DiagnosticDatabase) {
    fun load(runId: String): CompleteReport {
        val dao = database.diagnosticDao()
        val run = requireNotNull(dao.runById(runId)) { "Unknown diagnostic run: $runId" }
        val snapshots = dao.snapshotsFor(runId)
        val probes = dao.probesFor(runId)
        val attempts = dao.probeAttemptsFor(runId)
        val traffic = dao.appTrafficFor(runId)
        val flows = dao.connectionFlowsFor(runId)
        val events = dao.eventsFor(runId)
        val evidence = dao.appEvidenceFor(runId)
        val accounting = dao.trafficAccountingFor(runId)
        val lifecycle = dao.networkLifecycleFor(runId)
        val gaps = dao.captureGapsFor(runId)
        return build(run, snapshots, probes, attempts, traffic, flows, events, evidence, accounting, lifecycle, gaps)
    }

    fun build(
        run: DiagnosticRunEntity,
        snapshots: List<SnapshotEntity>,
        probes: List<ProbeEntity>,
        attempts: List<ProbeAttemptEntity>,
        traffic: List<AppTrafficEntity>,
        flows: List<ConnectionFlowEntity>,
        events: List<DiagnosticEventEntity>,
        evidence: List<AppEvidenceEntity>,
        accounting: List<TrafficAccountingEntity>,
        lifecycle: List<NetworkLifecycleEntity>,
        gaps: List<CaptureGapEntity>,
    ): CompleteReport =
        CompleteReport(
            run = ReportRun(
                runId = run.runId,
                status = run.status,
                runtimeMode = run.runtimeMode,
                plannedStart = run.plannedStartEpochMs,
                plannedEnd = run.plannedEndEpochMs,
                actualStart = run.startedAtEpochMs,
                actualEnd = run.endedAtEpochMs,
                snapshotMinutes = run.snapshotMinutes,
                connectionCaptureEnabled = run.connectionCaptureEnabled,
                reportBaseName = run.reportBaseName,
                preferredFormat = run.preferredReportFormat,
                deviceModel = run.deviceModel,
                connectivityMinutes = run.connectivityMinutes,
                throughputMinutes = run.throughputMinutes,
                terminalReason = run.terminalReason,
                lastActionAt = run.lastActionAtEpochMs,
            ),
            summary = ReportNumericSummary(
                averageDownloadMbps = probes.mapNotNull { it.downloadMbps }.average().takeIf { !it.isNaN() },
                snapshotCount = snapshots.size,
                probeCount = probes.size,
                probeAttemptCount = attempts.size,
                appTrafficCount = traffic.size,
                connectionFlowCount = flows.size,
                evidenceCount = evidence.size,
                systemMobileBytes = snapshots.sumOf { it.totalMobileBytes },
                tunnelObservedBytes = accounting.filter { it.layer == "TUNNEL_OBSERVED" }.sumOf { it.bytes },
                probeBytes = accounting.filter { it.layer == "ACTIVE_PROBE" }.sumOf { it.bytes },
                heartbeatBytes = accounting.filter { it.layer == "HEARTBEAT" }.sumOf { it.bytes },
                screenWakeRequestCount = events.count { it.eventType == "NETWORK_SCREEN_WAKE_REQUESTED" },
                reconnectStartedCount = events.count { it.eventType == "NETWORK_RETRY_STARTED" },
                reconnectExhaustedCount = events.count { it.eventType == "NETWORK_RETRIES_EXHAUSTED" },
            ),
            tables = linkedMapOf(
                "snapshots" to rows(snapshots),
                "probes" to rows(probes),
                "probeAttempts" to rows(attempts),
                "appTraffic" to rows(traffic),
                "connectionFlows" to rows(flows),
                "events" to rows(events),
                "appEvidence" to rows(evidence),
                "trafficAccounting" to rows(accounting),
                "networkLifecycle" to rows(lifecycle),
                "captureGaps" to rows(gaps),
            ),
        )

    private fun rows(values: List<*>): List<Map<String, String?>> =
        object : AbstractList<Map<String, String?>>() {
            override val size: Int get() = values.size

            override fun get(index: Int): Map<String, String?> {
                val value = requireNotNull(values[index])
                return value.javaClass.fields
                    .sortedBy { it.name }
                    .associate { field -> field.name to field.get(value)?.toString() }
            }
        }
}
