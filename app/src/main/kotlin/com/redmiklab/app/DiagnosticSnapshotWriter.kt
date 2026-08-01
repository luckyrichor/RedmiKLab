package com.redmiklab.app

import com.redmiklab.diagnostics.NetworkSnapshotSource
import com.redmiklab.diagnostics.TrafficStatsSource
import com.redmiklab.storage.AppTrafficEntity
import com.redmiklab.storage.SnapshotEntity
import java.time.Instant

interface DiagnosticSnapshotStore {
    fun lastSnapshotAt(runId: String): Long?
    fun insertSnapshot(snapshot: SnapshotEntity)
    fun insertAppTraffic(traffic: AppTrafficEntity)
}

data class SnapshotWriteResult(
    val actualAtEpochMs: Long,
    val trafficCollectionStatus: String,
)

class DiagnosticSnapshotWriter(
    private val snapshotSource: NetworkSnapshotSource,
    private val trafficSource: TrafficStatsSource,
    private val store: DiagnosticSnapshotStore,
    private val runtimeStateSource: DeviceRuntimeStateSource = DeviceRuntimeStateSource { DeviceRuntimeState.unknown() },
) {
    fun write(
        runId: String,
        runStartedAtEpochMs: Long,
        plannedAtEpochMs: Long,
        triggerSource: String,
    ): SnapshotWriteResult? {
        val snapshot = runCatching(snapshotSource::read).getOrNull() ?: return null
        val actualAtEpochMs = snapshot.timestamp.toEpochMilli()
        val windowStartEpochMs = store.lastSnapshotAt(runId) ?: runStartedAtEpochMs
        val trafficResult = runCatching {
            trafficSource.readWindow(
                Instant.ofEpochMilli(windowStartEpochMs.coerceAtMost(actualAtEpochMs)),
                snapshot.timestamp,
            )
        }
        val traffic = trafficResult.getOrNull()
        val runtimeState = runCatching(runtimeStateSource::read).getOrElse { DeviceRuntimeState.unknown() }
        val trafficStatus = trafficResult.fold(
            onSuccess = { "SUCCESS" },
            onFailure = { error ->
                if (error is SecurityException) "USAGE_ACCESS_MISSING" else "TRAFFIC_COLLECTION_FAILED"
            },
        )

        store.insertSnapshot(
            SnapshotEntity(
                runId,
                plannedAtEpochMs,
                actualAtEpochMs,
                (actualAtEpochMs - plannedAtEpochMs).coerceAtLeast(0),
                triggerSource,
                traffic?.totalMobileBytes ?: 0,
                snapshot.isMobileDataActive,
                snapshot.radioTechnology.name,
                snapshot.signalDbm,
                snapshot.signalLevel,
                snapshot.isRoaming,
                trafficStatus,
                runtimeState.screenInteractive,
                runtimeState.deviceLocked,
                runtimeState.charging,
                runtimeState.lightIdle,
                runtimeState.deepIdle,
                runtimeState.batteryExempt,
                runtimeState.physicalNetworkId,
                runtimeState.vpnNetworkId,
                runtimeState.vpnUnderlyingNetworkId,
                runtimeState.guardianState,
                runtimeState.guardianRetryAttempt,
                runtimeState.captureComplete,
                runtimeState.lastNetworkLostAtEpochMs,
                runtimeState.lastNetworkRecoveredAtEpochMs,
            ),
        )
        traffic?.appUsage?.forEach { app ->
            store.insertAppTraffic(
                AppTrafficEntity(
                    runId,
                    traffic.start.toEpochMilli(),
                    traffic.end.toEpochMilli(),
                    app.packageName,
                    app.displayName,
                    app.mobileBytes,
                    traffic.isApproximate,
                ),
            )
        }
        return SnapshotWriteResult(actualAtEpochMs, trafficStatus)
    }
}
