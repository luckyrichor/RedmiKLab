package com.redmiklab.model

import java.time.Instant

data class NetworkObservation(
    val deviceId: String,
    val timestamp: Instant,
    val throughputMbps: Double,
    val radioTechnology: RadioTechnology,
    val signalLevel: Int,
)

enum class RadioTechnology { FiveG, FourG, Other, Unavailable }

data class Finding(
    val kind: FindingKind,
    val deviceIds: Set<String>,
    val evidence: String,
)

enum class FindingKind { PossibleNetworkSide }

object DiagnosisRule {
    fun evaluate(
        observations: List<NetworkObservation>,
        baselineMbpsByDevice: Map<String, Double>,
    ): List<Finding> = observations
        .groupBy { it.timestamp.epochSecond / THIRTY_MINUTES_SECONDS }
        .values
        .mapNotNull { window -> networkSideFinding(window, baselineMbpsByDevice) }

    private fun networkSideFinding(
        window: List<NetworkObservation>,
        baselineMbpsByDevice: Map<String, Double>,
    ): Finding? {
        val byDevice = window.associateBy { it.deviceId }
        if (byDevice.size < 2 || byDevice.size != window.size) return null
        if (window.any { it.radioTechnology != RadioTechnology.FiveG }) return null
        if (window.maxOf { it.signalLevel } - window.minOf { it.signalLevel } > 1) return null
        if (window.any { observation ->
                val baseline = baselineMbpsByDevice[observation.deviceId] ?: return null
                observation.throughputMbps > baseline * SLOW_RATIO
            }
        ) return null

        return Finding(
            kind = FindingKind.PossibleNetworkSide,
            deviceIds = byDevice.keys,
            evidence = "同一 30 分钟窗口内多台设备均低于各自基线的 30%，且均为 5G、信号等级接近；此为网络侧关联线索，不代表因果结论。",
        )
    }

    private const val THIRTY_MINUTES_SECONDS = 30 * 60L
    private const val SLOW_RATIO = 0.30
}
