package com.redmiklab.reports

import com.redmiklab.storage.ProbeEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeCsvRendererTest {
    @Test
    fun includes_planned_time_delay_and_trigger_source() {
        val csv = ProbeCsvRenderer.render(
            listOf(
                ProbeEntity(
                    "run-a",
                    1_800_000,
                    1_800_125,
                    125,
                    "STRICT_INTERNAL",
                    1_800_150,
                    1_990_000,
                    1_990_100,
                    10,
                    20,
                    3.5,
                    null,
                    1_000,
                    null,
                ),
            ),
        )

        assertTrue(csv.startsWith("planned_at,dispatched_at,delay_ms,trigger_source,first_attempt_at,last_attempt_at,completed_at,"))
        assertTrue(csv.contains("1800000,1800125,125,\"STRICT_INTERNAL\",1800150,1990000,1990100"))
    }
}
