package com.redmiklab.model

import java.time.Instant

data class NetworkSnapshot(
    val timestamp: Instant,
    val isMobileDataActive: Boolean,
    val radioTechnology: RadioTechnology,
    val signalDbm: Int?,
    val signalLevel: Int?,
    val isRoaming: Boolean?,
)

data class ProbeResult(
    val timestamp: Instant,
    val dnsLatencyMs: Long?,
    val httpsLatencyMs: Long?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val consumedBytes: Long,
    val failure: ProbeFailure?,
)

enum class ProbeFailure { Dns, Connection, Timeout, BudgetExhausted, NO_PHYSICAL_CELLULAR_NETWORK }

data class TrafficWindow(
    val start: Instant,
    val end: Instant,
    val totalMobileBytes: Long,
    val appUsage: List<AppTraffic>,
    val isApproximate: Boolean,
) {
    fun topApps(limit: Int): List<AppTraffic> = appUsage
        .sortedByDescending { it.mobileBytes }
        .take(limit.coerceAtLeast(0))
}

data class AppTraffic(
    val packageName: String,
    val displayName: String,
    val mobileBytes: Long,
)
