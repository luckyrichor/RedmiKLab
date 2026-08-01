package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ThroughputCalculatorTest {
    @Test
    fun converts_downloaded_bytes_and_elapsed_time_to_megabits_per_second() {
        assertEquals(8.0, requireNotNull(ThroughputCalculator.mbps(1_000_000, 1_000)), 0.0001)
    }

    @Test
    fun returns_null_when_no_measurement_duration_exists() {
        assertEquals(null, ThroughputCalculator.mbps(100, 0))
    }
}
