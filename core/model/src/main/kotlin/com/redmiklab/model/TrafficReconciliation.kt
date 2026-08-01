package com.redmiklab.model

import kotlin.math.abs

data class TrafficReconciliationResult(
    val explainedBytes: Long,
    val unexplainedBytes: Long,
    val differenceRatio: Double,
    val warnings: List<String>,
)

object TrafficReconciliation {
    fun reconcile(
        systemBytes: Long,
        tunnelBytes: Long,
        probeBytes: Long,
        heartbeatBytes: Long,
    ): TrafficReconciliationResult {
        val explained = tunnelBytes + probeBytes + heartbeatBytes
        val difference = systemBytes - explained
        val ratio = if (systemBytes == 0L) {
            if (difference == 0L) 0.0 else 1.0
        } else abs(difference).toDouble() / systemBytes
        val warnings = buildList {
            add("LAYERS_HAVE_DIFFERENT_COVERAGE")
            if (difference > 0) add("SYSTEM_BYTES_EXCEED_OBSERVED_LAYERS")
            if (difference < 0) add("OBSERVED_LAYERS_EXCEED_SYSTEM_ESTIMATE")
        }
        return TrafficReconciliationResult(explained, difference, ratio, warnings)
    }
}
