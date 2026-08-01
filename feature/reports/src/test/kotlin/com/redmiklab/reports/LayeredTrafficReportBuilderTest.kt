package com.redmiklab.reports

import com.redmiklab.storage.TrafficAccountingEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredTrafficReportBuilderTest {
    @Test
    fun traffic_summary_includes_android_system_mobile_total_as_its_own_layer() {
        val csv = LayeredTrafficReportBuilder.trafficSummary(emptyList(), systemMobileBytes = 1_024)

        assertTrue(csv.contains("ANDROID_SYSTEM_MOBILE,BIDIRECTIONAL,APPROXIMATE,1024"))
    }

    @Test
    fun forwarding_csv_keeps_stage_outcome_direction_bytes_and_reason_unambiguous() {
        val csv = LayeredTrafficReportBuilder.forwardingOutcomes(
            listOf(
                TrafficAccountingEntity(
                    "run", 10, 20, "com.video", "UPSTREAM_SOCKET_ACCEPTED",
                    "UP", 80, "ACCEPTED", "cdn.example",
                ),
            ),
        )

        assertTrue(csv.startsWith("window_start,window_end,package_name,stage,direction,bytes,outcome,reason"))
        assertTrue(csv.contains("UPSTREAM_SOCKET_ACCEPTED"))
        assertTrue(csv.contains("ACCEPTED"))
    }
}
