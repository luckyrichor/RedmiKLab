package com.redmiklab.reports

import com.redmiklab.storage.ProbeEntity

object ProbeCsvRenderer {
    fun render(probes: List<ProbeEntity>): String = buildString {
        appendLine("planned_at,dispatched_at,delay_ms,trigger_source,first_attempt_at,last_attempt_at,completed_at,dns_ms,https_ms,download_mbps,upload_mbps,bytes,failure")
        probes.forEach { probe ->
            appendLine(
                "${probe.plannedAtEpochMs},${probe.timestampEpochMs},${probe.delayMs}," +
                    "${csv(probe.triggerSource)},${probe.firstAttemptAtEpochMs ?: ""}," +
                    "${probe.lastAttemptAtEpochMs ?: ""},${probe.completedAtEpochMs}," +
                    "${probe.dnsLatencyMs ?: ""},${probe.httpsLatencyMs ?: ""}," +
                    "${probe.downloadMbps ?: ""},${probe.uploadMbps ?: ""},${probe.consumedBytes}," +
                    csv(probe.failure ?: ""),
            )
        }
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
