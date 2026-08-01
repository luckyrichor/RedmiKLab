package com.redmiklab.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class UidTrafficAggregatorTest {
    @Test
    fun merges_multiple_system_buckets_for_the_same_uid() {
        val result = UidTrafficAggregator.aggregate(listOf(UidTraffic(10308, 5), UidTraffic(10308, 7), UidTraffic(1000, 3)))

        assertEquals(listOf(UidTraffic(1000, 3), UidTraffic(10308, 12)), result)
    }
}
