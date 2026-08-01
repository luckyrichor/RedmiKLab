package com.redmiklab.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosisRuleTest {
    @Test
    fun marks_network_side_when_both_devices_are_slow_with_similar_radio() {
        val observations = listOf(
            NetworkObservation("k60", Instant.parse("2026-07-12T02:00:00Z"), 3.0, RadioTechnology.FiveG, 3),
            NetworkObservation("k80", Instant.parse("2026-07-12T02:00:00Z"), 2.5, RadioTechnology.FiveG, 4),
        )
        val baselines = mapOf("k60" to 12.0, "k80" to 10.0)

        val finding = DiagnosisRule.evaluate(observations, baselines).single()

        assertEquals(FindingKind.PossibleNetworkSide, finding.kind)
        assertEquals(setOf("k60", "k80"), finding.deviceIds)
    }
}
